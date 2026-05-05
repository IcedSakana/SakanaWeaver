package com.agent.core.multiagent;

import com.agent.core.multiagent.a2a.A2AClient;
import com.agent.core.multiagent.a2a.AgentCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Sub-agent proxy.
 * Wraps a sub-agent's A2A client and agent card for the AgentHub.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentProxy {

    /** Unique agent identifier */
    private String agentId;

    /** Agent card (capabilities definition) */
    private AgentCard agentCard;

    /** A2A client for communication */
    private A2AClient client;

    /** Registration time */
    private LocalDateTime registeredAt;
}
