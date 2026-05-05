package com.agent.core.agent.memory;

/**
 * Memory type enum based on the Atkinson-Shiffrin Memory Model.
 *
 * <p>Defines the three layers of the memory system:
 * <ul>
 *   <li>{@link #SENSORY} - Ultra-short-lived environment perception memory,
 *       invalidated on every new page action.</li>
 *   <li>{@link #SHORT_TERM} - Session-scoped working memory with bounded capacity.</li>
 *   <li>{@link #LONG_TERM} - Persistent memory for knowledge, experience,
 *       preferences and chat summaries.</li>
 * </ul>
 *
 * @author agent-server
 */
public enum MemoryType {

    /**
     * Sensory memory: the most ephemeral layer.
     * Captures raw environmental context such as the current page URL,
     * page title and recent user actions. Automatically invalidated
     * whenever a new page action occurs.
     */
    SENSORY,

    /**
     * Short-term (working) memory: session-level layer.
     * Holds segment and entity memories accumulated during a single
     * session, with a configurable maximum capacity.
     */
    SHORT_TERM,

    /**
     * Long-term memory: the persistent layer.
     * Stores knowledge points, execution experiences, user preferences,
     * and historical chat summaries. Supports time-based eviction.
     */
    LONG_TERM
}
