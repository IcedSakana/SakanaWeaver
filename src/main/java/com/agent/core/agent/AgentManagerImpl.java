package com.agent.core.agent;

import com.agent.common.exception.AgentException;
import com.agent.core.agent.persist.AgentPersist;
import com.agent.core.agent.react.ReActAgent;
import com.agent.core.event.EventCenter;
import com.agent.core.mcp.McpManager;
import com.agent.core.task.TaskManager;
import com.agent.model.agent.AgentContext;
import com.agent.model.agent.AgentInstance;
import com.agent.model.agent.AgentStatus;
import com.agent.model.event.Event;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent manager implementation.
 * Manages all agent instances on this server node.
 *
 * Key behaviors:
 * - One session -> one agent instance (1:1 mapping)
 * - Agent instances run in the server runtime (not in external sandboxes)
 * - Supports sleep/wake for resource management
 * - Periodic checkpoint dump every 30 seconds for crash recovery
 */
@Slf4j
@Service
public class AgentManagerImpl implements AgentManager {

    /** sessionId -> AgentInstance */
    private final Map<String, AgentInstance> agentInstances = new ConcurrentHashMap<>();

    /** sessionId -> ReActAgent (the running agent logic) */
    private final Map<String, ReActAgent> agentRunners = new ConcurrentHashMap<>();

    @Value("${agent.node-id:#{T(java.util.UUID).randomUUID().toString().substring(0,8)}}")
    private String nodeId;

    private final EventCenter eventCenter;
    private final AgentPersist agentPersist;
    private final TaskManager taskManager;
    private final McpManager mcpManager;

    public AgentManagerImpl(EventCenter eventCenter,
                             AgentPersist agentPersist,
                             TaskManager taskManager,
                             McpManager mcpManager) {
        this.eventCenter = eventCenter;
        this.agentPersist = agentPersist;
        this.taskManager = taskManager;
        this.mcpManager = mcpManager;
    }

    @Override
    public AgentInstance startAgent(String sessionId) {
        if (agentInstances.containsKey(sessionId)) {
            throw new AgentException("AGENT_EXISTS", "Agent already exists for session: " + sessionId);
        }

        // Create agent instance
        AgentInstance instance = AgentInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .status(AgentStatus.INIT)
                .context(AgentContext.builder()
                        .sessionId(sessionId)
                        .shortTermMemory(new java.util.ArrayList<>())
                        .actContexts(new java.util.HashMap<>())
                        .availableTools(mcpManager.getAvailableToolNames())
                        .build())
                .nodeId(nodeId)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();

        // Create ReAct agent runner
        ReActAgent runner = new ReActAgent(sessionId, instance, eventCenter, taskManager, mcpManager);
        runner.initialize();

        agentInstances.put(sessionId, instance);
        agentRunners.put(sessionId, runner);

        // Subscribe to input events from EventCenter
        eventCenter.subscribeInput(sessionId, event -> {
            runner.onInputEvent(event);
        });

        instance.setStatus(AgentStatus.IDLE);
        log.info("Agent started: sessionId={}, instanceId={}, nodeId={}", sessionId, instance.getInstanceId(), nodeId);

        return instance;
    }

    @Override
    @Async("agentExecutor")
    public void acceptInput(String sessionId, Event event) {
        ReActAgent runner = agentRunners.get(sessionId);
        if (runner == null) {
            // Try to resume if agent was sleeping
            AgentInstance instance = resumeAgent(sessionId);
            runner = agentRunners.get(sessionId);
            if (runner == null) {
                throw new AgentException("AGENT_NOT_FOUND", "No agent instance for session: " + sessionId);
            }
        }

        AgentInstance instance = agentInstances.get(sessionId);
        if (instance != null) {
            instance.setStatus(AgentStatus.RUNNING);
            instance.setLastActiveAt(LocalDateTime.now());
        }

        try {
            // Execute agent processing
            runner.processInput(event);
        } finally {
            if (instance != null && !instance.shouldSleep() && !instance.shouldStop()) {
                instance.setStatus(AgentStatus.IDLE);
            }
        }
    }

