package com.agent.model.event;

/**
 * Event type enumeration.
 * Defines all types of events in the agent system.
 */
public enum EventType {

    /** User input message */
    USER_MESSAGE,

    /** Agent reply message */
    AGENT_MESSAGE,

    /** Sub-agent reply message */
    SUB_AGENT_MESSAGE,

    /** System heartbeat */
    HEART_BEAT,

    /** Agent execution progress */
    AGENT_EXEC_PROGRESS,

    /** System error */
    SYSTEM_ERROR,

    /** Agent sleep event */
    AGENT_SLEEP,

    /** Agent wake event */
    AGENT_WAKE,

    /** Task status change */
    TASK_STATUS_CHANGE,

    /** MCP tool call event */
    MCP_TOOL_CALL,

    /** MCP tool result event */
    MCP_TOOL_RESULT
}
