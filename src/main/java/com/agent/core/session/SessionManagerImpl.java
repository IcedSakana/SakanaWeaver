package com.agent.core.session;

import com.agent.common.exception.AgentException;
import com.agent.core.agent.AgentManager;
import com.agent.core.event.EventCenter;
import com.agent.model.event.Event;
import com.agent.model.session.Session;
import com.agent.model.session.SessionStatus;
import com.agent.persist.entity.SessionEntity;
import com.agent.persist.mapper.SessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session manager implementation.
 * Manages session lifecycle and coordinates with agent instances.
 *
 * Key behaviors:
 * - Each session has exactly one agent instance
 * - Sessions auto-sleep after 20 minutes of inactivity
 * - Session status changes trigger agent lifecycle operations
 */
@Slf4j
@Service
public class SessionManagerImpl implements SessionManager {

    /** In-memory session cache for quick access */
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();

    /** Session sleep timeout: 20 minutes */
    private static final long SLEEP_TIMEOUT_MINUTES = 20;

    private final AgentManager agentManager;
    private final EventCenter eventCenter;
    private final SessionMapper sessionMapper;

    public SessionManagerImpl(AgentManager agentManager,
                               EventCenter eventCenter,
                               SessionMapper sessionMapper) {
        this.agentManager = agentManager;
        this.eventCenter = eventCenter;
        this.sessionMapper = sessionMapper;
    }

    @Override
    public Session createSession(String userId, String title) {
        String sessionId = UUID.randomUUID().toString();
        Session session = Session.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(SessionStatus.INIT)
                .title(title)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .metadata(new HashMap<>())
                .build();

        // Persist to DB
        sessionMapper.insert(toEntity(session));

        // Cache in memory
        sessionCache.put(sessionId, session);

        // Start agent instance for this session
        agentManager.startAgent(sessionId);

        // Update status to RUNNING
        session.setStatus(SessionStatus.RUNNING);
        sessionMapper.updateStatus(sessionId, SessionStatus.RUNNING.name());

        log.info("Created session: sessionId={}, userId={}", sessionId, userId);
        return session;
    }

    @Override
    public Session getSession(String sessionId) {
        Session session = sessionCache.get(sessionId);
        if (session == null) {
            // Try load from DB
            SessionEntity entity = sessionMapper.selectById(sessionId);
            if (entity != null) {
                session = toModel(entity);
                sessionCache.put(sessionId, session);
            }
        }
        return session;
    }

    @Override
    public List<Session> getUserSessions(String userId) {
        List<SessionEntity> entities = sessionMapper.selectByUserId(userId);
        return entities.stream().map(this::toModel).toList();
    }

    @Override
    public void acceptInput(String sessionId, Event event) {
        Session session = getSession(sessionId);
        if (session == null) {
            throw new AgentException("SESSION_NOT_FOUND", "Session not found: " + sessionId);
        }

        // If sleeping, resume first
        if (session.getStatus() == SessionStatus.SLEEP) {
            resumeSession(sessionId);
        }

        if (!session.canAccept()) {
            throw new AgentException("SESSION_NOT_READY", "Session is not ready to accept input: " + session.getStatus());
        }

        // Update last active time
        session.setLastActiveAt(LocalDateTime.now());
        sessionMapper.updateLastActiveAt(sessionId, LocalDateTime.now());

        // Forward to agent manager
        agentManager.acceptInput(sessionId, event);
    }

    @Override
    public void updateStatus(String sessionId, SessionStatus status) {
        Session session = getSession(sessionId);
        if (session != null) {
            session.setStatus(status);
            sessionMapper.updateStatus(sessionId, status.name());
            log.info("Session status updated: sessionId={}, status={}", sessionId, status);
        }
    }

    @Override
    public void closeSession(String sessionId) {
        Session session = getSession(sessionId);
        if (session != null) {
            agentManager.stopAgent(sessionId);
            eventCenter.unsubscribeInput(sessionId);
            session.setStatus(SessionStatus.CLOSED);
            sessionMapper.updateStatus(sessionId, SessionStatus.CLOSED.name());
            sessionCache.remove(sessionId);
            log.info("Session closed: sessionId={}", sessionId);
        }
    }

    @Override
    public void sleepSession(String sessionId) {
        Session session = getSession(sessionId);
        if (session != null && session.getStatus() == SessionStatus.RUNNING) {
            agentManager.sleepAgent(sessionId);
            session.setStatus(SessionStatus.SLEEP);
            sessionMapper.updateStatus(sessionId, SessionStatus.SLEEP.name());
            log.info("Session sleeping: sessionId={}", sessionId);
        }
    }

    @Override
    public void resumeSession(String sessionId) {
        Session session = getSession(sessionId);
        if (session != null && session.getStatus() == SessionStatus.SLEEP) {
            agentManager.resumeAgent(sessionId);
            session.setStatus(SessionStatus.RUNNING);
            session.setLastActiveAt(LocalDateTime.now());
            sessionMapper.updateStatus(sessionId, SessionStatus.RUNNING.name());
            sessionMapper.updateLastActiveAt(sessionId, LocalDateTime.now());
            log.info("Session resumed: sessionId={}", sessionId);
        }
    }

    @Override
    public List<Event> getConversationHistory(String sessionId, int limit, int offset) {
        // Conversation history is stored separately (not via EventCenter streaming)
        // This queries the persisted conversation records
        // TODO: Implement with conversation history table
        return List.of();
    }

    /**
     * Scheduled task: auto-sleep inactive sessions.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000)
    public void autoSleepInactiveSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(SLEEP_TIMEOUT_MINUTES);
        for (Map.Entry<String, Session> entry : sessionCache.entrySet()) {
            Session session = entry.getValue();
            if (session.getStatus() == SessionStatus.RUNNING
                    && session.getLastActiveAt() != null
                    && session.getLastActiveAt().isBefore(threshold)) {
                log.info("Auto-sleeping inactive session: sessionId={}", session.getSessionId());
                sleepSession(session.getSessionId());
            }
        }
    }

    private SessionEntity toEntity(Session session) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(session.getSessionId());
        entity.setUserId(session.getUserId());
        entity.setStatus(session.getStatus().name());
        entity.setTitle(session.getTitle());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setLastActiveAt(session.getLastActiveAt());
        return entity;
    }

    private Session toModel(SessionEntity entity) {
        return Session.builder()
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .status(SessionStatus.valueOf(entity.getStatus()))
                .title(entity.getTitle())
                .createdAt(entity.getCreatedAt())
                .lastActiveAt(entity.getLastActiveAt())
                .build();
    }
}
