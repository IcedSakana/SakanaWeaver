package com.agent.protocol.ws;

import com.agent.common.utils.JsonUtils;
import com.agent.model.event.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages WebSocket sessions per agent session.
 * Multiple browser windows may connect to the same session,
 * so one sessionId can have multiple WebSocket connections.
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    /** sessionId -> list of active WebSocket connections */
    private final Map<String, List<WebSocketSession>> sessionConnections = new ConcurrentHashMap<>();

    /**
     * Add a WebSocket connection for a session.
     */
    public void addConnection(String sessionId, WebSocketSession wsSession) {
        sessionConnections.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                .add(wsSession);
        log.info("Added WS connection for session={}, total={}", sessionId,
                sessionConnections.get(sessionId).size());
    }

    /**
     * Remove a WebSocket connection for a session.
     */
    public void removeConnection(String sessionId, WebSocketSession wsSession) {
        List<WebSocketSession> connections = sessionConnections.get(sessionId);
        if (connections != null) {
            connections.remove(wsSession);
            if (connections.isEmpty()) {
                sessionConnections.remove(sessionId);
            }
            log.info("Removed WS connection for session={}, remaining={}", sessionId,
                    connections.size());
        }
    }

    /**
     * Broadcast an event to all WebSocket connections of a session.
     */
    public void broadcast(String sessionId, Event event) {
        List<WebSocketSession> connections = sessionConnections.get(sessionId);
        if (connections == null || connections.isEmpty()) {
            log.debug("No active WS connections for session={}", sessionId);
            return;
        }

        String json = JsonUtils.toJson(event);
        TextMessage message = new TextMessage(json);

        for (WebSocketSession ws : connections) {
            if (ws.isOpen()) {
                try {
                    synchronized (ws) {
                        ws.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to WS session={}", ws.getId(), e);
                }
            }
        }
    }

    /**
     * Check if a session has active WebSocket connections.
     */
    public boolean hasConnections(String sessionId) {
        List<WebSocketSession> connections = sessionConnections.get(sessionId);
        return connections != null && !connections.isEmpty();
    }

    /**
     * Get the number of active connections for a session.
     */
    public int getConnectionCount(String sessionId) {
        List<WebSocketSession> connections = sessionConnections.get(sessionId);
        return connections != null ? connections.size() : 0;
    }
}
