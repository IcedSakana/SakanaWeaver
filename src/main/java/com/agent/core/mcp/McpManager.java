package com.agent.core.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) manager interface.
 * Manages all connected MCP Servers and provides tool invocation.
 */
public interface McpManager {

    /**
     * Register an MCP server.
     *
     * @param serverId      unique server identifier
     * @param name          server name
     * @param endpoint      server endpoint URL
     * @param transportType transport type (stdio, sse, streamable-http)
     * @param authConfig    authentication config
     */
    void registerServer(String serverId, String name, String endpoint,
                        String transportType, Map<String, String> authConfig);

    /**
     * Unregister an MCP server.
     *
     * @param serverId server identifier
     */
    void unregisterServer(String serverId);

    /**
     * Get all available tool names.
     *
     * @return list of tool names
     */
    List<String> getAvailableToolNames();

    /**
     * Get tool definitions for LLM function calling.
     *
     * @return list of tool definitions in LLM-compatible format
     */
    List<Map<String, Object>> getToolDefinitions();

    /**
     * Call an MCP tool.
     *
     * @param toolName tool name
     * @param params   tool parameters
     * @return tool execution result
     */
    Object callTool(String toolName, Map<String, Object> params);

    /**
     * Refresh tools from all connected servers.
     */
    void refreshTools();
}
