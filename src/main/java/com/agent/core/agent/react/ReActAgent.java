package com.agent.core.agent.react;

import com.agent.core.agent.area.CognitionArea;
import com.agent.core.agent.area.ExpressionArea;
import com.agent.core.agent.area.MotorArea;
import com.agent.core.agent.area.PerceptionArea;
import com.agent.core.agent.area.SelfEvaluationArea;
import com.agent.core.agent.code.CodeExecutionResult;
import com.agent.core.agent.code.CodeSandbox;
import com.agent.core.agent.memory.MemoryManager;
import com.agent.core.agent.memory.MemoryType;
import com.agent.core.agent.memory.SegmentMemoryMessage;
import com.agent.core.agent.memory.ShortTermMemory;
import com.agent.core.agent.segment.Segment;
import com.agent.core.agent.segment.SegmentBuilder;
import com.agent.core.agent.segment.SegmentContext;
import com.agent.core.event.EventCenter;
import com.agent.core.mcp.McpManager;
import com.agent.core.task.TaskManager;
import com.agent.model.agent.AgentContext;
import com.agent.model.agent.AgentInstance;
import com.agent.model.event.Event;
import com.agent.model.event.EventType;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ReAct Agent implementation integrated with the brain-inspired
 * Area / Segment / CodeExecution / Memory architecture.
 *
 * <p>The processing pipeline mirrors the flow of information through
 * cortical areas in a biological brain:</p>
 *
 * <pre>
 *   Event
 *     |
 *     v
 *   [PerceptionArea]  -- perceive and normalize input
 *     |
 *     v
 *   [CognitionArea]   -- intent recognition, plan generation, code generation
 *     |
 *     +--- simple task --------+--- complex task ---+
 *     |                        |                    |
 *     v                        v                    v
 *   (code execution)    [MotorArea]           [MotorArea]
 *     |                  (multi-round)         (multi-round)
 *     v                        |                    |
 *   [SelfEvaluationArea]  <----+--------------------+
 *     |
 *     +--- retry (max 2) ---> back to CognitionArea
 *     |
 *     v
 *   [ExpressionArea]  -- render output for user
 *     |
 *     v
 *   EventCenter.publishOutput(...)
 * </pre>
 *
 * <p>Throughout the pipeline, every significant step is recorded as a
 * {@link Segment} inside a {@link SegmentContext}, building a structured
 * Chain-of-Thought that can be inspected, replayed, and used for prompt
 * construction.</p>
 *
 * <p>A {@link MemoryManager} manages the Atkinson-Shiffrin three-layer memory
 * (sensory, short-term, long-term), and a {@link CodeSandbox} provides safe
 * execution of LLM-generated Python code.</p>
 *
 * <h3>Signal handling</h3>
 * <ul>
 *   <li><b>SleepSignal</b> -- complete current loop iteration, dump all area
 *       and segment contexts, then hibernate.</li>
 *   <li><b>StopSignal</b> -- stop execution immediately.</li>
 * </ul>
 *
 * @author agent-framework
 */
@Slf4j
public class ReActAgent {

    /** Maximum number of self-evaluation retries before giving up. */
    private static final int MAX_RETRIES = 2;

    // ------------------------------------------------------------------
    // Core dependencies
    // ------------------------------------------------------------------

    private final String sessionId;
    private final AgentInstance instance;
    private final EventCenter eventCenter;
    private final TaskManager taskManager;
    private final McpManager mcpManager;

    // ------------------------------------------------------------------
    // Brain-inspired areas
    // ------------------------------------------------------------------

    private final PerceptionArea perceptionArea;
    private final CognitionArea cognitionArea;
    private final MotorArea motorArea;
    private final ExpressionArea expressionArea;
    private final SelfEvaluationArea selfEvaluationArea;

    // ------------------------------------------------------------------
    // Segment, code execution, and memory
    // ------------------------------------------------------------------

    private final SegmentContext segmentContext;
    private final CodeSandbox codeSandbox;
    private final MemoryManager memoryManager;

