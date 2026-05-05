package com.agent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Memory message representing an inference segment produced during agent execution.
 *
 * <p>A segment captures a single turn of interaction within a session,
 * recording the segment type (e.g. {@code THOUGHT}, {@code ACTION},
 * {@code OBSERVATION}), the round index within the execution loop,
 * and the conversational role of the participant.
 *
 * <p>This is the core memory carrier of agent execution and is stored
 * inside {@link ShortTermMemory}.
 *
 * @author agent-server
 * @see MemoryMessage
 * @see ShortTermMemory
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SegmentMemoryMessage extends MemoryMessage {

    /**
     * The type of this segment, derived from the {@code SegmentType} enum name.
     * Typical values include {@code "THOUGHT"}, {@code "ACTION"},
     * {@code "OBSERVATION"}, {@code "PLAN"}, etc.
     */
    private String segmentType;

    /**
     * Zero-based index of the execution round this segment belongs to.
     * Multiple segments may share the same round index when they
     * represent different phases of a single reasoning step.
     */
    private Integer roundIndex;

    /**
     * The conversational role that produced this segment.
     * Accepted values: {@code "user"}, {@code "assistant"},
     * {@code "system"}, {@code "tool"}.
     */
    private String role;
}
