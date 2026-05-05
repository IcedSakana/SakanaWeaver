package com.agent.core.agent.segment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class that governs how segments are built and assembled into prompts.
 *
 * <p>{@code SegmentBuildConfig} controls aspects such as:</p>
 * <ul>
 *   <li>Which model the segments will be sent to ({@link #modelName}).</li>
 *   <li>How many historical segments to retain ({@link #maxHistorySegments}).</li>
 *   <li>Per-segment token limits ({@link #maxTokensPerSegment}).</li>
 *   <li>Whether knowledge-enhancement and experience-injection are enabled.</li>
 *   <li>Arbitrary feature switches for experimentation.</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 *   SegmentBuildConfig config = SegmentBuildConfig.builder()
 *       .modelName("gpt-4o")
 *       .maxHistorySegments(100)
 *       .enableKnowledgeEnhancement(true)
 *       .build();
 * }</pre>
 *
 * @author agent-server
 * @see SegmentContext
 * @see SegmentBuilder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SegmentBuildConfig {

    /**
     * The name (or identifier) of the target model that will consume the assembled prompt.
     * This may influence prompt formatting, token budgets, and feature availability.
     */
    private String modelName;

    /**
     * Maximum number of history segments to retain when building the prompt.
     * Older segments beyond this limit are dropped (oldest first) to stay within
     * context-window constraints.
     *
     * <p>Default: {@code 50}</p>
     */
    @Builder.Default
    private int maxHistorySegments = 50;

    /**
     * When {@code true}, enables knowledge-enhancement injection during prompt assembly.
     * Knowledge enhancement augments segments with relevant retrieved documents or
     * knowledge-base entries.
     *
     * <p>Default: {@code false}</p>
     */
    @Builder.Default
    private boolean enableKnowledgeEnhancement = false;

    /**
     * When {@code true}, enables experience-injection during prompt assembly.
     * Experience injection inserts previously successful reasoning traces or
     * exemplar solutions into the context.
     *
     * <p>Default: {@code false}</p>
     */
    @Builder.Default
    private boolean enableExperienceInjection = false;

    /**
     * Maximum number of tokens allowed per individual segment. Segments whose
     * content exceeds this limit should be truncated or summarized before
     * inclusion in the prompt.
     *
     * <p>Default: {@code 2000}</p>
     */
    @Builder.Default
    private int maxTokensPerSegment = 2000;

    /**
     * Arbitrary feature switches for A/B testing, experimental capabilities, or
     * runtime toggles. Keys are feature names; values are typically {@link Boolean}
     * or {@link String} but may be any serializable object.
     *
     * <p>Example entries:</p>
     * <ul>
     *   <li>{@code "enableReflection" -> true}</li>
     *   <li>{@code "promptVersion" -> "v2.1"}</li>
     * </ul>
     */
    @Builder.Default
    private Map<String, Object> featureSwitches = new HashMap<>();

    /**
     * Returns the value of a feature switch, or {@code null} if the key is not present.
     *
     * @param key the feature switch key
     * @return the switch value, or {@code null}
     */
    public Object getFeatureSwitch(String key) {
        return featureSwitches == null ? null : featureSwitches.get(key);
    }

    /**
     * Checks whether a boolean feature switch is enabled.
     *
     * @param key the feature switch key
     * @return {@code true} if the switch exists and is {@code Boolean.TRUE}; {@code false} otherwise
     */
    public boolean isFeatureEnabled(String key) {
        Object value = getFeatureSwitch(key);
        return value instanceof Boolean && (Boolean) value;
    }
}
