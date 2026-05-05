package com.agent.core.multiagent.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A2A Protocol message model.
 * Used for communication between main agent and sub-agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2AMessage {

    /** Message ID */
    private String id;

    /** Task ID */
    private String taskId;

    /** Message type: task, task-update, task-result */
    private String type;

    /** Message content */
    private A2AContent content;

    /** Metadata for extensions */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A2AContent {
        /** Role: user, agent */
        private String role;
        /** Parts of the content */
        private java.util.List<A2APart> parts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A2APart {
        /** Part type: text, data, file */
        private String type;
        /** Text content */
        private String text;
        /** Data content */
        private Object data;
        /** MIME type */
        private String mimeType;
    }
}
