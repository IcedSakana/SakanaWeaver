package com.agent.model.agent;

/**
 * Agent instance status enumeration.
 */
public enum AgentStatus {

    /** Initializing */
    INIT,

    /** Running - processing user input */
    RUNNING,

    /** Idle - waiting for user input */
    IDLE,

    /** Sleeping - persisted to storage */
    SLEEPING,

    /** Stopped */
    STOPPED
}
