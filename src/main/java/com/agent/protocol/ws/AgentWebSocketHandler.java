package com.agent.protocol.ws;

import com.agent.common.utils.JsonUtils;
import com.agent.core.event.EventCenter;
import com.agent.core.session.SessionManager;
import com.agent.model.event.Event;
import com.agent.model.event.EventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for agent real-time communication.
 * Manages WebSocket connections and routes events between frontend and agent instances.
 *
 * Connection path: /ws/sessions/{sessionId}/connection
 */
@Slf4j
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final EventCenter eventCenter;
    private final WebSocketSessionManager wsSessionManager;

    public AgentWebSocketHandler(SessionManager sessionManager,
                                  EventCenter eventCenter,
                                  WebSocketSessionManager wsSessionManager) {
        this.sessionManager = sessionManager;
        this.eventCenter = eventCenter;
        this.wsSessionManager = wsSessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        String sessionId = extractSessionId(wsSession);
        if (sessionId == null) {
            wsSession.close(CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        log.info("WebSocket connected: sessionId={}, wsSessionId={}", sessionId, wsSession.getId());

        // Register this WebSocket connection
        wsSessionManager.addConnection(sessionId, wsSession);

        // Subscribe to output events for this session via EventCenter
        eventCenter.subscribeOutput(sessionId, event -> {
            sendEventToClient(wsSession, event);
        });

        // Send heartbeat to confirm connection
        Event heartbeat = Event.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .eventType(EventType.HEART_BEAT)
                .timestamp(java.time.LocalDateTime.now())
                .build();
        sendEventToClient(wsSession, heartbeat);
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String sessionId = extractSessionId(wsSession);
        if (sessionId == null) return;

        String payload = message.getPayload();
        log.debug("Received message from sessionId={}: {}", sessionId, payload);

        try {
            Event event = JsonUtils.fromJson(payload, Event.class);
            event.setSessionId(sessionId);
            event.setEventType(EventType.USER_MESSAGE);

            // Publish user input event to EventCenter, which routes it to the agent instance
            eventCenter.publishInput(sessionId, event);

            // Also forward to session manager for processing
            sessionManager.acceptInput(sessionId, event);

        } catch (Exception e) {
            log.error("Failed to process message from sessionId={}", sessionId, e);
            Event errorEvent = Event.errorEvent(sessionId, "Failed to process message: " + e.getMessage());
            sendEventToClient(wsSession, errorEvent);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(wsSession);
        if (sessionId != null) {
            log.info("WebSocket disconnected: sessionId={}, status={}", sessionId, status);
            wsSessionManager.removeConnection(sessionId, wsSession);
            eventCenter.unsubscribeOutput(sessionId, wsSession.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) throws Exception {
        String sessionId = extractSessionId(wsSession);
        log.error("WebSocket transport error: sessionId={}", sessionId, exception);
        if (wsSession.isOpen()) {
            wsSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Send an event to a specific WebSocket client.
     */
    private void sendEventToClient(WebSocketSession wsSession, Event event) {
        if (wsSession.isOpen()) {
            try {
                String json = JsonUtils.toJson(event);
                wsSession.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("Failed to send event to wsSession={}", wsSession.getId(), e);
            }
        }
    }

    /**
     * Extract sessionId from WebSocket URI path.
     */
    private String extractSessionId(WebSocketSession wsSession) {
        URI uri = wsSession.getUri();
        if (uri == null) return null;

        String path = uri.getPath();
        // Path: /ws/sessions/{sessionId}/connection
        String[] parts = path.split("/");
        if (parts.length >= 4 && "sessions".equals(parts[2])) {
            return parts[3];
        }

        // Also check attributes set by interceptor
        Map<String, Object> attrs = wsSession.getAttributes();
        return (String) attrs.get("sessionId");
    }
}
