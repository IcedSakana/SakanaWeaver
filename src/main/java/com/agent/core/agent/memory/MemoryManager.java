package com.agent.core.agent.memory;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central coordinator for the 3-layer Atkinson-Shiffrin memory system.
 *
 * <p>{@code MemoryManager} owns one {@link SensoryMemory} instance,
 * a per-session pool of {@link ShortTermMemory} instances, and a single
 * shared {@link LongTermMemory}. It provides convenience methods for
 * promoting memories across layers and for assembling a unified memory
 * context that can be injected into prompt building.
 *
 * <h3>Promotion flow</h3>
 * <pre>
 *   Sensory  --{@link #promoteToShortTerm}--&gt;  Short-Term
 *   Short-Term  --{@link #promoteToLongTerm}--&gt;  Long-Term
 * </pre>
 *
 * @author agent-server
 * @see SensoryMemory
 * @see ShortTermMemory
 * @see LongTermMemory
 */
@Slf4j
public class MemoryManager {

    /**
     * The sensory (perception) memory layer -- singleton per manager.
     */
    private final SensoryMemory sensoryMemory;

    /**
     * Per-session short-term memory pool.
     * Key: sessionId, Value: the session's working memory.
     */
    private final ConcurrentHashMap<String, ShortTermMemory> shortTermPool;

    /**
     * The long-term (persistent) memory layer -- singleton per manager.
     */
    private final LongTermMemory longTermMemory;

    /**
     * Create a new {@code MemoryManager} with fresh, empty memory layers.
     */
    public MemoryManager() {
        this.sensoryMemory = SensoryMemory.builder().build();
        this.shortTermPool = new ConcurrentHashMap<>();
        this.longTermMemory = LongTermMemory.builder().build();
    }

    /**
     * Create a {@code MemoryManager} with pre-existing memory layers.
     *
     * @param sensoryMemory  the sensory memory instance
     * @param longTermMemory the long-term memory instance
     */
    public MemoryManager(SensoryMemory sensoryMemory, LongTermMemory longTermMemory) {
        this.sensoryMemory = sensoryMemory != null
                ? sensoryMemory
                : SensoryMemory.builder().build();
        this.shortTermPool = new ConcurrentHashMap<>();
        this.longTermMemory = longTermMemory != null
                ? longTermMemory
                : LongTermMemory.builder().build();
    }

    // ================================================================
    // Accessors
    // ================================================================

    /**
     * Return the global sensory memory instance.
     *
     * @return the sensory memory
     */
    public SensoryMemory getSensoryMemory() {
        return sensoryMemory;
    }

    /**
     * Return (or lazily create) the short-term memory for a given session.
     *
     * @param sessionId the session identifier
     * @return the session's short-term memory, never {@code null}
     */
    public ShortTermMemory getShortTermMemory(String sessionId) {
        return shortTermPool.computeIfAbsent(sessionId, sid ->
                ShortTermMemory.builder()
                        .sessionId(sid)
                        .build());
    }

    /**
     * Return the global long-term memory instance.
     *
     * @return the long-term memory
     */
    public LongTermMemory getLongTermMemory() {
        return longTermMemory;
    }

    // ================================================================
    // Promotion operations
    // ================================================================

    /**
     * Promote the current sensory snapshot into the short-term memory
     * of the given session.
     *
     * <p>A new {@link SegmentMemoryMessage} with segment type
     * {@code "SENSORY_CAPTURE"} is created from the sensory state and
     * appended to the session's working memory. The sensory memory is
     * then invalidated.
     *
     * @param sensory the sensory memory to promote (must be
     *                {@linkplain SensoryMemory#isValid() valid})
     * @return the {@link SegmentMemoryMessage} that was added, or
     *         {@code null} if the sensory memory was invalid
     */
    public SegmentMemoryMessage promoteToShortTerm(SensoryMemory sensory) {
        if (sensory == null || !sensory.isValid()) {
            log.debug("Sensory memory is null or invalid; skipping promotion.");
            return null;
        }

        // Build a content string from the sensory snapshot
        StringBuilder sb = new StringBuilder();
        sb.append("[Page] ").append(sensory.getCurrentPageTitle());
        sb.append(" (").append(sensory.getCurrentPageUrl()).append(")");
        if (sensory.getPageContext() != null && !sensory.getPageContext().isEmpty()) {
            sb.append("\n[Context] ").append(sensory.getPageContext());
        }
        if (sensory.getUserActions() != null && !sensory.getUserActions().isEmpty()) {
            sb.append("\n[Actions] ").append(String.join(", ", sensory.getUserActions()));
        }

        SegmentMemoryMessage segment = SegmentMemoryMessage.builder()
                .memoryType(MemoryType.SHORT_TERM)
                .content(sb.toString())
                .timestamp(sensory.getCapturedAt())
                .segmentType("SENSORY_CAPTURE")
                .roundIndex(0)
                .role("system")
                .build();

        // We need a sessionId to store; derive from metadata or use a default
        String sessionId = segment.getSessionId() != null
                ? segment.getSessionId()
                : "default";
        getShortTermMemory(sessionId).addSegment(segment);

        // Invalidate the sensory snapshot after promotion
        sensory.invalidate();

        log.debug("Promoted sensory memory to short-term memory (session={}).", sessionId);
        return segment;
    }

    /**
     * Promote the short-term memory of a session into long-term memory.
     *
     * <p>This method summarises the session's segment and entity memories
     * and stores the result as a {@link LongTermMemory.HistoryChatSummary}.
     * After promotion the short-term memory for that session is cleared.
     *
     * @param sessionId the session whose short-term memory should be promoted
     */
    public void promoteToLongTerm(String sessionId) {
        ShortTermMemory stm = shortTermPool.get(sessionId);
        if (stm == null || stm.getSegmentMemories().isEmpty()) {
            log.debug("No short-term memory to promote for session={}.", sessionId);
            return;
        }

        // Build a simple summary from the segment contents
        StringBuilder summaryBuilder = new StringBuilder();
        List<String> keys = new ArrayList<>();
        String userId = null;

        for (SegmentMemoryMessage seg : stm.getSegmentMemories()) {
            if (seg.getContent() != null) {
                summaryBuilder.append("[").append(seg.getRole()).append("] ")
                        .append(seg.getContent()).append("\n");
            }
            if (seg.getUserId() != null && userId == null) {
                userId = seg.getUserId();
            }
            // Collect segment types as search keys
            if (seg.getSegmentType() != null && !keys.contains(seg.getSegmentType())) {
                keys.add(seg.getSegmentType());
            }
        }

        // Also capture entity types as keys
        for (EntityMemoryMessage ent : stm.getEntityMemories()) {
            if (ent.getEntityType() != null && !keys.contains(ent.getEntityType())) {
                keys.add(ent.getEntityType());
            }
        }

        LongTermMemory.HistoryChatSummary summary = LongTermMemory.HistoryChatSummary.builder()
                .sessionId(sessionId)
                .userId(userId)
                .summary(summaryBuilder.toString())
                .keys(keys)
                .build();

        longTermMemory.addChatSummary(summary);

        // Clear the short-term memory after promotion
        stm.clear();

        log.info("Promoted short-term memory to long-term for session={}. Keys={}.",
                sessionId, keys);
    }

    // ================================================================
    // Context assembly
    // ================================================================

    /**
     * Assemble a unified memory context from all three layers.
     *
     * <p>The returned map is intended to be consumed by a prompt builder
     * and contains the following keys:
     * <ul>
     *   <li>{@code "sensory"} -- a map of the current sensory snapshot
     *       (empty map when invalid).</li>
     *   <li>{@code "shortTerm"} -- a map keyed by sessionId, each value
     *       being the {@link ShortTermMemory#toMap()} output.</li>
     *   <li>{@code "longTerm"} -- a map with keys
     *       {@code "knowledgePoints"}, {@code "executionExperiences"},
     *       {@code "userPreferences"}, {@code "chatSummaries"}.</li>
     * </ul>
     *
     * @return a hierarchical map of all memory layers
     */
    public Map<String, Object> buildMemoryContext() {
        Map<String, Object> context = new HashMap<>();

        // --- Sensory layer ---
        Map<String, Object> sensoryMap = new HashMap<>();
        if (sensoryMemory.isValid()) {
            sensoryMap.put("currentPageUrl", sensoryMemory.getCurrentPageUrl());
            sensoryMap.put("currentPageTitle", sensoryMemory.getCurrentPageTitle());
            sensoryMap.put("pageContext", sensoryMemory.getPageContext());
            sensoryMap.put("userActions", sensoryMemory.getUserActions());
            sensoryMap.put("capturedAt", sensoryMemory.getCapturedAt());
        }
        context.put("sensory", sensoryMap);

        // --- Short-term layer ---
        Map<String, Object> shortTermMap = new HashMap<>();
        shortTermPool.forEach((sid, stm) ->
                shortTermMap.put(sid, stm.toMap()));
        context.put("shortTerm", shortTermMap);

        // --- Long-term layer ---
        Map<String, Object> longTermMap = new HashMap<>();
        longTermMap.put("knowledgePoints", longTermMemory.getKnowledgePoints());
        longTermMap.put("executionExperiences", longTermMemory.getExecutionExperiences());
        longTermMap.put("userPreferences", longTermMemory.getUserPreferences());
        longTermMap.put("chatSummaries", longTermMemory.getChatSummaries());
        context.put("longTerm", longTermMap);

        return context;
    }
}
