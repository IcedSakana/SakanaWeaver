package com.agent.core.agent.area;

import com.agent.core.agent.BaseAct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cognition Area -- the prefrontal cortex / intent-planning region of the agent.
 *
 * <p>Receives a {@link PerceptionArea.PerceptionResult} from the Perception Area
 * and performs intent recognition, reasoning, and plan generation. Internally it
 * uses a <em>Segment</em> mechanism: conversation history is segmented and fed
 * to the underlying LLM so that intent and plan are derived with full context.</p>
 *
 * <h3>Processing Pipeline</h3>
 * <ol>
 *   <li>Segment construction -- build the LLM prompt segment from perception result
 *       and conversation history</li>
 *   <li>Intent recognition -- classify user intent (QUERY, OPERATE, CHAT, etc.)</li>
 *   <li>Code generation -- produce Python code that implements the plan</li>
 *   <li>Complexity assessment -- determine if the task is complex enough to require
 *       delegation to the {@link MotorArea}</li>
 * </ol>
 *
 * <p>When {@link CognitionResult#isComplexTask} is {@code true}, the orchestrating
 * agent should forward the result to {@link MotorArea} for multi-round execution.</p>
 *
 * @author agent-framework
 */
@Slf4j
public class CognitionArea extends BaseArea {

    private static final String AREA_NAME = "CognitionArea";

    /** Maximum number of history segments to include in the LLM prompt. */
    private static final int MAX_SEGMENT_HISTORY = 20;

    /** Conversation segment history used for LLM context construction. */
    private final List<Map<String, Object>> segmentHistory = new ArrayList<>();

    /** History of recognized intents for tracking. */
    private final List<String> intentHistory = new ArrayList<>();

    /** LLM endpoint for intent recognition and code generation (configurable). */
    private String llmEndpoint;

    public CognitionArea() {
        // Concrete Act implementations (e.g., IntentRecognitionAct, CodeGenerationAct)
        // are registered externally via registerAct().
    }

    // -------------------------------------------------
    // Main entry point
    // -------------------------------------------------

    /**
     * Plan a response based on the perception result.
     *
     * <p>Constructs an LLM segment from the perception result and conversation
     * history, runs intent recognition, generates executable Python code, and
     * assesses task complexity.</p>
     *
     * @param perceptionResult the output of the Perception Area
     * @return a {@link CognitionResult} describing the intent, generated code,
     *         and complexity flag; or {@code null} if stopped
     */
    public CognitionResult plan(PerceptionArea.PerceptionResult perceptionResult) {
        if (!shouldContinue()) {
            log.debug("{}: skipped -- sleep/stop signal received", AREA_NAME);
            return null;
        }

        log.info("{}: planning for input: {}", AREA_NAME,
                truncate(perceptionResult.getEnhancedQuery() != null
                        ? perceptionResult.getEnhancedQuery()
                        : perceptionResult.getNormalizedInput(), 100));

        // Step 1: Build segment for LLM context
        Map<String, Object> segment = buildSegment(perceptionResult);
        segmentHistory.add(segment);
        trimSegmentHistory();

        // Step 2: Intent recognition
        String intent = recognizeIntent(perceptionResult);
        intentHistory.add(intent);

        if (!shouldContinue()) return null;

        // Step 3: Generate Python code / execution plan
        String generatedCode = generateCode(perceptionResult, intent);

        if (!shouldContinue()) return null;

        // Step 4: Build plan description
        String planDescription = buildPlanDescription(intent, perceptionResult);

        // Step 5: Assess complexity -- complex tasks are delegated to MotorArea
        boolean isComplex = assessComplexity(intent, perceptionResult, generatedCode);

        CognitionResult result = CognitionResult.builder()
                .intent(intent)
                .generatedCode(generatedCode)
                .planDescription(planDescription)
                .isComplexTask(isComplex)
                .build();

        log.info("{}: planning complete -- intent={}, complex={}, codeLength={}",
                AREA_NAME, intent, isComplex,
                generatedCode != null ? generatedCode.length() : 0);

        return result;
    }

    // -------------------------------------------------
    // Internal pipeline steps
    // -------------------------------------------------

    /**
     * Build a prompt segment from the perception result and existing history.
     *
     * <p>In production this constructs the full LLM prompt including system
     * instructions, tool descriptions, and conversation history.</p>
     *
     * @param pr the perception result
     * @return a segment map ready for LLM consumption
     */
    private Map<String, Object> buildSegment(PerceptionArea.PerceptionResult pr) {
        Map<String, Object> segment = new HashMap<>();
        segment.put("role", "user");
        segment.put("content", pr.getEnhancedQuery() != null ? pr.getEnhancedQuery() : pr.getNormalizedInput());
        segment.put("language", pr.getDetectedLanguage());
        segment.put("scene", pr.getScene());
        segment.put("timestamp", System.currentTimeMillis());

        if (pr.getEnvironmentContext() != null) {
            segment.put("environment", pr.getEnvironmentContext());
        }
        if (pr.getAttachments() != null && !pr.getAttachments().isEmpty()) {
            segment.put("attachments", pr.getAttachments());
        }

        return segment;
    }

    /**
     * Recognize user intent using LLM or registered Acts.
     *
     * <p>If an {@code IntentRecognition} act is registered it will be used;
     * otherwise a placeholder classification is performed.</p>
     *
     * @param pr the perception result
     * @return the classified intent string (e.g., QUERY, OPERATE, CHAT, COMPLEX_TASK)
     */
    private String recognizeIntent(PerceptionArea.PerceptionResult pr) {
        BaseAct intentAct = getAct("IntentRecognition");
        if (intentAct != null) {
            try {
                Object result = intentAct.execute(pr);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Exception e) {
                log.warn("{}: IntentRecognition act failed, falling back to default", AREA_NAME, e);
            }
        }

        // Placeholder intent classification
        // In production, the LLM call with segmentHistory would return the intent.
        log.debug("{}: using placeholder intent classification", AREA_NAME);
        return "CHAT";
    }

    /**
     * Generate executable Python code that implements the plan.
     *
     * <p>Uses the Segment mechanism: the full segmentHistory is sent to the LLM
     * which produces Python code calling available tools/APIs. If a
     * {@code CodeGeneration} act is registered it will be invoked; otherwise
     * a placeholder is returned.</p>
     *
     * @param pr     the perception result
     * @param intent the recognized intent
     * @return generated Python code string, or {@code null} if not applicable
     */
    private String generateCode(PerceptionArea.PerceptionResult pr, String intent) {
        BaseAct codeGenAct = getAct("CodeGeneration");
        if (codeGenAct != null) {
            try {
                Map<String, Object> codeInput = new HashMap<>();
                codeInput.put("perceptionResult", pr);
                codeInput.put("intent", intent);
                codeInput.put("segmentHistory", segmentHistory);
                Object result = codeGenAct.execute(codeInput);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Exception e) {
                log.warn("{}: CodeGeneration act failed", AREA_NAME, e);
            }
        }

        // Placeholder: In production the LLM generates Python code here.
        log.debug("{}: code generation skipped (no act or placeholder mode)", AREA_NAME);
        return null;
    }

    /**
     * Build a human-readable description of the execution plan.
     */
    private String buildPlanDescription(String intent, PerceptionArea.PerceptionResult pr) {
        String query = pr.getEnhancedQuery() != null ? pr.getEnhancedQuery() : pr.getNormalizedInput();
        return String.format("Intent: %s | Query: %s | Scene: %s",
                intent,
                truncate(query, 60),
                pr.getScene() != null ? pr.getScene() : "general");
    }

    /**
     * Assess whether the task is complex enough to require multi-round
     * execution in the {@link MotorArea}.
     *
     * <p>Complexity heuristics (placeholder):</p>
     * <ul>
     *   <li>Intent is explicitly COMPLEX_TASK or MULTI_STEP</li>
     *   <li>Generated code contains multiple function calls</li>
     *   <li>Query length exceeds a threshold indicating detailed instructions</li>
     * </ul>
     */
    private boolean assessComplexity(String intent, PerceptionArea.PerceptionResult pr, String code) {
        if ("COMPLEX_TASK".equals(intent) || "MULTI_STEP".equals(intent)) {
            return true;
        }
        if (code != null && code.lines().count() > 15) {
            return true;
        }
        String query = pr.getEnhancedQuery() != null ? pr.getEnhancedQuery() : pr.getNormalizedInput();
        return query != null && query.length() > 500;
    }

    /**
     * Trim segment history to prevent unbounded growth.
     */
    private void trimSegmentHistory() {
        while (segmentHistory.size() > MAX_SEGMENT_HISTORY) {
            segmentHistory.remove(0);
        }
    }

    // -------------------------------------------------
    // BaseArea contract
    // -------------------------------------------------

    @Override
    public Object execute(Object input) {
        if (input instanceof PerceptionArea.PerceptionResult) {
            return plan((PerceptionArea.PerceptionResult) input);
        }
        log.warn("{}: execute called with unsupported input type: {}",
                AREA_NAME, input == null ? "null" : input.getClass().getName());
        return null;
    }

    @Override
    public String getAreaName() {
        return AREA_NAME;
    }

    // -------------------------------------------------
    // Configuration
    // -------------------------------------------------

    /**
     * Set the LLM endpoint URL for intent recognition and code generation.
     *
     * @param llmEndpoint the endpoint URL
     */
    public void setLlmEndpoint(String llmEndpoint) {
        this.llmEndpoint = llmEndpoint;
    }

    public String getLlmEndpoint() {
        return llmEndpoint;
    }

    // -------------------------------------------------
    // Persistence
    // -------------------------------------------------

    @Override
    public Map<String, Object> dumpContext() {
        Map<String, Object> context = super.dumpContext();
        context.put("segmentHistory", new ArrayList<>(segmentHistory));
        context.put("intentHistory", new ArrayList<>(intentHistory));
        context.put("llmEndpoint", llmEndpoint);
        return context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadContext(Map<String, Object> context) {
        super.loadContext(context);
        if (context == null) return;

        if (context.containsKey("segmentHistory")) {
            segmentHistory.clear();
            Object hist = context.get("segmentHistory");
            if (hist instanceof List) {
                segmentHistory.addAll((List<Map<String, Object>>) hist);
            }
        }
        if (context.containsKey("intentHistory")) {
            intentHistory.clear();
            Object hist = context.get("intentHistory");
            if (hist instanceof List) {
                intentHistory.addAll((List<String>) hist);
            }
        }
        if (context.containsKey("llmEndpoint")) {
            this.llmEndpoint = (String) context.get("llmEndpoint");
        }
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // =================================================
    // Inner result class
    // =================================================

    /**
     * Result produced by the Cognition Area's planning process.
     *
     * <p>Contains the recognized intent, optionally generated Python code,
     * a human-readable plan description, and a flag indicating whether the
     * task is complex enough to require delegation to the Motor Area.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CognitionResult {

        /** Recognized user intent (e.g., QUERY, OPERATE, CHAT, COMPLEX_TASK). */
        private String intent;

        /** Generated Python code implementing the execution plan. May be {@code null} for simple intents. */
        private String generatedCode;

        /** Human-readable description of the plan. */
        private String planDescription;

        /**
         * Whether this task is complex and should be delegated to the
         * {@link MotorArea} for multi-round execution.
         */
        private boolean isComplexTask;
    }
}
