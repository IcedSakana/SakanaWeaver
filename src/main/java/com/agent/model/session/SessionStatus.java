package com.agent.model.session;

/**
 * Session status enumeration.
 */
public enum SessionStatus {

    /** Initializing - agent instance starting */
    INIT,

    /** Active - agent is processing */
    RUNNING,

    /** Sleeping - no user input for a while or user manually suspended */
    SLEEP,

    /** Closed - user closed the session */
    CLOSED
}
