package com.agent.core.agent.context;

import com.agent.core.agent.segment.Segment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level assembler that combines a {@link SystemPromptBuilder} and a
 * {@link UserPromptBuilder} to produce ready-to-use prompt pairs for different
 * agent stages (intent planning, task execution, expression generation).
 */
@NoArgsConstructor
public class PromptAssembler {

    private SystemPromptBuilder systemBuilder;
    private UserPromptBuilder userBuilder;

    public PromptAssembler(SystemPromptBuilder systemBuilder, UserPromptBuilder userBuilder) {
        this.systemBuilder = systemBuilder;
        this.userBuilder = userBuilder;
    }

    /**
     * Set the system prompt builder.
     */
    public PromptAssembler systemBuilder(SystemPromptBuilder systemBuilder) {
        this.systemBuilder = systemBuilder;
        return this;
    }

    /**
     * Set the user prompt builder.
     */
    public PromptAssembler userBuilder(UserPromptBuilder userBuilder) {
        this.userBuilder = userBuilder;
        return this;
    }

    /**
     * Assemble prompts for the <em>Intent Planner</em> stage.
     * <p>
     * The Intent Planner analyses the user's request and decides which tasks/tools
     * to invoke.  The system prompt emphasises planning and reasoning; the user prompt
     * carries the raw user segments plus any injected knowledge.
     *
     * @param roleDefinition   how the planner agent should behave
     * @param availableTools   tool stubs available to the planner
     * @param principles       rules / constraints
     * @param userSegments     the user's input segments
     * @param knowledgeContext optional retrieved knowledge
     * @return a {@link PromptPair} ready for the LLM call
     */
    public PromptPair assembleForIntentPlanner(String roleDefinition,
                                               List<String> availableTools,
                                               List<String> principles,
                                               List<Segment> userSegments,
                                               String knowledgeContext) {
        String systemPrompt = new SystemPromptBuilder()
                .roleDefinition(roleDefinition)
                .workMechanism("You are an intent planner. Analyse the user's request and determine "
                        + "the appropriate tasks and tools to invoke. Output a structured plan.")
                .availableTools(availableTools)
                .principles(principles)
                .outputFormat("Respond with a Python code block containing the execution plan.")
                .build();

        String userPrompt = new UserPromptBuilder()
                .fromSegments(userSegments)
                .withKnowledgeContext(knowledgeContext)
                .build();

        return new PromptPair(systemPrompt, userPrompt);
    }

    /**
     * Assemble prompts for the <em>Task Executor</em> stage.
     * <p>
     * The Task Executor carries out the concrete tool invocations planned by the
     * Intent Planner.  The system prompt emphasises precise tool usage; the user
     * prompt carries the plan segments plus history.
     *
     * @param roleDefinition      how the executor should behave
     * @param availableTools      tool stubs
     * @param principles          rules / constraints
     * @param taskSegments        segments describing the task to execute
     * @param historySegments     previous conversation turns
     * @param experienceContext   optional experience context
     * @return a {@link PromptPair} ready for the LLM call
     */
    public PromptPair assembleForTaskExecutor(String roleDefinition,
                                              List<String> availableTools,
                                              List<String> principles,
                                              List<Segment> taskSegments,
                                              List<Segment> historySegments,
                                              String experienceContext) {
        String systemPrompt = new SystemPromptBuilder()
                .roleDefinition(roleDefinition)
                .workMechanism("You are a task executor. Execute the given task by invoking the "
                        + "appropriate tools with the correct parameters. Follow the plan precisely.")
                .availableTools(availableTools)
                .principles(principles)
                .outputFormat("Respond with a Python code block containing the tool invocation code.")
                .build();

        String userPrompt = new UserPromptBuilder()
                .fromSegments(taskSegments)
                .withHistorySegments(historySegments)
                .withExperienceContext(experienceContext)
                .build();

        return new PromptPair(systemPrompt, userPrompt);
    }

    /**
     * Assemble prompts for the <em>Expression</em> stage.
     * <p>
     * The Expression stage converts raw tool results into a natural-language or
     * formatted response for the end user.
     *
     * @param roleDefinition   how the expression agent should behave
     * @param principles       rules / constraints
     * @param resultSegments   segments containing tool execution results
     * @param knowledgeContext optional knowledge context
     * @return a {@link PromptPair} ready for the LLM call
     */
    public PromptPair assembleForExpression(String roleDefinition,
                                            List<String> principles,
                                            List<Segment> resultSegments,
                                            String knowledgeContext) {
        String systemPrompt = new SystemPromptBuilder()
                .roleDefinition(roleDefinition)
                .workMechanism("You are an expression agent. Transform the tool execution results "
                        + "into a clear, well-structured response for the user.")
                .principles(principles)
                .outputFormat("Respond in natural language. Use markdown formatting when appropriate.")
                .build();

        String userPrompt = new UserPromptBuilder()
                .fromSegments(resultSegments)
                .withKnowledgeContext(knowledgeContext)
                .build();

        return new PromptPair(systemPrompt, userPrompt);
    }

    // ---- inner class ----

    /**
     * Immutable pair carrying both the system prompt and the user prompt.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PromptPair {
        private String systemPrompt;
        private String userPrompt;
    }
}
