package com.agent.core.agent.persist;

import com.agent.common.utils.JsonUtils;
import com.agent.model.agent.AgentContext;
import com.agent.persist.entity.AgentContextEntity;
import com.agent.persist.mapper.AgentContextMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Agent persistence implementation using MySQL.
 * Serializes agent context to JSON and stores in database.
 */
@Slf4j
@Service
public class AgentPersistImpl implements AgentPersist {

    private final AgentContextMapper agentContextMapper;

    public AgentPersistImpl(AgentContextMapper agentContextMapper) {
        this.agentContextMapper = agentContextMapper;
    }

    @Override
    public void dump(String sessionId, AgentContext context) {
        try {
            String contextJson = JsonUtils.toJson(context);

            AgentContextEntity existing = agentContextMapper.selectBySessionId(sessionId);
            if (existing != null) {
                existing.setContextData(contextJson);
                existing.setUpdatedAt(LocalDateTime.now());
                agentContextMapper.updateContextData(sessionId, contextJson, LocalDateTime.now());
            } else {
                AgentContextEntity entity = new AgentContextEntity();
                entity.setSessionId(sessionId);
                entity.setContextData(contextJson);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                agentContextMapper.insert(entity);
            }

            log.debug("Dumped agent context: sessionId={}, size={} bytes", sessionId, contextJson.length());
        } catch (Exception e) {
            log.error("Failed to dump agent context: sessionId={}", sessionId, e);
            throw new RuntimeException("Agent context dump failed", e);
        }
    }

    @Override
    public AgentContext load(String sessionId) {
        try {
            AgentContextEntity entity = agentContextMapper.selectBySessionId(sessionId);
            if (entity == null || entity.getContextData() == null) {
                log.warn("No persisted context found for session={}", sessionId);
                return null;
            }

            AgentContext context = JsonUtils.fromJson(entity.getContextData(), AgentContext.class);
            log.info("Loaded agent context: sessionId={}", sessionId);
            return context;
        } catch (Exception e) {
            log.error("Failed to load agent context: sessionId={}", sessionId, e);
            return null;
        }
    }

    @Override
    public void delete(String sessionId) {
        agentContextMapper.deleteBySessionId(sessionId);
        log.info("Deleted agent context: sessionId={}", sessionId);
    }
}
