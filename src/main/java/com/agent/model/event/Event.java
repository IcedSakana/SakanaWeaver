package com.agent.model.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Unified Event protocol for frontend-backend communication via WebSocket.
 * Each Event maps to one WebSocket frame for streaming.
 *
 * Unique bubble identifier: eventSource + taskId + artifact.index
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {

    /** Unique event ID */
    private String eventId;

    /** Session ID this event belongs to */
    private String sessionId;

    /** Whether this event is part of a stream */
    private Boolean stream;

    /** Event type */
    private EventType eventType;

    /** Event source identifier (e.g., agent name or sub-agent name) */
    private String eventSource;

    /** Event source type (e.g., MAIN_AGENT, SUB_AGENT, SYSTEM) */
    private String eventSourceType;

    /** Task ID this event is associated with */
    private String taskId;

    /** Artifact payload (aligned with A2A protocol) */
    private Artifact artifact;

    /** Event creation timestamp */
    private LocalDateTime timestamp;

    /** Additional metadata */
    private java.util.Map<String, Object> extra;

    /**
     * Create a simple text event.
     */
    public static Event textEvent(String sessionId, EventType type, String source, String text) {
        return Event.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .stream(false)
                .eventType(type)
                .eventSource(source)
                .eventSourceType("MAIN_AGENT")
                .artifact(Artifact.builder()
                        .parts(java.util.List.of(
                                Artifact.ArtifactPart.builder()
                                        .type("text")
                                        .data(text)
                                        .build()))
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a streaming chunk event.
     */
    public static Event streamChunkEvent(String sessionId, String taskId, String source, String chunk, int index) {
        return Event.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .stream(true)
                .eventType(EventType.AGENT_MESSAGE)
                .eventSource(source)
                .eventSourceType("MAIN_AGENT")
                .taskId(taskId)
                .artifact(Artifact.builder()
                        .index(index)
                        .parts(java.util.List.of(
                                Artifact.ArtifactPart.builder()
                                        .type("text")
                                        .data(chunk)
                                        .build()))
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an execution progress event.
     */
    public static Event progressEvent(String sessionId, String taskId, String source, String status, String detail) {
        return Event.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .stream(false)
                .eventType(EventType.AGENT_EXEC_PROGRESS)
                .eventSource(source)
                .taskId(taskId)
                .artifact(Artifact.builder()
                        .metadata(java.util.Map.of("status", status))
                        .parts(java.util.List.of(
                                Artifact.ArtifactPart.builder()
                                        .type("text")
                                        .data(detail)
                                        .build()))
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a system error event.
     */
    public static Event errorEvent(String sessionId, String errorMessage) {
        return Event.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .stream(false)
                .eventType(EventType.SYSTEM_ERROR)
                .eventSource("SYSTEM")
                .eventSourceType("SYSTEM")
                .artifact(Artifact.builder()
                        .parts(java.util.List.of(
                                Artifact.ArtifactPart.builder()
                                        .type("text")
                                        .data(errorMessage)
                                        .build()))
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