    @Override
    public void sleepAgent(String sessionId) {
        AgentInstance instance = agentInstances.get(sessionId);
        ReActAgent runner = agentRunners.get(sessionId);

        if (instance == null || runner == null) {
            log.warn("Cannot sleep agent: not found for session={}", sessionId);
            return;
        }

        // Send sleep signal to agent
        instance.requestSleep();

        // Wait for current execution to finish, then dump
        runner.onSleep();

        // Persist agent context
        agentPersist.dump(sessionId, instance.getContext());

        // Unsubscribe from input events
        eventCenter.unsubscribeInput(sessionId);

        // Remove from memory
        instance.setStatus(AgentStatus.SLEEPING);
        agentRunners.remove(sessionId);
        agentInstances.remove(sessionId);

        log.info("Agent sleeping: sessionId={}", sessionId);
    }

    @Override
    public AgentInstance resumeAgent(String sessionId) {
        if (agentInstances.containsKey(sessionId)) {
            AgentInstance existing = agentInstances.get(sessionId);
            existing.setStatus(AgentStatus.IDLE);
            return existing;
        }

        // Load persisted context
        AgentContext context = agentPersist.load(sessionId);
        if (context == null) {
            throw new AgentException("AGENT_NO_CONTEXT", "No persisted context for session: " + sessionId);
        }

        // Recreate agent instance
        AgentInstance instance = AgentInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .status(AgentStatus.INIT)
                .context(context)
                .nodeId(nodeId)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();

        // Recreate ReAct agent runner with restored context
        ReActAgent runner = new ReActAgent(sessionId, instance, eventCenter, taskManager, mcpManager);
        runner.restore(context);

        agentInstances.put(sessionId, instance);
        agentRunners.put(sessionId, runner);

        // Re-subscribe to input events
        eventCenter.subscribeInput(sessionId, event -> {
            runner.onInputEvent(event);
        });

        instance.setStatus(AgentStatus.IDLE);
        log.info("Agent resumed: sessionId={}, instanceId={}", sessionId, instance.getInstanceId());

        return instance;
    }

    @Override
    public void stopAgent(String sessionId) {
        AgentInstance instance = agentInstances.get(sessionId);
        ReActAgent runner = agentRunners.get(sessionId);

        if (instance != null) {
            instance.requestStop();
        }
        if (runner != null) {
            runner.onStop();
        }

        eventCenter.unsubscribeInput(sessionId);
        agentRunners.remove(sessionId);
        agentInstances.remove(sessionId);

        log.info("Agent stopped: sessionId={}", sessionId);
    }

    @Override
    public AgentInstance getAgent(String sessionId) {
        return agentInstances.get(sessionId);
    }

    @Override
    public int getActiveAgentCount() {
        return agentInstances.size();
    }

    /**
     * Periodic checkpoint: dump all agent instances every 30 seconds.
     * This ensures crash recovery with minimal data loss.
     */
    @Scheduled(fixedRate = 30_000)
    public void checkpointDump() {
        for (Map.Entry<String, AgentInstance> entry : agentInstances.entrySet()) {
            try {
                agentPersist.dump(entry.getKey(), entry.getValue().getContext());
            } catch (Exception e) {
                log.error("Checkpoint dump failed for session={}", entry.getKey(), e);
            }
        }
        if (!agentInstances.isEmpty()) {
            log.debug("Checkpoint completed: {} agents dumped", agentInstances.size());
        }
    }

    /**
     * Dump all agents on graceful shutdown.
     */
    @Override
    @PreDestroy
    public void dumpAll() {
        log.info("Graceful shutdown: dumping {} agent instances", agentInstances.size());
        for (Map.Entry<String, AgentInstance> entry : agentInstances.entrySet()) {
            try {
                String sessionId = entry.getKey();
                AgentInstance instance = entry.getValue();
                instance.requestSleep();

                ReActAgent runner = agentRunners.get(sessionId);
                if (runner != null) {
                    runner.onSleep();
                }

                agentPersist.dump(sessionId, instance.getContext());
                log.info("Dumped agent for session={}", sessionId);
            } catch (Exception e) {
                log.error("Failed to dump agent for session={}", entry.getKey(), e);
            }
        }
        // TODO: Send MetaQ message for other nodes to pick up these sessions
    }
}
