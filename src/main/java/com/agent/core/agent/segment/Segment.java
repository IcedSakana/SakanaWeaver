package com.agent.core.agent.segment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Core model representing a single segment in the Agent's Chain-of-Thought (CoT) reasoning.
 *
 * <p>A segment is the atomic building block of Context Engineering. It follows the
 * {@code [Segment]: [Content]} structure and captures one discrete piece of information
 * (user input, thought, code, tool invocation, etc.) within a reasoning round.</p>
 *
 * <p>Example prompt output:</p>
 * <pre>
 *   [THOUGHT]: The user is asking for a summary of the latest sales data.
 *   [TOOL_CALL]: query_database {"table": "sales", "limit": 100}
 *   [TOOL_RESULT]: {"rows": [...]}
 *   [EXPRESS]: Here is a summary of the latest sales data ...
 * </pre>
 *
 * @author agent-server
 * @see SegmentType
 * @see SegmentContext
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Segment {

    /**
     * Unique identifier for this segment. Auto-generated if not provided.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * The type of this segment, determining its role within the reasoning chain.
     */
    private SegmentType type;

    /**
     * The textual content carried by this segment.
     */
    private String content;

    /**
     * Creation timestamp in epoch milliseconds. Defaults to the current time.
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();

    /**
     * The zero-based index of the reasoning round this segment belongs to.
     * A round groups related segments from a single user-interaction cycle.
     */
    private Integer roundIndex;

    /**
     * Optional metadata map for attaching arbitrary key-value pairs
     * (e.g., tool names, execution duration, token counts).
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Formats this segment into the standard prompt representation used by the
     * Context Engineering framework.
     *
     * <p>The format is: {@code [{type}]: {content}}</p>
     *
     * @return the prompt-formatted string, e.g. {@code [THOUGHT]: I need to check the DB.}
     */
    public String toPromptFormat() {
        return "[" + type.getValue() + "]: " + content;
    }

    /**
     * Adds a single metadata entry to this segment.
     *
     * @param key   the metadata key
     * @param value the metadata value
     * @return this segment instance for fluent chaining
     */
    public Segment withMeta(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
}
