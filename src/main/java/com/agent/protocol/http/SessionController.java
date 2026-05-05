package com.agent.protocol.http;

import com.agent.core.session.SessionManager;
import com.agent.model.event.Event;
import com.agent.model.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Session management REST controller.
 * Provides HTTP endpoints for session CRUD and conversation history.
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionManager sessionManager;

    public SessionController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Create a new session.
     * POST /api/sessions
     */
    @PostMapping
    public ResponseEntity<Session> createSession(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String title = request.getOrDefault("title", "New Session");

        Session session = sessionManager.createSession(userId, title);
        return ResponseEntity.ok(session);
    }

    /**
     * Get session by ID.
     * GET /api/sessions/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Session> getSession(@PathVariable String sessionId) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    /**
     * Get all sessions for a user.
     * GET /api/sessions?userId=xxx
     */
    @GetMapping
    public ResponseEntity<List<Session>> getUserSessions(@RequestParam String userId) {
        List<Session> sessions = sessionManager.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Close a session.
     * DELETE /api/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> closeSession(@PathVariable String sessionId) {
        sessionManager.closeSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * Sleep a session.
     * POST /api/sessions/{sessionId}/sleep
     */
    @PostMapping("/{sessionId}/sleep")
    public ResponseEntity<Void> sleepSession(@PathVariable String sessionId) {
        sessionManager.sleepSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * Resume a sleeping session.
     * POST /api/sessions/{sessionId}/resume
     */
    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<Void> resumeSession(@PathVariable String sessionId) {
        sessionManager.resumeSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get conversation history.
     * GET /api/sessions/{sessionId}/history?limit=50&offset=0
     */
    @GetMapping("/{sessionId}/history")
    public ResponseEntity<List<Event>> getConversationHistory(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<Event> history = sessionManager.getConversationHistory(sessionId, limit, offset);
        return ResponseEntity.ok(history);
    }
}
