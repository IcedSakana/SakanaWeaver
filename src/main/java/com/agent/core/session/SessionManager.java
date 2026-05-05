package com.agent.core.session;

import com.agent.model.event.Event;
import com.agent.model.session.Session;
import com.agent.model.session.SessionStatus;

import java.util.List;

/**
 * Session manager interface.
 * Manages user sessions and coordinates with agent instances.
 */
public interface SessionManager {

    /**
     * Create a new session for a user.
     *
     * @param userId user identifier
     * @param title  session title
     * @return created session
     */
    Session createSession(String userId, String title);

    /**
     * Get a session by ID.
     *
     * @param sessionId session identifier
     * @return session or null
     */
    Session getSession(String sessionId);

    /**
     * Get all sessions for a user.
     *
     * @param userId user identifier
     * @return list of sessions
     */
    List<Session> getUserSessions(String userId);

    /**
     * Accept user input for a session.
     * Routes the input to the corresponding agent instance.
     *
     * @param sessionId session identifier
     * @param event     user input event
     */
    void acceptInput(String sessionId, Event event);

    /**
     * Update session status.
     *
     * @param sessionId session identifier
     * @param status    new status
     */
    void updateStatus(String sessionId, SessionStatus status);

    /**
     * Close a session.
     *
     * @param sessionId session identifier
     */
    void closeSession(String sessionId);

    /**
     * Sleep a session (and its agent instance).
     *
     * @param sessionId session identifier
     */
    void sleepSession(String sessionId);

    /**
     * Resume a sleeping session.
     *
     * @param sessionId session identifier
     */
    void resumeSession(String sessionId);

    /**
     * Get conversation history for a session.
     *
     * @param sessionId session identifier
     * @param limit     max number of messages
     * @param offset    offset for pagination
     * @return list of events
     */
    List<Event> getConversationHistory(String sessionId, int limit, int offset);
}
