package com.agent.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP Tool definition.
 * Represents a single tool exposed by an MCP Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpTool {

    /** Tool name */
    private String name;

    /** Tool description (used for LLM function calling) */
    private String description;

    /** Input JSON Schema */
    private Map<String, Object> inputSchema;

    /** The MCP server this tool belongs to */
    private String serverId;

    /**
     * Convert to LLM function definition format.
     */
    public Map<String, Object> toFunctionDefinition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description != null ? description : "",
                        "parameters", inputSchema != null ? inputSchema : Map.of()
                )
        );
    }
}
