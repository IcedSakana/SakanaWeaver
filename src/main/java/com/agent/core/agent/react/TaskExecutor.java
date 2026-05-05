package com.agent.core.agent.react;

import com.agent.core.agent.BaseAct;
import com.agent.core.event.EventCenter;
import com.agent.core.mcp.McpManager;
import com.agent.model.event.Event;
import com.agent.model.event.EventType;
import com.agent.model.task.Task;
import com.agent.model.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Task executor act.
 * Executes planned tasks including:
 * - MCP tool calls
 * - LLM calls for response generation
 * - Sub-agent invocations
 *
 * Follows ReAct (Reasoning + Acting) pattern:
 * Think -> Act -> Observe -> Think -> ...
 */
@Slf4j
public class TaskExecutor extends BaseAct {

    private final String sessionId;
    private final EventCenter eventCenter;
    private final McpManager mcpManager;

    /** Current execution state for dump/load */
    private String currentTaskId;
    private int currentStepIndex;

    public TaskExecutor(String sessionId, EventCenter eventCenter, McpManager mcpManager) {
        super("TaskExecutor");
        this.sessionId = sessionId;
        this.eventCenter = eventCenter;
        this.mcpManager = mcpManager;
    }

    @Override
    public Object execute(Object input) {
        if (!shouldContinue()) return null;

        Task task = (Task) input;
        this.currentTaskId = task.getTaskId();

        log.info("Executing task: taskId={}, intent={}, steps={}",
                task.getTaskId(), task.getIntent(), task.getSteps().size());

        task.setStatus(TaskStatus.RUNNING);

        // Emit execution progress
        eventCenter.publishOutput(sessionId, Event.progressEvent(
                sessionId, task.getTaskId(), "TaskExecutor", "RUNNING", "Starting task execution"));

        try {
            // Execute each step in the plan
            for (int i = 0; i < task.getSteps().size(); i++) {
                if (!shouldContinue()) {
                    log.info("Task execution interrupted by signal: taskId={}", task.getTaskId());
                    break;
                }

                this.currentStepIndex = i;
                Task.TaskStep step = task.getSteps().get(i);
                step.setStatus(TaskStatus.RUNNING);

                // Emit step progress
                eventCenter.publishOutput(sessionId, Event.progressEvent(
                        sessionId, task.getTaskId(), "TaskExecutor", "STEP_" + i,
                        "Executing: " + step.getDescription()));

                try {
                    Object stepResult = executeStep(step, task);
                    step.setResult(stepResult);
                    step.setStatus(TaskStatus.COMPLETED);
                } catch (Exception e) {
                    log.error("Step execution failed: taskId={}, step={}", task.getTaskId(), i, e);
                    step.setStatus(TaskStatus.FAILED);
                    task.setStatus(TaskStatus.FAILED);
                    task.setErrorMessage(e.getMessage());

                    // Send error event to frontend
                    eventCenter.publishOutput(sessionId, Event.errorEvent(sessionId,
                            "Task execution failed: " + e.getMessage()));
                    return task;
                }
            }

            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(java.time.LocalDateTime.now());

            log.info("Task completed: taskId={}", task.getTaskId());

        } catch (Exception e) {
            log.error("Task execution error: taskId={}", task.getTaskId(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
        }

        return task;
    }

    /**
     * Execute a single step.
     */
    private Object executeStep(Task.TaskStep step, Task task) {
        return switch (step.getType()) {
            case "MCP_CALL" -> executeMcpCall(step);
            case "LLM_CALL" -> executeLlmCall(step, task);
            case "SUB_AGENT_CALL" -> executeSubAgentCall(step);
            default -> {
                log.warn("Unknown step type: {}", step.getType());
                yield null;
            }
        };
    }

    /**
     * Execute MCP tool call.
     * TODO: Integrate with actual MCP client.
     */
    private Object executeMcpCall(Task.TaskStep step) {
        String toolName = step.getTarget();
        Map<String, Object> params = step.getInput();

        log.info("Executing MCP call: tool={}, params={}", toolName, params);

        // Call MCP tool via McpManager
        Object result = mcpManager.callTool(toolName, params);

        // Send tool result event
        eventCenter.publishOutput(sessionId, Event.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .sessionId(sessionId)
                .eventType(EventType.MCP_TOOL_RESULT)
                .eventSource("TaskExecutor")
                .taskId(currentTaskId)
                .artifact(com.agent.model.event.Artifact.builder()
                        .metadata(Map.of("toolName", toolName != null ? toolName : ""))
                        .parts(java.util.List.of(
                                com.agent.model.event.Artifact.ArtifactPart.builder()
                                        .type("data")
                                        .data(result)
                                        .build()))
                        .build())
                .timestamp(java.time.LocalDateTime.now())
                .build());

        return result;
    }

    /**
     * Execute LLM call for response generation.
     * TODO: Integrate with actual LLM endpoint.
     */
    private Object executeLlmCall(Task.TaskStep step, Task task) {
        log.info("Executing LLM call for task: {}", task.getTaskId());

        // Placeholder: In production, this sends the conversation context
        // to the LLM and streams the response back via EventCenter.

        // Simulate streaming response
        String response = "This is a placeholder response. Please configure the LLM endpoint.";

        // Send streaming chunks
        String[] chunks = response.split("(?<=\\s)");
        for (int i = 0; i < chunks.length; i++) {
            if (!shouldContinue()) break;

            eventCenter.publishOutput(sessionId, Event.streamChunkEvent(
                    sessionId, task.getTaskId(), "Agent", chunks[i], i));

            try {
                Thread.sleep(50); // Simulate streaming delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return response;
    }

    /**
     * Execute sub-agent call via A2A protocol.
     * TODO: Integrate with AgentHub.
     */
    private Object executeSubAgentCall(Task.TaskStep step) {
        String subAgentId = step.getTarget();
        log.info("Executing sub-agent call: agent={}", subAgentId);

        // Placeholder: In production, this calls the sub-agent via AgentHub
        // using A2A protocol and streams the response back.

        return Map.of("status", "placeholder", "message", "Sub-agent call not yet configured");
    }

    @Override
    public Map<String, Object> dumpContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("currentTaskId", currentTaskId);
        ctx.put("currentStepIndex", currentStepIndex);
        return ctx;
    }

    @Override
    public void loadContext(Map<String, Object> context) {
        if (context != null) {
            this.currentTaskId = (String) context.get("currentTaskId");
            Object stepIdx = context.get("currentStepIndex");
            this.currentStepIndex = stepIdx != null ? (int) stepIdx : 0;
        }
    }
}
