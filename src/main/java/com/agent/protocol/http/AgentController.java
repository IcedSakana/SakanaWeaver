package com.agent.protocol.http;

import com.agent.core.agent.AgentManager;
import com.agent.core.mcp.McpManager;
import com.agent.core.multiagent.AgentHub;
import com.agent.core.multiagent.a2a.AgentCard;
import com.agent.model.agent.AgentInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent management REST controller.
 * Provides HTTP endpoints for agent, MCP, and Multi-Agent management.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentManager agentManager;
    private final McpManager mcpManager;
    private final AgentHub agentHub;

    public AgentController(AgentManager agentManager, McpManager mcpManager, AgentHub agentHub) {
        this.agentManager = agentManager;
        this.mcpManager = mcpManager;
        this.agentHub = agentHub;
    }

    // ==================== Agent Instance APIs ====================

    /**
     * Get agent instance info for a session.
     * GET /api/agent/instances/{sessionId}
     */
    @GetMapping("/instances/{sessionId}")
    public ResponseEntity<AgentInstance> getAgent(@PathVariable String sessionId) {
        AgentInstance instance = agentManager.getAgent(sessionId);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(instance);
    }

    /**
     * Get active agent count on this node.
     * GET /api/agent/instances/count
     */
    @GetMapping("/instances/count")
    public ResponseEntity<Map<String, Object>> getAgentCount() {
        return ResponseEntity.ok(Map.of("count", agentManager.getActiveAgentCount()));
    }

    // ==================== MCP Management APIs ====================

    /**
     * Register an MCP server.
     * POST /api/agent/mcp/servers
     */
    @PostMapping("/mcp/servers")
    public ResponseEntity<Map<String, String>> registerMcpServer(@RequestBody Map<String, Object> request) {
        String serverId = (String) request.get("serverId");
        String name = (String) request.get("name");
        String endpoint = (String) request.get("endpoint");
        String transportType = (String) request.getOrDefault("transportType", "streamable-http");
        @SuppressWarnings("unchecked")
        Map<String, String> authConfig = (Map<String, String>) request.get("authConfig");

        mcpManager.registerServer(serverId, name, endpoint, transportType, authConfig);
        return ResponseEntity.ok(Map.of("status", "registered", "serverId", serverId));
    }

    /**
     * Unregister an MCP server.
     * DELETE /api/agent/mcp/servers/{serverId}
     */
    @DeleteMapping("/mcp/servers/{serverId}")
    public ResponseEntity<Void> unregisterMcpServer(@PathVariable String serverId) {
        mcpManager.unregisterServer(serverId);
        return ResponseEntity.ok().build();
    }

    /**
     * List all available MCP tools.
     * GET /api/agent/mcp/tools
     */
    @GetMapping("/mcp/tools")
    public ResponseEntity<List<Map<String, Object>>> getMcpTools() {
        return ResponseEntity.ok(mcpManager.getToolDefinitions());
    }

    /**
     * Refresh MCP tools from all connected servers.
     * POST /api/agent/mcp/tools/refresh
     */
    @PostMapping("/mcp/tools/refresh")
    public ResponseEntity<Void> refreshMcpTools() {
        mcpManager.refreshTools();
        return ResponseEntity.ok().build();
    }

    // ==================== Multi-Agent APIs ====================

    /**
     * Register a sub-agent.
     * POST /api/agent/sub-agents
     */
    @PostMapping("/sub-agents")
    public ResponseEntity<Map<String, String>> registerSubAgent(@RequestBody Map<String, Object> request) {
        String agentId = (String) request.get("agentId");
        String baseUrl = (String) request.get("baseUrl");

        if (request.containsKey("agentCard")) {
            // Register with provided agent card
            @SuppressWarnings("unchecked")
            Map<String, Object> cardMap = (Map<String, Object>) request.get("agentCard");
            AgentCard card = com.agent.common.utils.JsonUtils.fromMap(cardMap, AgentCard.class);
            agentHub.registerSubAgent(agentId, card);
        } else {
            // Discover agent card from well-known endpoint
            agentHub.registerSubAgent(agentId, baseUrl);
        }

        return ResponseEntity.ok(Map.of("status", "registered", "agentId", agentId));
    }

    /**
     * Unregister a sub-agent.
     * DELETE /api/agent/sub-agents/{agentId}
     */
    @DeleteMapping("/sub-agents/{agentId}")
    public ResponseEntity<Void> unregisterSubAgent(@PathVariable String agentId) {
        agentHub.unregisterSubAgent(agentId);
        return ResponseEntity.ok().build();
    }

    /**
     * List all registered sub-agents as tools.
     * GET /api/agent/sub-agents
     */
    @GetMapping("/sub-agents")
    public ResponseEntity<List<Map<String, Object>>> getSubAgents() {
        return ResponseEntity.ok(agentHub.getSubAgentAsTools());
    }

    // ==================== Health Check ====================

    /**
     * Health check endpoint.
     * GET /api/agent/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "activeAgents", agentManager.getActiveAgentCount(),
                "registeredSubAgents", agentHub.getSubAgentCount(),
                "availableTools", mcpManager.getAvailableToolNames().size()
        ));
    }
}
