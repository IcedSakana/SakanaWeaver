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
 * Self-Evaluation Area -- the metacognitive / quality-assurance region of the agent.
 *
 * <p>Evaluates whether the agent's output for a given task meets quality criteria
 * and satisfies the user's original intent. Acts as an internal feedback loop that
 * can trigger retries or surface improvement suggestions.</p>
 *
 * <h3>Evaluation Pipeline</h3>
 * <ol>
 *   <li><b>Completeness check</b> -- did the result address the user's request?</li>
 *   <li><b>Correctness check</b> -- is the result logically and factually sound?</li>
 *   <li><b>Quality check</b> -- does the result meet formatting, tone, and
 *       depth expectations?</li>
 *   <li><b>Safety check</b> -- does the result comply with content policies?</li>
 * </ol>
 *
 * <p>The evaluation produces an {@link EvaluationResult} containing a pass/fail
 * verdict, whether a retry is recommended, the failure reason (if any), and
 * actionable suggestions for the next attempt.</p>
 *
 * @author agent-framework
 */
@Slf4j
public class SelfEvaluationArea extends BaseArea {

    private static final String AREA_NAME = "SelfEvaluationArea";

    /** Evaluation history keyed by taskId, for tracking quality trends. */
    private final List<Map<String, Object>> evaluationHistory = new ArrayList<>();

    /** Maximum evaluation history entries to retain. */
    private static final int MAX_EVALUATION_HISTORY = 100;

    /** Maximum number of retries recommended before giving up. */
    private static final int MAX_RETRY_SUGGESTIONS = 3;

    public SelfEvaluationArea() {
        // Concrete Act implementations (e.g., CompletenessCheckAct,
        // CorrectnessCheckAct, SafetyCheckAct) are registered externally
        // via registerAct().
    }

    // -------------------------------------------------
    // Main entry point
    // -------------------------------------------------

    /**
     * Evaluate the result of a completed task.
     *
     * <p>Runs registered evaluation acts (completeness, correctness, quality,
     * safety) and aggregates their verdicts into a single {@link EvaluationResult}.
     * If any check fails, the result includes the failure reason and suggestions
     * for improvement.</p>
     *
     * @param taskId the identifier of the task being evaluated
     * @param result the task output to evaluate (may be any type)
     * @return an {@link EvaluationResult} with the evaluation verdict; or
     *         {@code null} if the area is stopped
     */
    public EvaluationResult evaluate(String taskId, Object result) {
        if (!shouldContinue()) {
            log.debug("{}: skipped -- sleep/stop signal received", AREA_NAME);
            return null;
        }

        log.info("{}: evaluating task={}, resultType={}",
                AREA_NAME, taskId,
                result != null ? result.getClass().getSimpleName() : "null");

        List<String> failureReasons = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        boolean allPassed = true;

        // --- Run evaluation checks ---

        // 1. Completeness check
        if (shouldContinue()) {
            CheckResult completeness = runCheck("CompletenessCheck", taskId, result);
            if (!completeness.passed) {
                allPassed = false;
                failureReasons.add("Completeness: " + completeness.reason);
                suggestions.addAll(completeness.suggestions);
            }
        }

        // 2. Correctness check
        if (shouldContinue()) {
            CheckResult correctness = runCheck("CorrectnessCheck", taskId, result);
            if (!correctness.passed) {
                allPassed = false;
                failureReasons.add("Correctness: " + correctness.reason);
                suggestions.addAll(correctness.suggestions);
            }
        }

        // 3. Quality check
        if (shouldContinue()) {
            CheckResult quality = runCheck("QualityCheck", taskId, result);
            if (!quality.passed) {
                allPassed = false;
                failureReasons.add("Quality: " + quality.reason);
                suggestions.addAll(quality.suggestions);
            }
        }

        // 4. Safety check
        if (shouldContinue()) {
            CheckResult safety = runCheck("SafetyCheck", taskId, result);
            if (!safety.passed) {
                allPassed = false;
                failureReasons.add("Safety: " + safety.reason);
                suggestions.addAll(safety.suggestions);
            }
        }

        // Determine retry recommendation
        int previousRetries = countPreviousRetries(taskId);
        boolean shouldRetry = !allPassed && previousRetries < MAX_RETRY_SUGGESTIONS;

        String aggregatedFailureReason = allPassed
                ? null
                : String.join("; ", failureReasons);

        EvaluationResult evalResult = EvaluationResult.builder()
                .passed(allPassed)
                .shouldRetry(shouldRetry)
                .failureReason(aggregatedFailureReason)
                .suggestions(suggestions)
                .build();

        // Record evaluation in history
        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("taskId", taskId);
        historyEntry.put("passed", allPassed);
        historyEntry.put("shouldRetry", shouldRetry);
        historyEntry.put("failureReason", aggregatedFailureReason);
        historyEntry.put("retryCount", previousRetries + (shouldRetry ? 1 : 0));
        historyEntry.put("timestamp", System.currentTimeMillis());
        evaluationHistory.add(historyEntry);
        trimEvaluationHistory();

        log.info("{}: evaluation complete -- task={}, passed={}, shouldRetry={}",
                AREA_NAME, taskId, allPassed, shouldRetry);

        return evalResult;
    }

