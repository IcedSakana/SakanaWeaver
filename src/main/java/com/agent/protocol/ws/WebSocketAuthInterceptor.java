package com.agent.protocol.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket handshake interceptor for authentication and session ID extraction.
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        URI uri = request.getURI();
        String path = uri.getPath();

        // Extract sessionId from path: /ws/sessions/{sessionId}/connection
        String[] parts = path.split("/");
        if (parts.length >= 4 && "sessions".equals(parts[2])) {
            String sessionId = parts[3];
            attributes.put("sessionId", sessionId);
            log.debug("WebSocket handshake: sessionId={}", sessionId);

            // TODO: Add authentication logic here (e.g., token validation from query params)
            // String token = uri.getQuery(); // parse token from query parameters

            return true;
        }

        log.warn("WebSocket handshake rejected: invalid path={}", path);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }
}
