package com.agent.model.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Task model.
 * Each plan recognized by intent recognition is mapped to a Task.
 * Tasks are managed within a session for persistence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    /** Unique task ID */
    private String taskId;

    /** Session ID */
    private String sessionId;

    /** Task name/description */
    private String name;

    /** Current status */
    private TaskStatus status;

    /** Intent recognized from user input */
    private String intent;

    /** Planned steps for execution */
    private List<TaskStep> steps;

    /** Task result */
    private Object result;

    /** Error message if failed */
    private String errorMessage;

    /** Creation time */
    private LocalDateTime createdAt;

    /** Completion time */
    private LocalDateTime completedAt;

    /** Task metadata */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStep {

        /** Step index */
        private int index;

        /** Step description */
        private String description;

        /** Step type: MCP_CALL, LLM_CALL, SUB_AGENT_CALL */
        private String type;

        /** Step status */
        private TaskStatus status;

        /** Step result */
        private Object result;

        /** Tool/agent to invoke */
        private String target;

        /** Input parameters */
        private Map<String, Object> input;
    }
}
