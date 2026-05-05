package com.agent.core.agent;

import com.agent.model.agent.AgentInstance;
import com.agent.model.event.Event;

/**
 * Agent manager interface.
 * Manages agent instance lifecycle: create, sleep, resume, stop.
 */
public interface AgentManager {

    /**
     * Start a new agent instance for a session.
     *
     * @param sessionId session identifier
     * @return the created agent instance
     */
    AgentInstance startAgent(String sessionId);

    /**
     * Accept user input and forward to the agent instance.
     *
     * @param sessionId session identifier
     * @param event     user input event
     */
    void acceptInput(String sessionId, Event event);

    /**
     * Sleep (hibernate) an agent instance.
     * Persists runtime data and releases memory.
     *
     * @param sessionId session identifier
     */
    void sleepAgent(String sessionId);

    /**
     * Resume a sleeping agent instance.
     * Loads persisted runtime data back into memory.
     *
     * @param sessionId session identifier
     * @return the resumed agent instance
     */
    AgentInstance resumeAgent(String sessionId);

    /**
     * Stop an agent instance.
     *
     * @param sessionId session identifier
     */
    void stopAgent(String sessionId);

    /**
     * Get the agent instance for a session.
     *
     * @param sessionId session identifier
     * @return agent instance or null
     */
    AgentInstance getAgent(String sessionId);

    /**
     * Dump all agent instances (for graceful shutdown).
     */
    void dumpAll();

    /**
     * Get the count of active agent instances on this node.
     */
    int getActiveAgentCount();
}