    // ------------------------------------------------------------------
    // All areas for batch lifecycle operations
    // ------------------------------------------------------------------

    private final Map<String, com.agent.core.agent.area.BaseArea> areas = new LinkedHashMap<>();

    // ===================================================================
    // Constructor
    // ===================================================================

    public ReActAgent(String sessionId,
                      AgentInstance instance,
                      EventCenter eventCenter,
                      TaskManager taskManager,
                      McpManager mcpManager) {
        this.sessionId = sessionId;
        this.instance = instance;
        this.eventCenter = eventCenter;
        this.taskManager = taskManager;
        this.mcpManager = mcpManager;

        // Initialize the 5 Areas
        this.perceptionArea = new PerceptionArea();
        this.cognitionArea = new CognitionArea();
        this.motorArea = new MotorArea();
        this.expressionArea = new ExpressionArea();
        this.selfEvaluationArea = new SelfEvaluationArea();

        areas.put("PerceptionArea", perceptionArea);
        areas.put("CognitionArea", cognitionArea);
        areas.put("MotorArea", motorArea);
        areas.put("ExpressionArea", expressionArea);
        areas.put("SelfEvaluationArea", selfEvaluationArea);

        // Initialize Segment, Code, and Memory subsystems
        this.segmentContext = new SegmentContext(sessionId);
        this.codeSandbox = new CodeSandbox(sessionId);
        this.memoryManager = new MemoryManager();
    }

    // ===================================================================
    // Lifecycle
    // ===================================================================

    /**
     * Initialize the agent and all subsystems.
     *
     * <p>Starts the {@link CodeSandbox} so that it is ready to execute
     * LLM-generated Python code on first request.</p>
     */
    public void initialize() {
        log.info("ReActAgent initializing: sessionId={}", sessionId);

        try {
            codeSandbox.initialize();
            log.info("CodeSandbox initialized for sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("CodeSandbox initialization failed for sessionId={}. " +
                    "Code execution will be unavailable until manually initialized.", sessionId, e);
        }

        log.info("ReActAgent initialized: sessionId={}, areas={}", sessionId, areas.keySet());
    }

    /**
     * Restore agent from persisted context.
     *
     * <p>Restores each area's internal state from the serialized
     * {@link AgentContext#getActContexts()} map and rebuilds the
     * {@link SegmentContext} from the persisted segment timeline.</p>
     */
    @SuppressWarnings("unchecked")
    public void restore(AgentContext context) {
        Map<String, Object> actContexts = context.getActContexts();
        if (actContexts != null) {
            // Restore each area
            for (Map.Entry<String, com.agent.core.agent.area.BaseArea> entry : areas.entrySet()) {
                Object areaCtx = actContexts.get(entry.getKey());
                if (areaCtx instanceof Map) {
                    entry.getValue().loadContext((Map<String, Object>) areaCtx);
                    entry.getValue().resetSignals();
                }
            }

            // Restore SegmentContext
            Object segCtx = actContexts.get("SegmentContext");
            if (segCtx instanceof Map) {
                SegmentContext restored = SegmentContext.fromMap((Map<String, Object>) segCtx);
                segmentContext.setCurrentSegments(restored.getCurrentSegments());
                segmentContext.setHistorySegments(restored.getHistorySegments());
                segmentContext.setCurrentRound(restored.getCurrentRound());
            }
        }

        log.info("ReActAgent restored: sessionId={}, segmentCount={}",
                sessionId, segmentContext.getTotalSegmentCount());
    }

    // ===================================================================
    // Event handling
    // ===================================================================

    /**
     * Handle input event from EventCenter.
     *
     * <p>Only processes {@link EventType#USER_MESSAGE} events; all others
     * are silently ignored.</p>
     */
    public void onInputEvent(Event event) {
        if (event.getEventType() == EventType.USER_MESSAGE) {
            processInput(event);
        }
    }

    // ===================================================================
    // Core processing pipeline
    // ===================================================================

