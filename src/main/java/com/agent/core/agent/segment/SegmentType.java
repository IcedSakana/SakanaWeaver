package com.agent.core.agent.segment;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of all supported segment types in the Agent's Chain-of-Thought (CoT) reasoning.
 *
 * <p>Each segment type represents a distinct phase or artifact within a single reasoning round.
 * Together they form the {@code [Segment]: [Content]} structure that drives Context Engineering
 * for the AI Agent system.</p>
 *
 * <ul>
 *   <li>{@link #USER_INPUT}       - Raw input provided by the end user.</li>
 *   <li>{@link #THOUGHT}          - Internal reasoning / Chain-of-Thought step.</li>
 *   <li>{@link #CODE}             - Source code generated or referenced by the agent.</li>
 *   <li>{@link #CODE_RESULT}      - Execution result of a code segment.</li>
 *   <li>{@link #EXPRESS}          - Final natural-language response expressed to the user.</li>
 *   <li>{@link #ROUND}            - Meta-segment summarising a completed reasoning round.</li>
 *   <li>{@link #TOOL_CALL}        - Invocation request sent to an external tool.</li>
 *   <li>{@link #TOOL_RESULT}      - Result returned from an external tool invocation.</li>
 *   <li>{@link #ERROR}            - Error or exception captured during processing.</li>
 *   <li>{@link #SYSTEM}           - System-level instruction or configuration prompt.</li>
 *   <li>{@link #ENVIRONMENT}      - Contextual information about the runtime environment.</li>
 * </ul>
 *
 * @author agent-server
 */
public enum SegmentType {

    /** Raw input provided by the end user. */
    USER_INPUT("USER_INPUT"),

    /** Internal reasoning / Chain-of-Thought step. */
    THOUGHT("THOUGHT"),

    /** Source code generated or referenced by the agent. */
    CODE("CODE"),

    /** Execution result of a code segment. */
    CODE_RESULT("CODE_RESULT"),

    /** Final natural-language response expressed to the user. */
    EXPRESS("EXPRESS"),

    /** Meta-segment summarising a completed reasoning round. */
    ROUND("ROUND"),

    /** Invocation request sent to an external tool. */
    TOOL_CALL("TOOL_CALL"),

    /** Result returned from an external tool invocation. */
    TOOL_RESULT("TOOL_RESULT"),

    /** Error or exception captured during processing. */
    ERROR("ERROR"),

    /** System-level instruction or configuration prompt. */
    SYSTEM("SYSTEM"),

    /** Contextual information about the runtime environment. */
    ENVIRONMENT("ENVIRONMENT");

    private final String value;

    SegmentType(String value) {
        this.value = value;
    }

    /**
     * Returns the string representation used for prompt formatting and JSON serialization.
     *
     * @return the segment type value
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolves a {@link SegmentType} from its string value (case-insensitive).
     *
     * @param value the string value to resolve
     * @return the matching {@link SegmentType}
     * @throws IllegalArgumentException if no matching type is found
     */
    public static SegmentType fromValue(String value) {
        for (SegmentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SegmentType: " + value);
    }
}
