package com.agent.core.agent.persist;

import com.agent.model.agent.AgentContext;

/**
 * Agent persistence interface.
 * Handles dump (serialize to DB) and load (deserialize from DB) of agent context.
 *
 * Used for:
 * - Session sleep/wake: active dump and load
 * - Service shutdown: graceful dump before node goes offline
 * - Crash recovery: periodic checkpoint dump
 */
public interface AgentPersist {

    /**
     * Dump agent context to persistent storage.
     *
     * @param sessionId session identifier
     * @param context   agent context to persist
     */
    void dump(String sessionId, AgentContext context);

    /**
     * Load agent context from persistent storage.
     *
     * @param sessionId session identifier
     * @return agent context, or null if not found
     */
    AgentContext load(String sessionId);

    /**
     * Delete persisted agent context.
     *
     * @param sessionId session identifier
     */
    void delete(String sessionId);
}
