package com.agent.core.mcp;

import com.agent.common.exception.AgentException;
import com.agent.model.mcp.McpServer;
import com.agent.model.mcp.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP manager implementation.
 * Manages MCP server connections and tool invocations.
 *
 * Each MCP server exposes a set of tools that the agent can use.
 * The manager maintains MCP clients and routes tool calls to the appropriate server.
 */
@Slf4j
@Service
public class McpManagerImpl implements McpManager {

    /** serverId -> McpServer */
    private final Map<String, McpServer> servers = new ConcurrentHashMap<>();

    /** toolName -> McpClient */
    private final Map<String, McpClient> toolClients = new ConcurrentHashMap<>();

    /** serverId -> McpClient */
    private final Map<String, McpClient> serverClients = new ConcurrentHashMap<>();

    @Override
    public void registerServer(String serverId, String name, String endpoint,
                                String transportType, Map<String, String> authConfig) {
        McpServer server = McpServer.builder()
                .serverId(serverId)
                .name(name)
                .endpoint(endpoint)
                .transportType(transportType)
                .authConfig(McpServer.AuthConfig.builder()
                        .type(authConfig != null ? authConfig.getOrDefault("type", "none") : "none")
                        .token(authConfig != null ? authConfig.get("token") : null)
                        .build())
                .tools(new ArrayList<>())
                .status("CONNECTING")
                .build();

        servers.put(serverId, server);

        // Create MCP client
        McpClient client = new McpClient(server);
        serverClients.put(serverId, client);

        try {
            // Connect and discover tools
            client.connect();
            List<McpTool> tools = client.listTools();
            server.setTools(tools);
            server.setStatus("CONNECTED");

            // Register tool -> client mapping
            for (McpTool tool : tools) {
                tool.setServerId(serverId);
                toolClients.put(tool.getName(), client);
            }

            log.info("MCP server registered: serverId={}, name={}, tools={}",
                    serverId, name, tools.size());

        } catch (Exception e) {
            server.setStatus("ERROR");
            log.error("Failed to connect MCP server: serverId={}, endpoint={}", serverId, endpoint, e);
        }
    }

    @Override
    public void unregisterServer(String serverId) {
        McpServer server = servers.remove(serverId);
        if (server != null) {
            // Remove tool mappings
            for (McpTool tool : server.getTools()) {
                toolClients.remove(tool.getName());
            }

            // Close client
            McpClient client = serverClients.remove(serverId);
            if (client != null) {
                client.close();
            }

            log.info("MCP server unregistered: serverId={}", serverId);
        }
    }

    @Override
    public List<String> getAvailableToolNames() {
        return servers.values().stream()
                .filter(s -> "CONNECTED".equals(s.getStatus()))
                .flatMap(s -> s.getTools().stream())
                .map(McpTool::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getToolDefinitions() {
        return servers.values().stream()
                .filter(s -> "CONNECTED".equals(s.getStatus()))
                .flatMap(s -> s.getTools().stream())
                .map(McpTool::toFunctionDefinition)
                .collect(Collectors.toList());
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> params) {
        McpClient client = toolClients.get(toolName);
        if (client == null) {
            throw new AgentException("MCP_TOOL_NOT_FOUND", "MCP tool not found: " + toolName);
        }

        log.info("Calling MCP tool: name={}, params={}", toolName, params);

        try {
            return client.callTool(toolName, params);
        } catch (Exception e) {
            log.error("MCP tool call failed: name={}", toolName, e);
            throw new AgentException("MCP_CALL_FAILED", "MCP tool call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void refreshTools() {
        for (Map.Entry<String, McpClient> entry : serverClients.entrySet()) {
            String serverId = entry.getKey();
            McpClient client = entry.getValue();
            McpServer server = servers.get(serverId);

            if (server != null) {
                try {
                    List<McpTool> tools = client.listTools();
                    // Remove old tool mappings
                    for (McpTool oldTool : server.getTools()) {
                        toolClients.remove(oldTool.getName());
                    }
                    // Set new tools
                    server.setTools(tools);
                    for (McpTool tool : tools) {
                        tool.setServerId(serverId);
                        toolClients.put(tool.getName(), client);
                    }
                    log.info("Refreshed tools for server: serverId={}, tools={}", serverId, tools.size());
                } catch (Exception e) {
                    log.error("Failed to refresh tools for server: serverId={}", serverId, e);
                }
            }
        }
    }
}
