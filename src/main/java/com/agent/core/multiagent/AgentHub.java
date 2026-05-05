package com.agent.core.multiagent;

import com.agent.common.utils.JsonUtils;
import com.agent.core.event.EventCenter;
import com.agent.core.multiagent.a2a.A2AClient;
import com.agent.core.multiagent.a2a.A2AMessage;
import com.agent.core.multiagent.a2a.AgentCard;
import com.agent.model.event.Artifact;
import com.agent.model.event.Event;
import com.agent.model.event.EventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentHub - Multi-Agent management and routing.
 * Manages sub-agent registration, discovery, and invocation.
 *
 * Sub-agents are registered via A2A protocol agent-cards and abstracted
 * as special "tools" in the main agent's intent recognition.
 *
 * Communication flow:
 * 1. Sub-agent registers agent-card with AgentHub
 * 2. Main agent's IntentPlanner selects a sub-agent based on intent
 * 3. AgentHub routes the request to the sub-agent via A2A protocol
 * 4. Sub-agent response is forwarded to frontend or summarized by main agent
 */
@Slf4j
@Component
public class AgentHub {

    /** agentId -> SubAgentProxy */
    private final Map<String, SubAgentProxy> subAgents = new ConcurrentHashMap<>();

    private final EventCenter eventCenter;

    public AgentHub(EventCenter eventCenter) {
        this.eventCenter = eventCenter;
    }

    /**
     * Register a sub-agent by discovering its agent-card.
     *
     * @param agentId  unique agent identifier
     * @param baseUrl  base URL of the sub-agent
     */
    public void registerSubAgent(String agentId, String baseUrl) {
        try {
            AgentCard card = A2AClient.discoverAgentCard(baseUrl);
            A2AClient client = new A2AClient(card);

            SubAgentProxy proxy = SubAgentProxy.builder()
                    .agentId(agentId)
                    .agentCard(card)
                    .client(client)
                    .registeredAt(LocalDateTime.now())
                    .build();

            subAgents.put(agentId, proxy);
            log.info("Sub-agent registered: agentId={}, name={}, skills={}",
                    agentId, card.getName(), card.getSkills() != null ? card.getSkills().size() : 0);

        } catch (Exception e) {
            log.error("Failed to register sub-agent: agentId={}, baseUrl={}", agentId, baseUrl, e);
        }
    }

    /**
     * Register a sub-agent with a pre-built agent card.
     *
     * @param agentId  unique agent identifier
     * @param card     agent card
     */
    public void registerSubAgent(String agentId, AgentCard card) {
        A2AClient client = new A2AClient(card);
        SubAgentProxy proxy = SubAgentProxy.builder()
                .agentId(agentId)
                .agentCard(card)
                .client(client)
                .registeredAt(LocalDateTime.now())
                .build();

        subAgents.put(agentId, proxy);
        log.info("Sub-agent registered (with card): agentId={}, name={}", agentId, card.getName());
    }

    /**
     * Unregister a sub-agent.
     */
    public void unregisterSubAgent(String agentId) {
        subAgents.remove(agentId);
        log.info("Sub-agent unregistered: agentId={}", agentId);
    }

    /**
     * Invoke a sub-agent.
     *
     * @param sessionId session ID for event routing
     * @param agentId   sub-agent ID
     * @param input     user input
     * @param metadata  additional metadata (including forward, type settings)
     * @return sub-agent response
     */
    public Object invokeSubAgent(String sessionId, String agentId, String input,
                                  Map<String, Object> metadata) {
        SubAgentProxy proxy = subAgents.get(agentId);
        if (proxy == null) {
            throw new RuntimeException("Sub-agent not found: " + agentId);
        }

        AgentCard card = proxy.getAgentCard();
        A2AClient client = proxy.getClient();

        log.info("Invoking sub-agent: agentId={}, name={}, streaming={}",
                agentId, card.getName(), card.isStreaming());

        if (card.isStreaming()) {
            // Streaming invocation via SSE
            return invokeStreaming(sessionId, agentId, client, input, metadata);
        } else {
            // Synchronous invocation
            return invokeSynchronous(sessionId, agentId, client, input, metadata);
        }
    }

    /**
     * Synchronous sub-agent invocation.
     */
    private Object invokeSynchronous(String sessionId, String agentId, A2AClient client,
                                      String input, Map<String, Object> metadata) {
        A2AMessage response = client.sendTask(input, metadata);

        // Check if result should be forwarded directly to frontend
        boolean forward = metadata != null && Boolean.TRUE.equals(metadata.get("forward"));

        if (forward) {
            // Forward directly to frontend
            eventCenter.publishOutput(sessionId, Event.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .eventType(EventType.SUB_AGENT_MESSAGE)
                    .eventSource(agentId)
                    .eventSourceType("SUB_AGENT")
                    .artifact(Artifact.builder()
                            .metadata(metadata)
                            .parts(convertA2AParts(response))
                            .build())
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        return response;
    }

    /**
     * Streaming sub-agent invocation via SSE.
     */
    private Object invokeStreaming(String sessionId, String agentId, A2AClient client,
                                    String input, Map<String, Object> metadata) {
        final int[] index = {0};

        client.sendTaskStreaming(input, metadata, chunk -> {
            // Forward each chunk to frontend
            eventCenter.publishOutput(sessionId, Event.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .stream(true)
                    .eventType(EventType.SUB_AGENT_MESSAGE)
                    .eventSource(agentId)
                    .eventSourceType("SUB_AGENT")
                    .artifact(Artifact.builder()
                            .index(index[0]++)
                            .metadata(metadata)
                            .parts(convertA2AParts(chunk))
                            .build())
                    .timestamp(LocalDateTime.now())
                    .build());
        });

        return Map.of("status", "streaming", "agentId", agentId);
    }

    /**
     * Get all registered sub-agents as tool definitions for the main agent.
     * Sub-agents are abstracted as special tools in the main agent's intent recognition.
     */
    public List<Map<String, Object>> getSubAgentAsTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map.Entry<String, SubAgentProxy> entry : subAgents.entrySet()) {
            AgentCard card = entry.getValue().getAgentCard();
            tools.add(Map.of(
                    "type", "sub_agent",
                    "name", entry.getKey(),
                    "description", card.getDescription() != null ? card.getDescription() : "",
                    "skills", card.getSkills() != null ? card.getSkills() : List.of(),
                    "inputSchema", card.getInputSchema() != null ? card.getInputSchema() : Map.of()
            ));
        }
        return tools;
    }

    /**
     * Get registered sub-agent count.
     */
    public int getSubAgentCount() {
        return subAgents.size();
    }

    /**
     * Convert A2A message parts to Artifact parts.
     */
    private List<Artifact.ArtifactPart> convertA2AParts(A2AMessage message) {
        if (message.getContent() == null || message.getContent().getParts() == null) {
            return List.of();
        }

        return message.getContent().getParts().stream()
                .map(part -> Artifact.ArtifactPart.builder()
                        .type(part.getType())
                        .data(part.getText() != null ? part.getText() : part.getData())
                        .mimeType(part.getMimeType())
                        .build())
                .toList();
    }
}
