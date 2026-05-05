package com.agent.core.multiagent.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A2A Agent Card model.
 * Based on Google A2A Protocol (Agent-to-Agent Protocol).
 *
 * Agent cards are discovered at /.well-known/agent.json endpoint
 * and define the sub-agent's capabilities, input schema, and communication mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard {

    /** Agent name */
    private String name;

    /** Agent description */
    private String description;

    /** Agent endpoint URL */
    private String url;

    /** Agent version */
    private String version;

    /** Supported input modes: text, file, data */
    private List<String> inputModes;

    /** Supported output modes: text, text/event-stream, application/json */
    private List<String> outputModes;

    /** Skills this agent can perform */
    private List<Skill> skills;

    /** Tags for capabilities: async, streaming, etc. */
    private List<String> tags;

    /** Authentication requirements */
    private AuthRequirement authentication;

    /** Few-shot examples for the main agent */
    private List<FewShotExample> fewShotExamples;

    /** Input JSON Schema */
    private Map<String, Object> inputSchema;

    /**
     * Check if this agent supports async execution.
     */
    public boolean isAsync() {
        return tags != null && tags.contains("async");
    }

    /**
     * Check if this agent supports streaming output.
     */
    public boolean isStreaming() {
        return outputModes != null && outputModes.contains("text/event-stream");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skill {
        private String name;
        private String description;
        private List<String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthRequirement {
        private String type;
        private Map<String, String> params;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FewShotExample {
        private String userInput;
        private String agentOutput;
    }
}
