package com.agent.model.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent instance model.
 * Represents a running agent bound to a specific session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInstance {

    /** Unique agent instance ID */
    private String instanceId;

    /** Bound session ID */
    private String sessionId;

    /** Current status */
    private AgentStatus status;

    /** Agent context (runtime data) */
    private AgentContext context;

    /** Server node holding this instance */
    private String nodeId;

    /** Creation time */
    private LocalDateTime createdAt;

    /** Last active time */
    private LocalDateTime lastActiveAt;

    /** Sleep signal */
    private volatile boolean sleepSignal;

    /** Stop signal */
    private volatile boolean stopSignal;

    /**
     * Send sleep signal to this agent instance.
     */
    public void requestSleep() {
        this.sleepSignal = true;
    }

    /**
     * Send stop signal to this agent instance.
     */
    public void requestStop() {
        this.stopSignal = true;
    }

    /**
     * Check if the agent should sleep.
     */
    public boolean shouldSleep() {
        return sleepSignal;
    }

    /**
     * Check if the agent should stop.
     */
    public boolean shouldStop() {
        return stopSignal;
    }
}
