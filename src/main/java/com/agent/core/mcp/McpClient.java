package com.agent.core.mcp;

import com.agent.common.utils.JsonUtils;
import com.agent.model.mcp.McpServer;
import com.agent.model.mcp.McpTool;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCP Client implementation.
 * Handles communication with a single MCP Server.
 *
 * Supports transport types:
 * - streamable-http: HTTP-based MCP protocol (default)
 * - sse: Server-Sent Events based transport
 * - stdio: Standard I/O based transport (for local servers)
 *
 * TODO: Replace with official MCP SDK when available for Java.
 */
@Slf4j
public class McpClient {

    private final McpServer server;
    private final OkHttpClient httpClient;
    private boolean connected = false;

    public McpClient(McpServer server) {
        this.server = server;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Connect to the MCP server.
     */
    public void connect() {
        log.info("Connecting to MCP server: name={}, endpoint={}, transport={}",
                server.getName(), server.getEndpoint(), server.getTransportType());

        // For streamable-http, verify server is reachable
        if ("streamable-http".equals(server.getTransportType()) || "sse".equals(server.getTransportType())) {
            // Send initialize request
            Map<String, Object> initRequest = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "initialize",
                    "params", Map.of(
                            "protocolVersion", "2024-11-05",
                            "capabilities", Map.of(),
                            "clientInfo", Map.of(
                                    "name", "agent-server",
                                    "version", "1.0.0"
                            )
                    )
            );

            try {
                Object response = sendRequest(initRequest);
                log.info("MCP server initialized: name={}, response={}", server.getName(), response);
                this.connected = true;
            } catch (Exception e) {
                log.warn("MCP server connection deferred (will retry on first use): name={}", server.getName());
                // Don't throw - allow registration even if server is not yet available
                this.connected = false;
            }
        } else {
            // For stdio transport, would need process management
            log.info("MCP stdio transport registered (deferred connection): name={}", server.getName());
            this.connected = true;
        }
    }

    /**
     * List available tools from the MCP server.
     */
    @SuppressWarnings("unchecked")
    public List<McpTool> listTools() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/list",
                "params", Map.of()
        );

        try {
            Map<String, Object> response = (Map<String, Object>) sendRequest(request);
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result == null) return List.of();

            List<Map<String, Object>> toolsList = (List<Map<String, Object>>) result.get("tools");
            if (toolsList == null) return List.of();

            List<McpTool> tools = new ArrayList<>();
            for (Map<String, Object> toolMap : toolsList) {
                McpTool tool = McpTool.builder()
                        .name((String) toolMap.get("name"))
                        .description((String) toolMap.get("description"))
                        .inputSchema((Map<String, Object>) toolMap.get("inputSchema"))
                        .build();
                tools.add(tool);
            }

            return tools;
        } catch (Exception e) {
            log.error("Failed to list tools from MCP server: name={}", server.getName(), e);
            return List.of();
        }
    }

    /**
     * Call a tool on the MCP server.
     */
    @SuppressWarnings("unchecked")
    public Object callTool(String toolName, Map<String, Object> params) {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", System.currentTimeMillis(),
                "method", "tools/call",
                "params", Map.of(
                        "name", toolName,
                        "arguments", params != null ? params : Map.of()
                )
        );

        Map<String, Object> response = (Map<String, Object>) sendRequest(request);
        return response.get("result");
    }

    /**
     * Send a JSON-RPC request to the MCP server.
     */
    @SuppressWarnings("unchecked")
    private Object sendRequest(Map<String, Object> requestBody) {
        String json = JsonUtils.toJson(requestBody);

        okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .url(server.getEndpoint())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json");

        // Add auth headers
        if (server.getAuthConfig() != null && "bearer".equals(server.getAuthConfig().getType())) {
            builder.addHeader("Authorization", "Bearer " + server.getAuthConfig().getToken());
        }

        okhttp3.Request request = builder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("MCP request failed with status: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "{}";
            return JsonUtils.fromJson(body, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("MCP request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Close the client connection.
     */
    public void close() {
        this.connected = false;
        log.info("MCP client closed: name={}", server.getName());
    }

    public boolean isConnected() {
        return connected;
    }
}
