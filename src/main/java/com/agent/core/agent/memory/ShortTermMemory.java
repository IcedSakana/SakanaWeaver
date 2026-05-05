package com.agent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Short-term (working) memory -- the session-level layer of the
 * Atkinson-Shiffrin model.
 *
 * <p>Holds {@link SegmentMemoryMessage segment memories} and
 * {@link EntityMemoryMessage entity memories} accumulated during a single
 * agent session. A configurable {@link #maxCapacity} bounds the total
 * number of segment messages retained; when the limit is exceeded the
 * oldest segments are evicted in FIFO order.
 *
 * <p>This class also provides simple keyword-based search over both
 * segment and entity contents, as well as serialisation helpers
 * ({@link #toMap} / {@link #fromMap}) for persistence.
 *
 * @author agent-server
 * @see SegmentMemoryMessage
 * @see EntityMemoryMessage
 * @see MemoryManager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermMemory {

    /**
     * The session this working memory belongs to.
     */
    private String sessionId;

    /**
     * Ordered list of segment memories produced during agent execution.
     */
    @Builder.Default
    private List<SegmentMemoryMessage> segmentMemories = new ArrayList<>();

    /**
     * Ordered list of entity memories extracted during agent execution.
     */
    @Builder.Default
    private List<EntityMemoryMessage> entityMemories = new ArrayList<>();

    /**
     * Maximum number of segment memories to retain.
     * When this limit is exceeded, the oldest segments are evicted first.
     */
    @Builder.Default
    private int maxCapacity = 200;

    // ----------------------------------------------------------------
    // Segment operations
    // ----------------------------------------------------------------

    /**
     * Append a segment memory to this working memory.
     *
     * <p>If adding the segment would exceed {@link #maxCapacity}, the
     * oldest segment is removed before the new one is appended.
     *
     * @param segment the segment memory to add (must not be {@code null})
     */
    public void addSegment(SegmentMemoryMessage segment) {
        if (segment == null) {
            return;
        }
        while (segmentMemories.size() >= maxCapacity) {
            segmentMemories.remove(0);
        }
        segmentMemories.add(segment);
    }

    /**
     * Retrieve the most recent {@code n} segment memories.
     *
     * @param n the maximum number of segments to return
     * @return an unmodifiable list of up to {@code n} segments, ordered
     *         from oldest to newest
     */
    public List<SegmentMemoryMessage> getRecentSegments(int n) {
        if (n <= 0 || segmentMemories.isEmpty()) {
            return Collections.emptyList();
        }
        int fromIndex = Math.max(0, segmentMemories.size() - n);
        return Collections.unmodifiableList(
                new ArrayList<>(segmentMemories.subList(fromIndex, segmentMemories.size()))
        );
    }

    // ----------------------------------------------------------------
    // Entity operations
    // ----------------------------------------------------------------

    /**
     * Add an entity memory to this working memory.
     *
     * @param entity the entity memory to add (must not be {@code null})
     */
    public void addEntity(EntityMemoryMessage entity) {
        if (entity == null) {
            return;
        }
        entityMemories.add(entity);
    }

    /**
     * Retrieve all entity memories whose {@code entityType} matches
     * the given type (case-insensitive comparison).
     *
     * @param type the entity type to filter by
     * @return an unmodifiable list of matching entity memories
     */
    public List<EntityMemoryMessage> getEntitiesByType(String type) {
        if (type == null || type.isBlank()) {
            return Collections.emptyList();
        }
        return entityMemories.stream()
                .filter(e -> type.equalsIgnoreCase(e.getEntityType()))
                .collect(Collectors.toUnmodifiableList());
    }

    // ----------------------------------------------------------------
    // Search
    // ----------------------------------------------------------------

    /**
     * Perform a simple case-insensitive keyword search across all
     * segment and entity contents.
     *
     * <p>A memory message matches when its {@code content} field
     * contains the query string (ignoring case).
     *
     * @param query the keyword to search for
     * @return a list of matching {@link MemoryMessage} instances
     *         (segments first, then entities)
     */
    public List<MemoryMessage> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String lowerQuery = query.toLowerCase();

        List<MemoryMessage> results = new ArrayList<>();

        segmentMemories.stream()
                .filter(s -> s.getContent() != null
                        && s.getContent().toLowerCase().contains(lowerQuery))
                .forEach(results::add);

        entityMemories.stream()
                .filter(e -> e.getContent() != null
                        && e.getContent().toLowerCase().contains(lowerQuery))
                .forEach(results::add);

        return results;
    }

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    /**
     * Clear all segment and entity memories, resetting this working
     * memory to an empty state.
     */
    public void clear() {
        segmentMemories.clear();
        entityMemories.clear();
    }

    // ----------------------------------------------------------------
    // Serialisation helpers
    // ----------------------------------------------------------------

    /**
     * Serialise this short-term memory into a plain {@link Map}
     * suitable for JSON persistence.
     *
     * @return a map representation of this instance
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", sessionId);
        map.put("maxCapacity", maxCapacity);

        List<Map<String, Object>> segments = segmentMemories.stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getId());
                    m.put("memoryType", s.getMemoryType() != null ? s.getMemoryType().name() : null);
                    m.put("sessionId", s.getSessionId());
                    m.put("userId", s.getUserId());
                    m.put("content", s.getContent());
                    m.put("timestamp", s.getTimestamp());
                    m.put("expireAt", s.getExpireAt());
                    m.put("metadata", s.getMetadata());
                    m.put("segmentType", s.getSegmentType());
                    m.put("roundIndex", s.getRoundIndex());
                    m.put("role", s.getRole());
                    return m;
                })
                .collect(Collectors.toList());
        map.put("segmentMemories", segments);

        List<Map<String, Object>> entities = entityMemories.stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", e.getId());
                    m.put("memoryType", e.getMemoryType() != null ? e.getMemoryType().name() : null);
                    m.put("sessionId", e.getSessionId());
                    m.put("userId", e.getUserId());
                    m.put("content", e.getContent());
                    m.put("timestamp", e.getTimestamp());
                    m.put("expireAt", e.getExpireAt());
                    m.put("metadata", e.getMetadata());
                    m.put("entityType", e.getEntityType());
                    m.put("schema", e.getSchema());
                    m.put("data", e.getData());
                    m.put("operation", e.getOperation());
                    m.put("tags", e.getTags());
                    return m;
                })
                .collect(Collectors.toList());
        map.put("entityMemories", entities);

        return map;
    }

    /**
     * Deserialise a short-term memory instance from a plain {@link Map}
     * previously produced by {@link #toMap()}.
     *
     * @param map the map representation
     * @return a reconstituted {@link ShortTermMemory} instance
     */
    @SuppressWarnings("unchecked")
    public static ShortTermMemory fromMap(Map<String, Object> map) {
        if (map == null) {
            return ShortTermMemory.builder().build();
        }

        ShortTermMemory stm = ShortTermMemory.builder()
                .sessionId((String) map.get("sessionId"))
                .maxCapacity(map.containsKey("maxCapacity")
                        ? ((Number) map.get("maxCapacity")).intValue()
                        : 200)
                .build();

        List<Map<String, Object>> segments =
                (List<Map<String, Object>>) map.getOrDefault("segmentMemories", Collections.emptyList());
        for (Map<String, Object> s : segments) {
            SegmentMemoryMessage seg = SegmentMemoryMessage.builder()
                    .id((String) s.get("id"))
                    .memoryType(s.get("memoryType") != null
                            ? MemoryType.valueOf((String) s.get("memoryType"))
                            : null)
                    .sessionId((String) s.get("sessionId"))
                    .userId((String) s.get("userId"))
                    .content((String) s.get("content"))
                    .timestamp(s.get("timestamp") != null
                            ? ((Number) s.get("timestamp")).longValue() : null)
                    .expireAt(s.get("expireAt") != null
                            ? ((Number) s.get("expireAt")).longValue() : null)
                    .metadata(s.get("metadata") != null
                            ? (Map<String, Object>) s.get("metadata")
                            : new HashMap<>())
                    .segmentType((String) s.get("segmentType"))
                    .roundIndex(s.get("roundIndex") != null
                            ? ((Number) s.get("roundIndex")).intValue() : null)
                    .role((String) s.get("role"))
                    .build();
            stm.getSegmentMemories().add(seg);
        }

        List<Map<String, Object>> entities =
                (List<Map<String, Object>>) map.getOrDefault("entityMemories", Collections.emptyList());
        for (Map<String, Object> e : entities) {
            EntityMemoryMessage ent = EntityMemoryMessage.builder()
                    .id((String) e.get("id"))
                    .memoryType(e.get("memoryType") != null
                            ? MemoryType.valueOf((String) e.get("memoryType"))
                            : null)
                    .sessionId((String) e.get("sessionId"))
                    .userId((String) e.get("userId"))
                    .content((String) e.get("content"))
                    .timestamp(e.get("timestamp") != null
                            ? ((Number) e.get("timestamp")).longValue() : null)
                    .expireAt(e.get("expireAt") != null
                            ? ((Number) e.get("expireAt")).longValue() : null)
                    .metadata(e.get("metadata") != null
                            ? (Map<String, Object>) e.get("metadata")
                            : new HashMap<>())
                    .entityType((String) e.get("entityType"))
                    .schema(e.get("schema") != null
                            ? (Map<String, Object>) e.get("schema")
                            : new HashMap<>())
                    .data(e.get("data") != null
                            ? (Map<String, Object>) e.get("data")
                            : new HashMap<>())
                    .operation((String) e.get("operation"))
                    .tags(e.get("tags") != null
                            ? (List<String>) e.get("tags")
                            : new ArrayList<>())
                    .build();
            stm.getEntityMemories().add(ent);
        }

        return stm;
    }
}
