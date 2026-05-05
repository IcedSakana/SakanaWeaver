package com.agent.core.agent.segment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class that provides convenient static factory methods for creating
 * {@link Segment} instances of each {@link SegmentType}.
 *
 * <p>Using {@code SegmentBuilder} avoids repetitive boiler-plate when constructing
 * segments throughout the agent pipeline:</p>
 * <pre>{@code
 *   Segment thought = SegmentBuilder.thought("I should query the inventory first.");
 *   Segment call    = SegmentBuilder.toolCall("query_inventory", Map.of("sku", "A123"));
 * }</pre>
 *
 * <p>All factory methods produce segments with a fresh UUID, the current timestamp, and
 * the appropriate {@link SegmentType}. Additional metadata can be attached afterwards
 * via {@link Segment#withMeta(String, Object)}.</p>
 *
 * @author agent-server
 * @see Segment
 * @see SegmentType
 */
public final class SegmentBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SegmentBuilder() {
        // Utility class -- no instantiation
    }

    /**
     * Creates a {@link SegmentType#USER_INPUT} segment.
     *
     * @param input the raw user input text
     * @return a new segment
     */
    public static Segment userInput(String input) {
        return Segment.builder()
                .type(SegmentType.USER_INPUT)
                .content(input)
                .build();
    }

    /**
     * Creates a {@link SegmentType#THOUGHT} segment representing an internal
     * Chain-of-Thought reasoning step.
     *
     * @param thought the reasoning text
     * @return a new segment
     */
    public static Segment thought(String thought) {
        return Segment.builder()
                .type(SegmentType.THOUGHT)
                .content(thought)
                .build();
    }

    /**
     * Creates a {@link SegmentType#CODE} segment containing source code
     * generated or referenced by the agent.
     *
     * @param code the source code text
     * @return a new segment
     */
    public static Segment code(String code) {
        return Segment.builder()
                .type(SegmentType.CODE)
                .content(code)
                .build();
    }

    /**
     * Creates a {@link SegmentType#CODE_RESULT} segment capturing the output
     * of an executed code block.
     *
     * @param result  the execution output
     * @param success {@code true} if execution succeeded, {@code false} otherwise
     * @return a new segment with {@code success} stored in metadata
     */
    public static Segment codeResult(String result, boolean success) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("success", success);

        return Segment.builder()
                .type(SegmentType.CODE_RESULT)
                .content(result)
                .metadata(metadata)
                .build();
    }

    /**
     * Creates a {@link SegmentType#EXPRESS} segment containing the final
     * natural-language response intended for the end user.
     *
     * @param expression the response text
     * @return a new segment
     */
    public static Segment express(String expression) {
        return Segment.builder()
                .type(SegmentType.EXPRESS)
                .content(expression)
                .build();
    }

    /**
     * Creates a {@link SegmentType#ROUND} meta-segment that summarises a
     * completed reasoning round.
     *
     * @param index   the zero-based round index
     * @param summary a human-readable summary of the round
     * @return a new segment with {@code roundIndex} and {@code roundSummary} metadata
     */
    public static Segment round(int index, String summary) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("roundIndex", index);
        metadata.put("roundSummary", summary);

        return Segment.builder()
                .type(SegmentType.ROUND)
                .content(summary)
                .roundIndex(index)
                .metadata(metadata)
                .build();
    }

    /**
     * Creates a {@link SegmentType#TOOL_CALL} segment representing an invocation
     * request to an external tool.
     *
     * <p>The tool name is stored in metadata, and the parameters are serialized to
     * JSON as the segment content.</p>
     *
     * @param toolName the name of the tool being invoked
     * @param params   the invocation parameters (will be serialized to JSON)
     * @return a new segment
     */
    public static Segment toolCall(String toolName, Map<String, Object> params) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("toolName", toolName);

        String content;
        try {
            content = toolName + " " + OBJECT_MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            content = toolName + " " + params;
        }

        return Segment.builder()
                .type(SegmentType.TOOL_CALL)
                .content(content)
                .metadata(metadata)
                .build();
    }

    /**
     * Creates a {@link SegmentType#TOOL_RESULT} segment capturing the result
     * returned from an external tool invocation.
     *
     * @param toolName the name of the tool that produced the result
     * @param result   the tool result (will be serialized to JSON if not a String)
     * @return a new segment
     */
    public static Segment toolResult(String toolName, Object result) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("toolName", toolName);

        String content;
        if (result instanceof String strResult) {
            content = strResult;
        } else {
            try {
                content = OBJECT_MAPPER.writeValueAsString(result);
            } catch (JsonProcessingException e) {
                content = String.valueOf(result);
            }
        }

        return Segment.builder()
                .type(SegmentType.TOOL_RESULT)
                .content(content)
                .metadata(metadata)
                .build();
    }

    /**
     * Creates a {@link SegmentType#ERROR} segment capturing an error or exception.
     *
     * @param errorMessage the error description
     * @return a new segment
     */
    public static Segment error(String errorMessage) {
        return Segment.builder()
                .type(SegmentType.ERROR)
                .content(errorMessage)
                .build();
    }

    /**
     * Creates a {@link SegmentType#SYSTEM} segment containing a system-level
     * instruction or configuration prompt.
     *
     * @param systemMessage the system instruction text
     * @return a new segment
     */
    public static Segment system(String systemMessage) {
        return Segment.builder()
                .type(SegmentType.SYSTEM)
                .content(systemMessage)
                .build();
    }

    /**
     * Creates a {@link SegmentType#ENVIRONMENT} segment describing the runtime
     * environment context (e.g. OS, available tools, workspace layout).
     *
     * @param environmentInfo the environment description
     * @return a new segment
     */
    public static Segment environment(String environmentInfo) {
        return Segment.builder()
                .type(SegmentType.ENVIRONMENT)
                .content(environmentInfo)
                .build();
    }
}
