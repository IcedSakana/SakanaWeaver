package com.agent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all memory messages in the 3-layer memory system.
 *
 * <p>Every memory message carries a unique identifier, a {@link MemoryType},
 * session and user scoping information, the actual content, timing
 * metadata and an extensible metadata map.
 *
 * <p>Subclasses such as {@link SegmentMemoryMessage} and
 * {@link EntityMemoryMessage} extend this base with domain-specific fields.
 *
 * @author agent-server
 * @see SegmentMemoryMessage
 * @see EntityMemoryMessage
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryMessage {

    /**
     * Unique identifier for this memory message.
     * Defaults to a random UUID string when not explicitly set.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * The memory layer this message belongs to.
     */
    private MemoryType memoryType;

    /**
     * The session this memory is associated with.
     */
    private String sessionId;

    /**
     * The user this memory is associated with.
     */
    private String userId;

    /**
     * The textual content of the memory.
     */
    private String content;

    /**
     * Creation / recording timestamp in epoch milliseconds.
     */
    private Long timestamp;

    /**
     * Optional expiration timestamp in epoch milliseconds.
     * {@code null} means the memory never expires by time alone.
     */
    private Long expireAt;

    /**
     * Extensible metadata map for attaching arbitrary key-value pairs.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
