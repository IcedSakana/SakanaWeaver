package com.agent.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Server configuration model.
 * Represents a registered MCP Server with its tools.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServer {

    /** Unique server ID */
    private String serverId;

    /** Server name */
    private String name;

    /** Server description */
    private String description;

    /** Server endpoint URL */
    private String endpoint;

    /** Transport type: stdio, sse, streamable-http */
    private String transportType;

    /** Authentication configuration */
    private AuthConfig authConfig;

    /** Available tools from this server */
    private List<McpTool> tools;

    /** Server status: CONNECTED, DISCONNECTED, ERROR */
    private String status;

    /** Additional server metadata */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthConfig {
        /** Auth type: none, bearer, oauth2 */
        private String type;
        /** Auth token or credentials */
        private String token;
        /** Additional auth parameters */
        private Map<String, String> params;
    }
}
