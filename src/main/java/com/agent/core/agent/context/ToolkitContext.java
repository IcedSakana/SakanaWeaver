package com.agent.core.agent.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Execution environment context that carries session metadata and runtime variables
 * throughout the agent's processing lifecycle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolkitContext {

    /**
     * Unique identifier for the current session.
     */
    private String sessionId;

    /**
     * Trace identifier for distributed tracing / observability.
     */
    private String traceId;

    /**
     * Tenant identifier for multi-tenant isolation.
     */
    private String tenantId;

    /**
     * Runtime variable storage – allows tools and agents to share transient state.
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * Static environment information (OS, region, deployment metadata, etc.).
     */
    @Builder.Default
    private Map<String, Object> environmentInfo = new HashMap<>();

    /**
     * Store a runtime variable.
     *
     * @param key   variable name
     * @param value variable value
     */
    public void setVariable(String key, Object value) {
        if (variables == null) {
            variables = new HashMap<>();
        }
        variables.put(key, value);
    }

    /**
     * Retrieve a runtime variable.
     *
     * @param key variable name
     * @return the stored value, or {@code null} if absent
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        if (variables == null) {
            return null;
        }
        return (T) variables.get(key);
    }

    /**
     * Flatten the entire context into a single map suitable for template rendering
     * or serialization.
     *
     * @return merged map of all context fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", sessionId);
        map.put("traceId", traceId);
        map.put("tenantId", tenantId);
        if (variables != null) {
            map.put("variables", variables);
        }
        if (environmentInfo != null) {
            map.put("environmentInfo", environmentInfo);
        }
        return map;
    }
}
