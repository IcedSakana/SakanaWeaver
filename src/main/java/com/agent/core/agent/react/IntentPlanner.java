package com.agent.core.agent.react;

import com.agent.core.agent.BaseAct;
import com.agent.model.task.Task;
import com.agent.model.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Intent planner act.
 * Performs intent recognition on user input and generates execution plans.
 *
 * In a real implementation, this would call an LLM for intent classification
 * and plan generation. This skeleton provides the structure for integration.
 */
@Slf4j
public class IntentPlanner extends BaseAct {

    /** History of recognized intents for context */
    private List<String> intentHistory;

    /** LLM endpoint for intent recognition (to be configured) */
    private String llmEndpoint;

    public IntentPlanner() {
        super("IntentPlanner");
        this.intentHistory = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        if (!shouldContinue()) return null;

        Map<String, Object> preprocessedInput = (Map<String, Object>) input;
        String normalizedInput = (String) preprocessedInput.get("normalizedInput");

        log.info("Planning intent for input: {}", normalizedInput);

        // Step 1: Intent recognition via LLM
        String intent = recognizeIntent(normalizedInput);

        // Step 2: Generate execution plan
        Task task = generatePlan(intent, normalizedInput);

        // Track intent history
        intentHistory.add(intent);

        return task;
    }

    /**
     * Recognize user intent using LLM.
     * TODO: Replace with actual LLM call.
     *
     * @param input normalized user input
     * @return recognized intent
     */
    private String recognizeIntent(String input) {
        // Placeholder: In production, this calls the configured LLM
        // with the system prompt containing available tools and sub-agents
        // to classify the user's intent.

        // Example intents: QUERY, OPERATE, CHAT, KNOWLEDGE_QA
        log.debug("Recognizing intent for: {}", input);
        return "CHAT"; // Default to chat intent
    }

    /**
     * Generate an execution plan based on recognized intent.
     * TODO: Replace with actual LLM-based planning.
     *
     * @param intent recognized intent
     * @param input  user input
     * @return task with execution plan
     */
    private Task generatePlan(String intent, String input) {
        Task task = Task.builder()
                .taskId(UUID.randomUUID().toString())
                .name("Task for: " + input.substring(0, Math.min(input.length(), 50)))
                .status(TaskStatus.PENDING)
                .intent(intent)
                .steps(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .metadata(new HashMap<>())
                .build();

        // Generate steps based on intent
        // In production, the LLM would generate specific MCP tool calls,
        // sub-agent invocations, or direct responses
        switch (intent) {
            case "OPERATE":
                // Plan includes MCP tool calls
                task.getSteps().add(Task.TaskStep.builder()
                        .index(0)
                        .description("Execute MCP tool call")
                        .type("MCP_CALL")
                        .status(TaskStatus.PENDING)
                        .build());
                break;
            case "KNOWLEDGE_QA":
                // Plan includes knowledge retrieval
                task.getSteps().add(Task.TaskStep.builder()
                        .index(0)
                        .description("Retrieve knowledge")
                        .type("LLM_CALL")
                        .status(TaskStatus.PENDING)
                        .build());
                break;
            case "SUB_AGENT":
                // Plan includes sub-agent invocation
                task.getSteps().add(Task.TaskStep.builder()
                        .index(0)
                        .description("Invoke sub-agent")
                        .type("SUB_AGENT_CALL")
                        .status(TaskStatus.PENDING)
                        .build());
                break;
            default:
                // Direct LLM response
                task.getSteps().add(Task.TaskStep.builder()
                        .index(0)
                        .description("Generate response")
                        .type("LLM_CALL")
                        .status(TaskStatus.PENDING)
                        .build());
                break;
        }

        return task;
    }

    public void setLlmEndpoint(String llmEndpoint) {
        this.llmEndpoint = llmEndpoint;
    }

    @Override
    public Map<String, Object> dumpContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("intentHistory", intentHistory);
        ctx.put("llmEndpoint", llmEndpoint);
        return ctx;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadContext(Map<String, Object> context) {
        if (context != null) {
            if (context.containsKey("intentHistory")) {
                this.intentHistory = (List<String>) context.get("intentHistory");
            }
            if (context.containsKey("llmEndpoint")) {
                this.llmEndpoint = (String) context.get("llmEndpoint");
            }
        }
    }
}