    // -------------------------------------------------
    // Internal check execution
    // -------------------------------------------------

    /**
     * Run a named evaluation check.
     *
     * <p>If a corresponding Act is registered it will be invoked; otherwise
     * the check passes by default (optimistic fallback).</p>
     *
     * @param checkName the act name for the check
     * @param taskId    the task being evaluated
     * @param result    the task output
     * @return a {@link CheckResult} with the check verdict
     */
    @SuppressWarnings("unchecked")
    private CheckResult runCheck(String checkName, String taskId, Object result) {
        BaseAct checkAct = getAct(checkName);
        if (checkAct == null) {
            // No act registered -- optimistic pass
            log.debug("{}: no '{}' act registered, skipping check", AREA_NAME, checkName);
            return new CheckResult(true, null, List.of());
        }

        try {
            Map<String, Object> input = new HashMap<>();
            input.put("taskId", taskId);
            input.put("result", result);
            input.put("evaluationHistory", evaluationHistory);

            Object checkOutput = checkAct.execute(input);

            if (checkOutput instanceof Map) {
                Map<String, Object> outputMap = (Map<String, Object>) checkOutput;
                boolean passed = Boolean.TRUE.equals(outputMap.get("passed"));
                String reason = (String) outputMap.get("reason");
                List<String> sug = outputMap.containsKey("suggestions")
                        ? (List<String>) outputMap.get("suggestions")
                        : List.of();
                return new CheckResult(passed, reason, sug);
            } else if (checkOutput instanceof Boolean) {
                return new CheckResult((Boolean) checkOutput, null, List.of());
            }

            // Treat non-null output as passed
            return new CheckResult(true, null, List.of());

        } catch (Exception e) {
            log.warn("{}: '{}' act threw exception, treating as failure", AREA_NAME, checkName, e);
            return new CheckResult(false,
                    checkName + " threw exception: " + e.getMessage(),
                    List.of("Investigate " + checkName + " failure: " + e.getMessage()));
        }
    }

    /**
     * Count previous retry evaluations for the same taskId.
     */
    private int countPreviousRetries(String taskId) {
        return (int) evaluationHistory.stream()
                .filter(entry -> taskId.equals(entry.get("taskId")))
                .filter(entry -> Boolean.TRUE.equals(entry.get("shouldRetry")))
                .count();
    }

    private void trimEvaluationHistory() {
        while (evaluationHistory.size() > MAX_EVALUATION_HISTORY) {
            evaluationHistory.remove(0);
        }
    }

    // -------------------------------------------------
    // BaseArea contract
    // -------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        if (input instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) input;
            String taskId = (String) params.get("taskId");
            Object result = params.get("result");
            if (taskId != null) {
                return evaluate(taskId, result);
            }
        }
        log.warn("{}: execute called with unsupported input: {}",
                AREA_NAME, input == null ? "null" : input.getClass().getName());
        return null;
    }

    @Override
    public String getAreaName() {
        return AREA_NAME;
    }

    // -------------------------------------------------
    // Persistence
    // -------------------------------------------------

    @Override
    public Map<String, Object> dumpContext() {
        Map<String, Object> context = super.dumpContext();
        context.put("evaluationHistory", new ArrayList<>(evaluationHistory));
        return context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadContext(Map<String, Object> context) {
        super.loadContext(context);
        if (context == null) return;

        if (context.containsKey("evaluationHistory")) {
            evaluationHistory.clear();
            Object hist = context.get("evaluationHistory");
            if (hist instanceof List) {
                evaluationHistory.addAll((List<Map<String, Object>>) hist);
            }
        }
    }

    // =================================================
    // Inner types
    // =================================================

    /**
     * Internal container for individual check results.
     */
    private static class CheckResult {
        final boolean passed;
        final String reason;
        final List<String> suggestions;

        CheckResult(boolean passed, String reason, List<String> suggestions) {
            this.passed = passed;
            this.reason = reason;
            this.suggestions = suggestions != null ? suggestions : List.of();
        }
    }

    /**
     * Result produced by the Self-Evaluation Area.
     *
     * <p>Aggregates verdicts from all evaluation checks into a single
     * pass/fail result with retry recommendation and actionable suggestions.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationResult {

        /** Whether the task result passed all evaluation checks. */
        private boolean passed;

        /** Whether the agent should retry the task with improvements. */
        private boolean shouldRetry;

        /**
         * Aggregated failure reason from all failed checks.
         * {@code null} when {@code passed} is {@code true}.
         */
        private String failureReason;

        /** Actionable suggestions for improving the result on retry. */
        private List<String> suggestions;
    }
}