    /**
     * Process user input through the full ReAct pipeline.
     *
     * <h3>Flow</h3>
     * <ol>
     *   <li><b>Perception</b> -- perceive and normalize the raw event.</li>
     *   <li><b>Segment: USER_INPUT</b> -- record the input in the segment timeline.</li>
     *   <li><b>Cognition</b> -- plan intent, generate code, assess complexity.</li>
     *   <li><b>Segment: THOUGHT</b> -- record the plan description.</li>
     *   <li><b>Code Execution</b> (if code was generated) -- run in CodeSandbox,
     *       record CODE and CODE_RESULT segments.</li>
     *   <li><b>Motor</b> (if complex task) -- delegate to MotorArea for multi-round
     *       execution, record ROUND segments.</li>
     *   <li><b>Self-Evaluation</b> -- evaluate the result; retry up to
     *       {@value #MAX_RETRIES} times if evaluation fails.</li>
     *   <li><b>Expression</b> -- render the result for the user.</li>
     *   <li><b>Segment: EXPRESS</b> -- record the final output.</li>
     *   <li><b>Publish</b> -- send the output event via EventCenter.</li>
     * </ol>
     */
    public void processInput(Event event) {
        String userInput = extractTextFromEvent(event);
        if (userInput == null || userInput.isEmpty()) {
            log.warn("Empty user input, skipping: sessionId={}", sessionId);
            return;
        }

        log.info("Processing input: sessionId={}, input={}",
                sessionId, truncate(userInput, 100));

        // Record user input in short-term memory
        ShortTermMemory stm = memoryManager.getShortTermMemory(sessionId);
        stm.addSegment(SegmentMemoryMessage.builder()
                .memoryType(MemoryType.SHORT_TERM)
                .sessionId(sessionId)
                .content(userInput)
                .timestamp(System.currentTimeMillis())
                .segmentType("USER_INPUT")
                .roundIndex(segmentContext.getCurrentRound())
                .role("user")
                .build());

        // Generate a task ID for tracking through the pipeline
        String taskId = UUID.randomUUID().toString();
        instance.getContext().setCurrentTaskId(taskId);

        try {
            // =============================================================
            // Step (a): Perception
            // =============================================================
            log.info("[{}] Step 1/7: Perception -- perceiving event", sessionId);

            PerceptionArea.PerceptionResult perceptionResult = perceptionArea.perceive(event);
            if (perceptionResult == null || !shouldContinue()) {
                log.warn("[{}] Perception returned null or agent signalled to stop", sessionId);
                return;
            }

            log.info("[{}] Perception complete: language={}, scene={}", sessionId,
                    perceptionResult.getDetectedLanguage(), perceptionResult.getScene());

            // =============================================================
            // Step (b): Record USER_INPUT segment
            // =============================================================
            Segment userInputSegment = SegmentBuilder.userInput(perceptionResult.getOriginalInput());
            segmentContext.addSegment(userInputSegment);
            log.debug("[{}] Segment added: USER_INPUT", sessionId);

            // =============================================================
            // Steps (c)-(g): Cognition + Execution + Evaluation loop
            //                with retry support
            // =============================================================
            Object executionResult = null;
            String expressionType = "text";
            int retryCount = 0;

            while (retryCount <= MAX_RETRIES) {
                if (!shouldContinue()) {
                    log.info("[{}] Agent signalled to stop during retry loop", sessionId);
                    return;
                }

                // ---------------------------------------------------------
                // Step (c): Cognition -- plan
                // ---------------------------------------------------------
                log.info("[{}] Step 2/7: Cognition -- planning (attempt {}/{})",
                        sessionId, retryCount + 1, MAX_RETRIES + 1);

                CognitionArea.CognitionResult cognitionResult = cognitionArea.plan(perceptionResult);
                if (cognitionResult == null || !shouldContinue()) {
                    log.warn("[{}] Cognition returned null or agent signalled to stop", sessionId);
                    return;
                }

                log.info("[{}] Cognition complete: intent={}, complex={}, hasCode={}",
                        sessionId, cognitionResult.getIntent(),
                        cognitionResult.isComplexTask(),
                        cognitionResult.getGeneratedCode() != null);

                // ---------------------------------------------------------
                // Step (d): Record THOUGHT segment
                // ---------------------------------------------------------
                Segment thoughtSegment = SegmentBuilder.thought(cognitionResult.getPlanDescription());
                if (retryCount > 0) {
                    thoughtSegment.withMeta("retryAttempt", retryCount);
                }
                segmentContext.addSegment(thoughtSegment);
                log.debug("[{}] Segment added: THOUGHT", sessionId);

                // ---------------------------------------------------------
                // Step (e): Code execution (if code was generated)
                // ---------------------------------------------------------
                CodeExecutionResult codeExecResult = null;

                if (cognitionResult.getGeneratedCode() != null
                        && !cognitionResult.getGeneratedCode().isBlank()) {
                    log.info("[{}] Step 3/7: Code Execution -- executing generated code", sessionId);

                    // Record CODE segment
                    Segment codeSegment = SegmentBuilder.code(cognitionResult.getGeneratedCode());
                    segmentContext.addSegment(codeSegment);
                    log.debug("[{}] Segment added: CODE", sessionId);

                    try {
                        if (codeSandbox.isReady()) {
                            codeExecResult = codeSandbox.execute(cognitionResult.getGeneratedCode());

                            // Record CODE_RESULT segment
                            String codeOutput = codeExecResult.isSuccess()
                                    ? codeExecResult.getOutput()
                                    : "ERROR: " + codeExecResult.getError();
                            Segment codeResultSegment = SegmentBuilder.codeResult(
                                    codeOutput, codeExecResult.isSuccess());
                            codeResultSegment.withMeta("executionTimeMs", codeExecResult.getExecutionTimeMs());
                            segmentContext.addSegment(codeResultSegment);

                            log.info("[{}] Code execution complete: success={}, timeMs={}",
                                    sessionId, codeExecResult.isSuccess(),
                                    codeExecResult.getExecutionTimeMs());
                        } else {
                            log.warn("[{}] CodeSandbox not ready, skipping code execution", sessionId);
                            Segment codeResultSegment = SegmentBuilder.codeResult(
                                    "CodeSandbox not ready -- execution skipped", false);
                            segmentContext.addSegment(codeResultSegment);
                        }
                    } catch (Exception e) {
                        log.error("[{}] Code execution threw exception", sessionId, e);
                        Segment errorSegment = SegmentBuilder.codeResult(
                                "Exception during code execution: " + e.getMessage(), false);
                        segmentContext.addSegment(errorSegment);
                    }
                }

                // ---------------------------------------------------------
                // Step (f): Motor execution (if complex task)
                // ---------------------------------------------------------
                MotorArea.MotorResult motorResult = null;

                if (cognitionResult.isComplexTask()) {
                    log.info("[{}] Step 4/7: Motor -- executing complex task", sessionId);

                    String goal = perceptionResult.getEnhancedQuery() != null
                            ? perceptionResult.getEnhancedQuery()
                            : perceptionResult.getNormalizedInput();
                    motorResult = motorArea.executeTask(goal, cognitionResult);

                    // Record ROUND segments for each motor round
                    if (motorResult.getResults() != null) {
                        for (Map<String, Object> roundRecord : motorResult.getResults()) {
                            int roundIdx = roundRecord.containsKey("round")
                                    ? ((Number) roundRecord.get("round")).intValue()
                                    : 0;
                            String roundSummary = roundRecord.containsKey("subGoal")
                                    ? (String) roundRecord.get("subGoal")
                                    : "Round " + roundIdx;
                            Segment roundSegment = SegmentBuilder.round(roundIdx, roundSummary);
                            if (roundRecord.containsKey("progress")) {
                                roundSegment.withMeta("progress", roundRecord.get("progress"));
                            }
                            if (roundRecord.containsKey("error")) {
                                roundSegment.withMeta("error", roundRecord.get("error"));
                            }
                            segmentContext.addSegment(roundSegment);
                        }
                    }

                    log.info("[{}] Motor execution complete: completed={}, rounds={}",
                            sessionId, motorResult.isCompleted(), motorResult.getRounds());
                }

                // ---------------------------------------------------------
                // Determine the effective result for evaluation
                // ---------------------------------------------------------
                if (motorResult != null) {
                    executionResult = motorResult;
                    expressionType = "data";
                } else if (codeExecResult != null) {
                    executionResult = codeExecResult.isSuccess()
                            ? codeExecResult.getOutput()
                            : codeExecResult.getError();
                    expressionType = codeExecResult.isSuccess() ? "data" : "text";
                } else {
                    // Simple chat / direct response -- use the plan description
                    executionResult = cognitionResult.getPlanDescription();
                    expressionType = "text";
                }

                // ---------------------------------------------------------
                // Step (g): Self-evaluation
                // ---------------------------------------------------------
                log.info("[{}] Step 5/7: Self-Evaluation -- evaluating result (attempt {}/{})",
                        sessionId, retryCount + 1, MAX_RETRIES + 1);

                SelfEvaluationArea.EvaluationResult evalResult =
                        selfEvaluationArea.evaluate(taskId, executionResult);

                if (evalResult == null || !shouldContinue()) {
                    log.warn("[{}] Self-evaluation returned null or agent signalled to stop", sessionId);
                    break;
                }

                log.info("[{}] Self-evaluation: passed={}, shouldRetry={}", sessionId,
                        evalResult.isPassed(), evalResult.isShouldRetry());

                if (evalResult.isPassed()) {
                    // Evaluation passed -- proceed to expression
                    log.info("[{}] Evaluation passed, proceeding to expression", sessionId);
                    break;
                }

                if (!evalResult.isShouldRetry()) {
                    // Failed but no retry recommended (max retries already exhausted
                    // or evaluation area decided retrying won't help)
                    log.warn("[{}] Evaluation failed, no retry recommended: {}",
                            sessionId, evalResult.getFailureReason());
                    break;
                }

                // Retry: loop back to cognition
                retryCount++;
                log.info("[{}] Evaluation failed, retrying ({}/{}): {}",
                        sessionId, retryCount, MAX_RETRIES, evalResult.getFailureReason());

                // Record the retry as an ERROR segment for traceability
                Segment retrySegment = SegmentBuilder.error(
                        "Self-evaluation failed (retry " + retryCount + "/" + MAX_RETRIES + "): "
                                + evalResult.getFailureReason());
                retrySegment.withMeta("suggestions", evalResult.getSuggestions());
                segmentContext.addSegment(retrySegment);
            }

            // =============================================================
            // Step (h): Expression
            // =============================================================
            log.info("[{}] Step 6/7: Expression -- rendering output as '{}'",
                    sessionId, expressionType);

            ExpressionArea.ExpressionResult expressionResult =
                    expressionArea.express(executionResult, expressionType);

            if (expressionResult == null || !shouldContinue()) {
                log.warn("[{}] Expression returned null or agent signalled to stop", sessionId);
                return;
            }

            log.info("[{}] Expression complete: type={}", sessionId, expressionResult.getType());

            // =============================================================
            // Step (i): Record EXPRESS segment
            // =============================================================
            String expressContent = expressionResult.getContent() != null
                    ? expressionResult.getContent().toString()
                    : "";
            Segment expressSegment = SegmentBuilder.express(expressContent);
            expressSegment.withMeta("outputType", expressionResult.getType().name());
            segmentContext.addSegment(expressSegment);
            log.debug("[{}] Segment added: EXPRESS", sessionId);

            // Archive the current round in SegmentContext
            segmentContext.archiveCurrentRound();

            // Record the assistant response in short-term memory
            stm.addSegment(SegmentMemoryMessage.builder()
                    .memoryType(MemoryType.SHORT_TERM)
                    .sessionId(sessionId)
                    .content(expressContent)
                    .timestamp(System.currentTimeMillis())
                    .segmentType("EXPRESS")
                    .roundIndex(segmentContext.getCurrentRound())
                    .role("assistant")
                    .build());

            // =============================================================
            // Step (j): Publish output event via EventCenter
            // =============================================================
            log.info("[{}] Step 7/7: Publishing output event", sessionId);

            Event outputEvent = Event.textEvent(
                    sessionId,
                    EventType.AGENT_MESSAGE,
                    "ReActAgent",
                    expressContent);
            outputEvent.setTaskId(taskId);

            eventCenter.publishOutput(sessionId, outputEvent);

            log.info("[{}] Processing complete: taskId={}, segments={}",
                    sessionId, taskId, segmentContext.getTotalSegmentCount());

        } catch (Exception e) {
            log.error("ReAct processing error: sessionId={}, taskId={}", sessionId, taskId, e);

            // Record error in segment context
            Segment errorSegment = SegmentBuilder.error(
                    "Agent processing error: " + e.getMessage());
            segmentContext.addSegment(errorSegment);
            segmentContext.archiveCurrentRound();

            // Publish error event
            eventCenter.publishOutput(sessionId, Event.errorEvent(sessionId,
                    "Agent processing error: " + e.getMessage()));
        }
    }

