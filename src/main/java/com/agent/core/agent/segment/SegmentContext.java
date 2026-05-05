package com.agent.core.agent.segment;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the full segment timeline for a single agent session.
 *
 * <p>{@code SegmentContext} maintains two segment lists:</p>
 * <ol>
 *   <li><b>currentSegments</b> &ndash; segments accumulated during the current reasoning round.</li>
 *   <li><b>historySegments</b> &ndash; segments from all previously archived rounds.</li>
 * </ol>
 *
 * <p>When a round completes, {@link #archiveCurrentRound()} moves every segment in
 * {@code currentSegments} into {@code historySegments} and increments the round counter,
 * preparing the context for the next user interaction.</p>
 *
 * <p>The {@link #buildUserPrompt()} method assembles the complete prompt by concatenating
 * history segments followed by current-round segments, each rendered through
 * {@link Segment#toPromptFormat()}.</p>
 *
 * @author agent-server
 * @see Segment
 * @see SegmentType
 */
@Data
public class SegmentContext {

    /**
     * The unique session identifier this context belongs to.
     */
    private String sessionId;

    /**
     * Segments accumulated in the current (active) reasoning round.
     */
    private List<Segment> currentSegments;

    /**
     * Segments from all previously completed rounds, ordered chronologically.
     */
    private List<Segment> historySegments;

    /**
     * Zero-based index of the current reasoning round.
     */
    private int currentRound;

    /**
     * Creates a new {@code SegmentContext} for the given session.
     *
     * @param sessionId the unique session identifier
     */
    public SegmentContext(String sessionId) {
        this.sessionId = sessionId;
        this.currentSegments = new ArrayList<>();
        this.historySegments = new ArrayList<>();
        this.currentRound = 0;
    }

    /**
     * Default constructor for deserialization.
     */
    public SegmentContext() {
        this.currentSegments = new ArrayList<>();
        this.historySegments = new ArrayList<>();
        this.currentRound = 0;
    }

    /**
     * Appends a segment to the current round.
     *
     * <p>The segment's {@code roundIndex} is automatically set to {@link #currentRound}
     * if it has not already been assigned.</p>
     *
     * @param segment the segment to add; must not be {@code null}
     */
    public void addSegment(Segment segment) {
        if (segment.getRoundIndex() == null) {
            segment.setRoundIndex(currentRound);
        }
        currentSegments.add(segment);
    }

    /**
     * Archives the current round by moving all {@code currentSegments} into
     * {@code historySegments}, then increments the round counter.
     *
     * <p>After this call, {@code currentSegments} is empty and ready for the next round.</p>
     */
    public void archiveCurrentRound() {
        historySegments.addAll(currentSegments);
        currentSegments = new ArrayList<>();
        currentRound++;
    }

    /**
     * Assembles the full user prompt from all segments (history + current round).
     *
     * <p>Each segment is rendered via {@link Segment#toPromptFormat()} and joined by
     * newline characters. History segments appear first, followed by current-round segments.</p>
     *
     * @return the assembled prompt string
     */
    public String buildUserPrompt() {
        List<Segment> allSegments = new ArrayList<>(historySegments.size() + currentSegments.size());
        allSegments.addAll(historySegments);
        allSegments.addAll(currentSegments);

        return allSegments.stream()
                .map(Segment::toPromptFormat)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Returns all segments (from both history and current round) that match the given type.
     *
     * @param type the segment type to filter by
     * @return an unmodifiable list of matching segments, in chronological order
     */
    public List<Segment> getSegmentsByType(SegmentType type) {
        List<Segment> result = new ArrayList<>();
        for (Segment seg : historySegments) {
            if (seg.getType() == type) {
                result.add(seg);
            }
        }
        for (Segment seg : currentSegments) {
            if (seg.getType() == type) {
                result.add(seg);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the last {@code n} segments across history and the current round,
     * ordered chronologically (oldest first).
     *
     * <p>If fewer than {@code n} segments exist in total, all segments are returned.</p>
     *
     * @param n the maximum number of segments to return
     * @return an unmodifiable list of the most recent segments
     */
    public List<Segment> getLastNSegments(int n) {
        List<Segment> allSegments = new ArrayList<>(historySegments.size() + currentSegments.size());
        allSegments.addAll(historySegments);
        allSegments.addAll(currentSegments);

        int total = allSegments.size();
        if (n >= total) {
            return Collections.unmodifiableList(new ArrayList<>(allSegments));
        }
        return Collections.unmodifiableList(new ArrayList<>(allSegments.subList(total - n, total)));
    }

    /**
     * Clears all segments (both history and current round) and resets the round counter to zero.
     */
    public void clear() {
        currentSegments.clear();
        historySegments.clear();
        currentRound = 0;
    }

    /**
     * Serializes this context into a plain {@link Map} suitable for JSON serialization
     * or persistent storage.
     *
     * @return a map containing all context state
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", sessionId);
        map.put("currentRound", currentRound);
        map.put("currentSegments", currentSegments);
        map.put("historySegments", historySegments);
        return map;
    }

    /**
     * Restores a {@code SegmentContext} from a previously serialized map.
     *
     * <p>The map is expected to contain the keys produced by {@link #toMap()}.
     * Segment entries may be raw {@link Segment} instances or {@link Map} representations
     * that will be converted accordingly.</p>
     *
     * @param map the serialized context map
     * @return a fully restored {@code SegmentContext}
     */
    @SuppressWarnings("unchecked")
    public static SegmentContext fromMap(Map<String, Object> map) {
        SegmentContext context = new SegmentContext();
        context.setSessionId((String) map.get("sessionId"));
        context.setCurrentRound(map.get("currentRound") instanceof Number
                ? ((Number) map.get("currentRound")).intValue()
                : 0);

        Object currentRaw = map.get("currentSegments");
        if (currentRaw instanceof List<?> rawList) {
            context.setCurrentSegments(convertSegmentList(rawList));
        }

        Object historyRaw = map.get("historySegments");
        if (historyRaw instanceof List<?> rawList) {
            context.setHistorySegments(convertSegmentList(rawList));
        }

        return context;
    }

    /**
     * Returns the total number of segments across all rounds (history + current).
     *
     * @return the total segment count
     */
    public int getTotalSegmentCount() {
        return historySegments.size() + currentSegments.size();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a raw list (potentially containing Maps) into a typed list of {@link Segment}s.
     */
    @SuppressWarnings("unchecked")
    private static List<Segment> convertSegmentList(List<?> rawList) {
        List<Segment> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Segment segment) {
                result.add(segment);
            } else if (item instanceof Map<?, ?> itemMap) {
                result.add(convertMapToSegment((Map<String, Object>) itemMap));
            }
        }
        return result;
    }

    /**
     * Converts a single {@link Map} to a {@link Segment} instance.
     */
    @SuppressWarnings("unchecked")
    private static Segment convertMapToSegment(Map<String, Object> map) {
        Segment.SegmentBuilder builder = Segment.builder();

        if (map.containsKey("id")) {
            builder.id((String) map.get("id"));
        }
        if (map.containsKey("type")) {
            Object typeVal = map.get("type");
            if (typeVal instanceof SegmentType segType) {
                builder.type(segType);
            } else if (typeVal instanceof String strType) {
                builder.type(SegmentType.fromValue(strType));
            }
        }
        if (map.containsKey("content")) {
            builder.content((String) map.get("content"));
        }
        if (map.containsKey("timestamp") && map.get("timestamp") instanceof Number ts) {
            builder.timestamp(ts.longValue());
        }
        if (map.containsKey("roundIndex") && map.get("roundIndex") instanceof Number ri) {
            builder.roundIndex(ri.intValue());
        }
        if (map.containsKey("metadata") && map.get("metadata") instanceof Map<?, ?> meta) {
            builder.metadata((Map<String, Object>) meta);
        }

        return builder.build();
    }
}
