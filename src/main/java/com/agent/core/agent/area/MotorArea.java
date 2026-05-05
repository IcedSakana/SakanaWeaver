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
 * Motor Area -- the executive-function / motor-planning region of the agent.
 *
 * <p>Handles complex, multi-step tasks that the {@link CognitionArea} has flagged
 * with {@code isComplexTask = true}. Operates in an <b>internal loop</b> where each
 * round consists of:</p>
 *
 * <ol>
 *   <li><b>Sub-goal decomposition</b> -- break the remaining work into the next
 *       actionable sub-goal.</li>
 *   <li><b>Segment execution</b> -- use the Segment mechanism (LLM call with
 *       accumulated context) to generate and execute code for the sub-goal.</li>
 *   <li><b>Progress assessment</b> -- evaluate whether the overall goal has been
 *       met, partially met, or requires more rounds.</li>
 *   <li><b>Termination check</b> -- complete if goal is achieved, or give up if
 *       the maximum round count is exceeded or no progress is being made.</li>
 * </ol>
 *
 * <p>The loop is bounded by {@link #maxRounds} to prevent runaway execution.
 * Sleep/stop signals are checked between rounds for graceful interruption.</p>
 *
 * @author agent-framework
 */
@Slf4j
public class MotorArea extends BaseArea {

    private static final String AREA_NAME = "MotorArea";

    /** Default maximum number of execution rounds before giving up. */
    private static final int DEFAULT_MAX_ROUNDS = 10;

    /** Configurable maximum rounds. */
    private int maxRounds = DEFAULT_MAX_ROUNDS;

    /** Segment history for the current task execution (cleared per task). */
    private final List<Map<String, Object>> segmentHistory = new ArrayList<>();

    /** Accumulated results from each round. */
    private final List<Map<String, Object>> roundResults = new ArrayList<>();

    /** The goal currently being pursued. */
    private String currentGoal;

    /** Current round number (for dump/load). */
    private int currentRound;

    public MotorArea() {
        // Concrete Act implementations (e.g., SubGoalDecompositionAct,
        // ProgressAssessmentAct) are registered externally via registerAct().
    }

    // -------------------------------------------------
    // Main entry point
    // -------------------------------------------------

    /**
     * Execute a complex task through the internal loop mechanism.
     *
     * <p>Each round builds an LLM segment with accumulated context, decomposes
     * the next sub-goal, generates code, executes it, and assesses progress.
     * The loop terminates when the goal is achieved, signals are received,
     * or the maximum round count is exceeded.</p>
     *
     * @param goal      the high-level goal description
     * @param cognition the cognition result from the Cognition Area, containing
     *                  the initial intent and any generated code
     * @return a {@link MotorResult} summarizing the execution outcome
     */
    public MotorResult executeTask(String goal, CognitionArea.CognitionResult cognition) {
        if (!shouldContinue()) {
            log.debug("{}: skipped -- sleep/stop signal received", AREA_NAME);
            return MotorResult.builder()
                    .completed(false)
                    .rounds(0)
                    .summary("Execution skipped due to sleep/stop signal")
                    .results(List.of())
                    .build();
        }

        log.info("{}: starting complex task execution -- goal: {}", AREA_NAME, truncate(goal, 120));

        this.currentGoal = goal;
        segmentHistory.clear();
        roundResults.clear();

        // Seed the segment history with the cognition result
        Map<String, Object> initialSegment = new HashMap<>();
        initialSegment.put("role", "system");
        initialSegment.put("goal", goal);
        initialSegment.put("intent", cognition.getIntent());
        initialSegment.put("initialCode", cognition.getGeneratedCode());
        initialSegment.put("planDescription", cognition.getPlanDescription());
        initialSegment.put("timestamp", System.currentTimeMillis());
        segmentHistory.add(initialSegment);

        boolean completed = false;
        int round = 0;

        // --- Internal execution loop ---
        while (round < maxRounds && shouldContinue()) {
            round++;
            this.currentRound = round;

            log.info("{}: round {}/{} -- goal: {}", AREA_NAME, round, maxRounds, truncate(goal, 80));

            try {
                // Step 1: Decompose next sub-goal
                String subGoal = decomposeSubGoal(goal, round);
                log.debug("{}: round {} sub-goal: {}", AREA_NAME, round, subGoal);

                // Step 2: Build segment and execute via LLM / Acts
                Map<String, Object> roundSegment = buildRoundSegment(subGoal, round);
                segmentHistory.add(roundSegment);

                Object roundOutput = executeRound(subGoal, round);

                // Step 3: Record round result
                Map<String, Object> roundRecord = new HashMap<>();
                roundRecord.put("round", round);
                roundRecord.put("subGoal", subGoal);
                roundRecord.put("output", roundOutput);
                roundRecord.put("timestamp", System.currentTimeMillis());
                roundResults.add(roundRecord);

                // Step 4: Assess progress
                ProgressStatus progress = assessProgress(goal, roundResults);
                roundRecord.put("progress", progress.name());

                log.info("{}: round {} progress: {}", AREA_NAME, round, progress);

                if (progress == ProgressStatus.COMPLETED) {
                    completed = true;
                    break;
                } else if (progress == ProgressStatus.STUCK) {
                    log.warn("{}: progress stuck at round {}, giving up", AREA_NAME, round);
                    break;
                }
                // PROGRESSING -> continue to next round

            } catch (Exception e) {
                log.error("{}: round {} failed with exception", AREA_NAME, round, e);
                Map<String, Object> errorRecord = new HashMap<>();
                errorRecord.put("round", round);
                errorRecord.put("error", e.getMessage());
                errorRecord.put("timestamp", System.currentTimeMillis());
                roundResults.add(errorRecord);
                // Continue to next round; the progress assessment will decide if we should stop
            }
        }

        if (round >= maxRounds && !completed) {
            log.warn("{}: reached max rounds ({}) without completing goal", AREA_NAME, maxRounds);
        }

        String summary = buildSummary(completed, round);

        MotorResult result = MotorResult.builder()
                .completed(completed)
                .rounds(round)
                .results(new ArrayList<>(roundResults))
                .summary(summary)
                .build();

        log.info("{}: task execution finished -- completed={}, rounds={}", AREA_NAME, completed, round);

        return result;
    }

    // -------------------------------------------------
    // Internal pipeline steps
    // -------------------------------------------------

    /**
     * Decompose the overall goal into the next actionable sub-goal.
     *
     * <p>If a {@code SubGoalDecomposition} act is registered it will be used;
     * otherwise a placeholder sub-goal is generated.</p>
     */
    private String decomposeSubGoal(String goal, int round) {
        BaseAct decompositionAct = getAct("SubGoalDecomposition");
        if (decompositionAct != null) {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("goal", goal);
                input.put("round", round);
                input.put("previousResults", roundResults);
                input.put("segmentHistory", segmentHistory);
                Object result = decompositionAct.execute(input);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Exception e) {
                log.warn("{}: SubGoalDecomposition act failed, using fallback", AREA_NAME, e);
            }
        }

        // Placeholder sub-goal
        return String.format("Round %d: Continue working on goal -- %s", round, truncate(goal, 80));
    }

    /**
     * Build an LLM prompt segment for the current round.
     */
    private Map<String, Object> buildRoundSegment(String subGoal, int round) {
        Map<String, Object> segment = new HashMap<>();
        segment.put("role", "assistant");
        segment.put("round", round);
        segment.put("subGoal", subGoal);
        segment.put("previousRounds", roundResults.size());
        segment.put("timestamp", System.currentTimeMillis());
        return segment;
    }

    /**
     * Execute the current round's sub-goal.
     *
     * <p>If a {@code RoundExecution} act is registered it will be used;
     * otherwise the base area's registered acts are tried in order.</p>
     */
    private Object executeRound(String subGoal, int round) {
        BaseAct executionAct = getAct("RoundExecution");
        if (executionAct != null) {
            Map<String, Object> input = new HashMap<>();
            input.put("subGoal", subGoal);
            input.put("round", round);
            input.put("segmentHistory", segmentHistory);
            return executionAct.execute(input);
        }

        // Placeholder: In production, the Segment mechanism generates and
        // executes Python code for this sub-goal.
        log.debug("{}: placeholder round execution for round {}", AREA_NAME, round);
        return Map.of("status", "placeholder", "round", round, "subGoal", subGoal);
    }

    /**
     * Assess overall progress toward the goal.
     *
     * <p>If a {@code ProgressAssessment} act is registered it will be used;
     * otherwise a simple heuristic is applied.</p>
     */
    private ProgressStatus assessProgress(String goal, List<Map<String, Object>> results) {
        BaseAct assessmentAct = getAct("ProgressAssessment");
        if (assessmentAct != null) {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("goal", goal);
                input.put("results", results);
                Object result = assessmentAct.execute(input);
                if (result instanceof String) {
                    return ProgressStatus.valueOf((String) result);
                }
                if (result instanceof ProgressStatus) {
                    return (ProgressStatus) result;
                }
            } catch (Exception e) {
                log.warn("{}: ProgressAssessment act failed, using heuristic", AREA_NAME, e);
            }
        }

        // Placeholder heuristic: check for consecutive errors
        if (results.size() >= 3) {
            long recentErrors = results.subList(Math.max(0, results.size() - 3), results.size())
                    .stream()
                    .filter(r -> r.containsKey("error"))
                    .count();
            if (recentErrors >= 3) {
                return ProgressStatus.STUCK;
            }
        }

        return ProgressStatus.PROGRESSING;
    }

    /**
     * Build a summary of the entire task execution.
     */
    private String buildSummary(boolean completed, int totalRounds) {
        StringBuilder sb = new StringBuilder();
        sb.append(completed ? "Task completed successfully" : "Task did not complete");
        sb.append(String.format(" after %d round(s).", totalRounds));

        long errors = roundResults.stream().filter(r -> r.containsKey("error")).count();
        if (errors > 0) {
            sb.append(String.format(" Encountered %d error(s) during execution.", errors));
        }

        return sb.toString();
    }

    // -------------------------------------------------
    // BaseArea contract
    // -------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        if (input instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) input;
            String goal = (String) params.get("goal");
            CognitionArea.CognitionResult cognition =
                    (CognitionArea.CognitionResult) params.get("cognition");
            if (goal != null && cognition != null) {
                return executeTask(goal, cognition);
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
    // Configuration
    // -------------------------------------------------

    /**
     * Set the maximum number of rounds before the motor loop gives up.
     *
     * @param maxRounds maximum rounds (must be &gt; 0)
     */
    public void setMaxRounds(int maxRounds) {
        if (maxRounds <= 0) {
            throw new IllegalArgumentException("maxRounds must be positive, got: " + maxRounds);
        }
        this.maxRounds = maxRounds;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    // -------------------------------------------------
    // Persistence
    // -------------------------------------------------

    @Override
    public Map<String, Object> dumpContext() {
        Map<String, Object> context = super.dumpContext();
        context.put("maxRounds", maxRounds);
        context.put("currentGoal", currentGoal);
        context.put("currentRound", currentRound);
        context.put("segmentHistory", new ArrayList<>(segmentHistory));
        context.put("roundResults", new ArrayList<>(roundResults));
        return context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadContext(Map<String, Object> context) {
        super.loadContext(context);
        if (context == null) return;

        if (context.containsKey("maxRounds")) {
            Object mr = context.get("maxRounds");
            if (mr instanceof Number) {
                this.maxRounds = ((Number) mr).intValue();
            }
        }
        if (context.containsKey("currentGoal")) {
            this.currentGoal = (String) context.get("currentGoal");
        }
        if (context.containsKey("currentRound")) {
            Object cr = context.get("currentRound");
            if (cr instanceof Number) {
                this.currentRound = ((Number) cr).intValue();
            }
        }
        if (context.containsKey("segmentHistory")) {
            segmentHistory.clear();
            Object hist = context.get("segmentHistory");
            if (hist instanceof List) {
                segmentHistory.addAll((List<Map<String, Object>>) hist);
            }
        }
        if (context.containsKey("roundResults")) {
            roundResults.clear();
            Object rr = context.get("roundResults");
            if (rr instanceof List) {
                roundResults.addAll((List<Map<String, Object>>) rr);
            }
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
    // Inner types
    // =================================================

    /**
     * Progress status of the motor loop.
     */
    public enum ProgressStatus {
        /** Making progress toward the goal. */
        PROGRESSING,
        /** Goal has been fully achieved. */
        COMPLETED,
        /** No progress is being made; should give up. */
        STUCK
    }

    /**
     * Result produced by the Motor Area's multi-round task execution.
     *
     * <p>Contains whether the task completed, how many rounds were executed,
     * detailed per-round results, and a human-readable summary.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MotorResult {

        /** Whether the overall goal was achieved. */
        private boolean completed;

        /** Total number of execution rounds performed. */
        private int rounds;

        /** Per-round result details (sub-goal, output, progress, errors). */
        private List<Map<String, Object>> results;

        /** Human-readable summary of the task execution. */
        private String summary;
    }
}
