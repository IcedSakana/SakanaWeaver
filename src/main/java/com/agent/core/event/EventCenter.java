package com.agent.core.event;

import com.agent.model.event.Event;

import java.util.function.Consumer;

/**
 * EventCenter interface.
 * Central hub for event routing between agent instances and WebSocket connections.
 * In distributed architecture, implemented via Redis Pub/Sub.
 *
 * Topics:
 * - {sessionId}_input: events from frontend to agent instance
 * - {sessionId}_output: events from agent instance to frontend
 */
public interface EventCenter {

    /**
     * Publish an input event (user message) to the agent instance.
     *
     * @param sessionId session identifier
     * @param event     the input event
     */
    void publishInput(String sessionId, Event event);

    /**
     * Publish an output event (agent response) to all connected frontends.
     *
     * @param sessionId session identifier
     * @param event     the output event
     */
    void publishOutput(String sessionId, Event event);

    /**
     * Subscribe to input events for a session (called by the machine holding the agent instance).
     *
     * @param sessionId session identifier
     * @param listener  callback when input event arrives
     */
    void subscribeInput(String sessionId, Consumer<Event> listener);

    /**
     * Subscribe to output events for a session (called by the machine holding WebSocket connections).
     *
     * @param sessionId session identifier
     * @param listener  callback when output event arrives
     */
    void subscribeOutput(String sessionId, Consumer<Event> listener);

    /**
     * Unsubscribe from output events.
     *
     * @param sessionId    session identifier
     * @param subscriberId subscriber identifier (e.g., WebSocket session ID)
     */
    void unsubscribeOutput(String sessionId, String subscriberId);

    /**
     * Unsubscribe from input events.
     *
     * @param sessionId session identifier
     */
    void unsubscribeInput(String sessionId);
}