    // ===================================================================
    // Signal handling
    // ===================================================================

    /**
     * Handle sleep signal.
     *
     * <p>Propagates the sleep signal to all areas, then dumps area contexts
     * and the segment timeline into the agent's {@link AgentContext} for
     * persistence.</p>
     */
    public void onSleep() {
        log.info("ReActAgent sleeping: sessionId={}", sessionId);

        // Propagate sleep to all areas
        for (com.agent.core.agent.area.BaseArea area : areas.values()) {
            area.onSleep();
        }

        // Dump area contexts
        Map<String, Object> areaContexts = new HashMap<>();
        for (Map.Entry<String, com.agent.core.agent.area.BaseArea> entry : areas.entrySet()) {
            areaContexts.put(entry.getKey(), entry.getValue().dumpContext());
        }

        // Dump segment context
        areaContexts.put("SegmentContext", segmentContext.toMap());

        instance.getContext().setActContexts(areaContexts);

        log.info("ReActAgent sleep complete: sessionId={}, areaContexts={}, segments={}",
                sessionId, areas.keySet(), segmentContext.getTotalSegmentCount());
    }

    /**
     * Handle stop signal.
     *
     * <p>Propagates the stop signal to all areas and shuts down the
     * {@link CodeSandbox}.</p>
     */
    public void onStop() {
        log.info("ReActAgent stopping: sessionId={}", sessionId);

        // Propagate stop to all areas
        for (com.agent.core.agent.area.BaseArea area : areas.values()) {
            area.onStop();
        }

        // Shut down code sandbox
        try {
            codeSandbox.shutdown();
            log.info("CodeSandbox shut down for sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("Error shutting down CodeSandbox: sessionId={}", sessionId, e);
        }

        // Promote short-term memory to long-term before stopping
        try {
            memoryManager.promoteToLongTerm(sessionId);
            log.info("Short-term memory promoted to long-term: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("Error promoting memory: sessionId={}", sessionId, e);
        }

        log.info("ReActAgent stopped: sessionId={}", sessionId);
    }

    // ===================================================================
    // Internal helpers
    // ===================================================================

    /**
     * Check whether the agent should continue executing (no sleep or stop signal).
     */
    private boolean shouldContinue() {
        return !instance.shouldSleep() && !instance.shouldStop();
    }

    /**
     * Extract text content from an event's first artifact part.
     */
    private String extractTextFromEvent(Event event) {
        if (event.getArtifact() != null
                && event.getArtifact().getParts() != null
                && !event.getArtifact().getParts().isEmpty()) {
            Object data = event.getArtifact().getParts().get(0).getData();
            return data != null ? data.toString() : null;
        }
        return null;
    }

    /**
     * Truncate a string for logging purposes.
     */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
