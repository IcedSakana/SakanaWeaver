package com.agent.model.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Session model.
 * One session maps to exactly one Agent instance.
 * A session supports multi-turn conversations between user and agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    /** Unique session ID */
    private String sessionId;

    /** User ID who owns this session */
    private String userId;

    /** Current session status */
    private SessionStatus status;

    /** Session title/summary */
    private String title;

    /** Session creation time */
    private LocalDateTime createdAt;

    /** Last active time */
    private LocalDateTime lastActiveAt;

    /** Session-level metadata */
    private Map<String, Object> metadata;

    /** The server node that holds the agent instance */
    private String nodeId;

    /**
     * Check if this session is active (INIT or RUNNING).
     */
    public boolean isActive() {
        return status == SessionStatus.INIT || status == SessionStatus.RUNNING;
    }

    /**
     * Check if this session can accept new input.
     */
    public boolean canAccept() {
        return status == SessionStatus.RUNNING;
    }
}
