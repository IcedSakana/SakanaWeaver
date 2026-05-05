package com.agent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Long-term memory -- the persistent layer of the Atkinson-Shiffrin model.
 *
 * <p>Stores four categories of long-lived information:
 * <ul>
 *   <li>{@link KnowledgePoint} -- factual knowledge extracted from
 *       agent interactions.</li>
 *   <li>{@link ExecutionExperience} -- records of tool invocations and
 *       their outcomes.</li>
 *   <li>{@link UserPreference} -- user-specific preferences with
 *       temporal windowing.</li>
 *   <li>{@link HistoryChatSummary} -- compressed summaries of past
 *       sessions, enriched with keyword keys for vector search.</li>
 * </ul>
 *
 * <p>All categories support simple keyword / filter-based retrieval
 * and time-based eviction via {@link #evictExpired()}.
 *
 * @author agent-server
 * @see MemoryManager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LongTermMemory {

    // ================================================================
    // Inner model classes
    // ================================================================

    /**
     * A factual knowledge point extracted from agent interactions.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgePoint {

        /** Unique identifier. */
        @Builder.Default
        private String id = UUID.randomUUID().toString();

        /** Short title summarising the knowledge. */
        private String title;

        /** Full textual content of the knowledge point. */
        private String content;

        /** Knowledge domain (e.g. {@code "java"}, {@code "devops"}). */
        private String domain;

        /** Free-form tags for retrieval and classification. */
        @Builder.Default
        private List<String> tags = new ArrayList<>();

        /** Epoch-millisecond timestamp of creation. */
        @Builder.Default
        private Long createdAt = System.currentTimeMillis();
    }

    /**
     * A record of a tool execution and its outcome.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionExperience {

        /** Unique identifier. */
        @Builder.Default
        private String id = UUID.randomUUID().toString();

        /** Name of the tool that was invoked. */
        private String toolName;

        /** Summarised description of the tool input. */
        private String inputSummary;

        /** Summarised description of the tool output. */
        private String outputSummary;

        /** Whether the execution was successful. */
        private Boolean success;

        /**
         * An optional status or error code associated with the execution.
         */
        private String code;

        /** Epoch-millisecond timestamp of creation. */
        @Builder.Default
        private Long createdAt = System.currentTimeMillis();
    }

    /**
     * A user-specific preference with optional temporal windowing.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreference {

        /** The user this preference belongs to. */
        private String userId;

        /** Schema definition of the preference data. */
        @Builder.Default
        private java.util.Map<String, Object> schema = new java.util.HashMap<>();

        /** Actual preference data. */
        @Builder.Default
        private java.util.Map<String, Object> data = new java.util.HashMap<>();

        /** Operation that last modified this preference. */
        private String operation;

        /** Free-form tags. */
        @Builder.Default
        private List<String> tags = new ArrayList<>();

        /**
         * Start of the temporal window (epoch millis) during which
         * this preference is applicable. May be {@code null}.
         */
        private Long windowStart;

        /**
         * End of the temporal window (epoch millis) during which
         * this preference is applicable. May be {@code null}.
         */
        private Long windowEnd;
    }

    /**
     * Compressed summary of a past chat session, enriched with
     * keyword keys suitable for vector / keyword search.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryChatSummary {

        /** The session this summary was derived from. */
        private String sessionId;

        /** The user who participated in the session. */
        private String userId;

        /** Free-text summary of the session. */
        private String summary;

        /**
         * Keyword keys extracted from the summary, designed to
         * support vector similarity or keyword-based search.
         */
        @Builder.Default
        private List<String> keys = new ArrayList<>();

        /** Epoch-millisecond timestamp of creation. */
        @Builder.Default
        private Long createdAt = System.currentTimeMillis();

        /**
         * Optional expiration timestamp (epoch millis).
         * {@code null} means the summary never expires.
         */
        private Long expireAt;
    }

    // ================================================================
    // Storage
    // ================================================================

    /** All stored knowledge points. */
    @Builder.Default
    private List<KnowledgePoint> knowledgePoints = new ArrayList<>();

    /** All stored execution experiences. */
    @Builder.Default
    private List<ExecutionExperience> executionExperiences = new ArrayList<>();

    /** All stored user preferences. */
    @Builder.Default
    private List<UserPreference> userPreferences = new ArrayList<>();

    /** All stored chat summaries. */
    @Builder.Default
    private List<HistoryChatSummary> chatSummaries = new ArrayList<>();

    // ================================================================
    // Add operations
    // ================================================================

    /**
     * Store a new knowledge point.
     *
     * @param point the knowledge point to add
     */
    public void addKnowledge(KnowledgePoint point) {
        if (point != null) {
            knowledgePoints.add(point);
        }
    }

    /**
     * Store a new execution experience.
     *
     * @param experience the experience to add
     */
    public void addExperience(ExecutionExperience experience) {
        if (experience != null) {
            executionExperiences.add(experience);
        }
    }

    /**
     * Store a new user preference.
     *
     * @param preference the preference to add
     */
    public void addPreference(UserPreference preference) {
        if (preference != null) {
            userPreferences.add(preference);
        }
    }

    /**
     * Store a new chat summary.
     *
     * @param summary the chat summary to add
     */
    public void addChatSummary(HistoryChatSummary summary) {
        if (summary != null) {
            chatSummaries.add(summary);
        }
    }

    // ================================================================
    // Search / query operations
    // ================================================================

    /**
     * Search knowledge points by a simple case-insensitive keyword
     * match against the {@code title}, {@code content} and
     * {@code domain} fields.
     *
     * @param query the search keyword
     * @param limit maximum number of results to return
     * @return matching knowledge points ordered by creation time
     *         (newest first), limited to {@code limit}
     */
    public List<KnowledgePoint> searchKnowledge(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        String lq = query.toLowerCase();
        return knowledgePoints.stream()
                .filter(kp -> matches(kp.getTitle(), lq)
                        || matches(kp.getContent(), lq)
                        || matches(kp.getDomain(), lq)
                        || kp.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lq)))
                .sorted(Comparator.comparingLong(
                        (KnowledgePoint kp) -> kp.getCreatedAt() != null ? kp.getCreatedAt() : 0L)
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Search execution experiences by tool name.
     *
     * @param toolName the tool name to filter by (case-insensitive)
     * @param limit    maximum number of results to return
     * @return matching experiences ordered by creation time
     *         (newest first), limited to {@code limit}
     */
    public List<ExecutionExperience> searchExperience(String toolName, int limit) {
        if (toolName == null || toolName.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        String lt = toolName.toLowerCase();
        return executionExperiences.stream()
                .filter(e -> e.getToolName() != null
                        && e.getToolName().toLowerCase().contains(lt))
                .sorted(Comparator.comparingLong(
                        (ExecutionExperience e) -> e.getCreatedAt() != null ? e.getCreatedAt() : 0L)
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve user preferences for a given user that fall within
     * a rolling time window.
     *
     * <p>A preference is included when:
     * <ul>
     *   <li>Its {@code userId} matches (case-insensitive), AND</li>
     *   <li>Its temporal window overlaps with
     *       {@code [now - windowDays .. now]}, or no window is set.</li>
     * </ul>
     *
     * @param userId     the user to look up
     * @param windowDays the number of past days to consider
     * @return matching preferences
     */
    public List<UserPreference> getUserPreferences(String userId, int windowDays) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        long windowStartMs = Instant.now()
                .minus(windowDays, ChronoUnit.DAYS)
                .toEpochMilli();

        return userPreferences.stream()
                .filter(p -> userId.equalsIgnoreCase(p.getUserId()))
                .filter(p -> {
                    // Include if no temporal window is defined
                    if (p.getWindowStart() == null && p.getWindowEnd() == null) {
                        return true;
                    }
                    // Include if the preference window overlaps [windowStartMs, now]
                    long ws = p.getWindowStart() != null ? p.getWindowStart() : 0L;
                    long we = p.getWindowEnd() != null ? p.getWindowEnd() : Long.MAX_VALUE;
                    return ws <= now && we >= windowStartMs;
                })
                .collect(Collectors.toList());
    }

    /**
     * Retrieve chat summaries for a given user within the last
     * {@code days} days, limited to {@code limit} results.
     *
     * @param userId the user to look up
     * @param days   the number of past days to consider
     * @param limit  maximum number of results to return
     * @return matching summaries ordered by creation time (newest first)
     */
    public List<HistoryChatSummary> getChatSummaries(String userId, int days, int limit) {
        if (userId == null || userId.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        long cutoff = Instant.now()
                .minus(days, ChronoUnit.DAYS)
                .toEpochMilli();

        return chatSummaries.stream()
                .filter(s -> userId.equalsIgnoreCase(s.getUserId()))
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt() >= cutoff)
                .sorted(Comparator.comparingLong(
                        (HistoryChatSummary s) -> s.getCreatedAt() != null ? s.getCreatedAt() : 0L)
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ================================================================
    // Eviction
    // ================================================================

    /**
     * Evict all expired entries from every category.
     *
     * <p>An entry is considered expired when its expiration timestamp
     * is non-null and is earlier than {@link System#currentTimeMillis()}.
     * For categories without an explicit {@code expireAt} field
     * (e.g. {@link KnowledgePoint}), no automatic eviction is performed.
     *
     * @return the total number of entries evicted
     */
    public int evictExpired() {
        long now = System.currentTimeMillis();
        int count = 0;

        // Chat summaries have an explicit expireAt field
        int before = chatSummaries.size();
        chatSummaries.removeIf(s ->
                s.getExpireAt() != null && s.getExpireAt() < now);
        count += (before - chatSummaries.size());

        // User preferences with a windowEnd in the past
        before = userPreferences.size();
        userPreferences.removeIf(p ->
                p.getWindowEnd() != null && p.getWindowEnd() < now);
        count += (before - userPreferences.size());

        return count;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static boolean matches(String field, String lowerQuery) {
        return field != null && field.toLowerCase().contains(lowerQuery);
    }
}
