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
 * Expression Area -- the language-production / output region of the agent.
 *
 * <p>Responsible for transforming internal processing results into user-facing
 * output. Supports multiple expression types:</p>
 *
 * <ul>
 *   <li><b>TEXT</b> -- plain text pass-through (chat replies, simple answers)</li>
 *   <li><b>DATA</b> -- structured data that requires the Segment history for
 *       context-aware answer synthesis</li>
 *   <li><b>CARD</b> -- rich card / form data passed directly to the frontend</li>
 *   <li><b>EVENT</b> -- system events or status updates forwarded as-is</li>
 * </ul>
 *
 * <p>The main entry point is {@link #express(Object, String)} which routes the
 * data through the appropriate rendering pipeline and produces an
 * {@link ExpressionResult}.</p>
 *
 * @author agent-framework
 */
@Slf4j
public class ExpressionArea extends BaseArea {

    private static final String AREA_NAME = "ExpressionArea";

    /** Segment history used for DATA-type expression (context-aware answers). */
    private final List<Map<String, Object>> segmentHistory = new ArrayList<>();

    /** Expression history for auditing / debugging. */
    private final List<Map<String, Object>> expressionHistory = new ArrayList<>();

    /** Maximum segment history entries to retain. */
    private static final int MAX_SEGMENT_HISTORY = 30;

    /** Maximum expression history entries to retain. */
    private static final int MAX_EXPRESSION_HISTORY = 50;

    public ExpressionArea() {
        // Concrete Act implementations (e.g., TextRenderingAct, DataSynthesisAct,
        // CardFormattingAct) are registered externally via registerAct().
    }

    // -------------------------------------------------
    // Main entry point
    // -------------------------------------------------

    /**
     * Express (render) the given data into a user-facing output.
     *
     * <p>Routing logic based on {@code expressionType}:</p>
     * <ul>
     *   <li>{@code "text"} -- pass the data through as plain text content</li>
     *   <li>{@code "data"} -- use Segment history to synthesize a contextual answer
     *       from structured data</li>
     *   <li>{@code "form"} / {@code "card"} -- pass complex UI data directly,
     *       output type is CARD</li>
     *   <li>anything else -- treat as an EVENT pass-through</li>
     * </ul>
     *
     * @param data           the data to express (String, Map, or arbitrary object)
     * @param expressionType the type of expression to perform
     * @return an {@link ExpressionResult} containing the rendered content and
     *         output type; or {@code null} if the area is stopped
     */
    public ExpressionResult express(Object data, String expressionType) {
        if (!shouldContinue()) {
            log.debug("{}: skipped -- sleep/stop signal received", AREA_NAME);
            return null;
        }

        String resolvedType = expressionType != null ? expressionType.toLowerCase() : "text";

        log.info("{}: expressing data as '{}', dataType={}",
                AREA_NAME, resolvedType,
                data != null ? data.getClass().getSimpleName() : "null");

        ExpressionResult result;

        switch (resolvedType) {
            case "text":
                result = expressText(data);
                break;
            case "data":
                result = expressData(data);
                break;
            case "form":
            case "card":
                result = expressCardOrForm(data);
                break;
            default:
                result = expressEvent(data, resolvedType);
                break;
        }

        // Record in history
        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("expressionType", resolvedType);
        historyEntry.put("outputType", result.getType().name());
        historyEntry.put("contentLength", result.getContent() != null
                ? result.getContent().toString().length() : 0);
        historyEntry.put("timestamp", System.currentTimeMillis());
        expressionHistory.add(historyEntry);
        trimExpressionHistory();

        log.debug("{}: expression complete -- type={}", AREA_NAME, result.getType());

        return result;
    }

    // -------------------------------------------------
    // Expression strategies
    // -------------------------------------------------

    /**
     * Express as plain text -- pass through directly.
     *
     * <p>If a {@code TextRendering} act is registered it will be invoked for
     * formatting / post-processing; otherwise the raw string representation
     * is used.</p>
     */
    private ExpressionResult expressText(Object data) {
        String content;

        BaseAct textAct = getAct("TextRendering");
        if (textAct != null) {
            try {
                Object rendered = textAct.execute(data);
                content = rendered != null ? rendered.toString() : "";
            } catch (Exception e) {
                log.warn("{}: TextRendering act failed, using raw toString", AREA_NAME, e);
                content = data != null ? data.toString() : "";
            }
        } else {
            content = data != null ? data.toString() : "";
        }

        return ExpressionResult.builder()
                .content(content)
                .type(OutputType.TEXT)
                .metadata(Map.of("source", "text_passthrough"))
                .build();
    }

    /**
     * Express structured data using Segment history for context-aware synthesis.
     *
     * <p>Adds the data to the segment history, then uses the {@code DataSynthesis}
     * act (or a placeholder) to generate a human-readable answer that incorporates
     * the structured data and conversational context.</p>
     */
    @SuppressWarnings("unchecked")
    private ExpressionResult expressData(Object data) {
        // Add data to segment history
        Map<String, Object> dataSegment = new HashMap<>();
        dataSegment.put("role", "data");
        dataSegment.put("content", data);
        dataSegment.put("timestamp", System.currentTimeMillis());
        segmentHistory.add(dataSegment);
        trimSegmentHistory();

        String synthesized;

        BaseAct dataSynthesisAct = getAct("DataSynthesis");
        if (dataSynthesisAct != null) {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("data", data);
                input.put("segmentHistory", segmentHistory);
                Object result = dataSynthesisAct.execute(input);
                synthesized = result != null ? result.toString() : data.toString();
            } catch (Exception e) {
                log.warn("{}: DataSynthesis act failed, using raw data", AREA_NAME, e);
                synthesized = data != null ? data.toString() : "";
            }
        } else {
            // Placeholder: In production the LLM synthesizes an answer from
            // the data and segment history.
            log.debug("{}: no DataSynthesis act, using raw data representation", AREA_NAME);
            synthesized = data != null ? data.toString() : "";
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "data_synthesis");
        metadata.put("segmentCount", segmentHistory.size());

        return ExpressionResult.builder()
                .content(synthesized)
                .type(OutputType.TEXT)
                .metadata(metadata)
                .build();
    }

    /**
     * Express card or form data -- pass complex structured data directly.
     *
     * <p>Cards and forms are rendered by the frontend; the Expression Area
     * simply wraps them in the result envelope. A {@code CardFormatting} act
     * can optionally enrich or validate the card payload.</p>
     */
    private ExpressionResult expressCardOrForm(Object data) {
        Object content = data;

        BaseAct cardAct = getAct("CardFormatting");
        if (cardAct != null) {
            try {
                content = cardAct.execute(data);
            } catch (Exception e) {
                log.warn("{}: CardFormatting act failed, using raw data", AREA_NAME, e);
            }
        }

        return ExpressionResult.builder()
                .content(content)
                .type(OutputType.CARD)
                .metadata(Map.of("source", "card_passthrough"))
                .build();
    }

    /**
     * Express as a system event -- pass through with EVENT type.
     */
    private ExpressionResult expressEvent(Object data, String subType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "event_passthrough");
        metadata.put("eventSubType", subType);

        return ExpressionResult.builder()
                .content(data)
                .type(OutputType.EVENT)
                .metadata(metadata)
                .build();
    }

    // -------------------------------------------------
    // Segment management
    // -------------------------------------------------

    /**
     * Append a segment to the expression area's history.
     *
     * <p>Useful when upstream areas want to feed context into the expression
     * pipeline without triggering a full expression cycle.</p>
     *
     * @param segment the segment map to append
     */
    public void appendSegment(Map<String, Object> segment) {
        segmentHistory.add(segment);
        trimSegmentHistory();
    }

    private void trimSegmentHistory() {
        while (segmentHistory.size() > MAX_SEGMENT_HISTORY) {
            segmentHistory.remove(0);
        }
    }

    private void trimExpressionHistory() {
        while (expressionHistory.size() > MAX_EXPRESSION_HISTORY) {
            expressionHistory.remove(0);
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
            Object data = params.get("data");
            String expressionType = (String) params.getOrDefault("expressionType", "text");
            return express(data, expressionType);
        }
        // Default: treat entire input as text expression
        return express(input, "text");
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
        context.put("segmentHistory", new ArrayList<>(segmentHistory));
        context.put("expressionHistory", new ArrayList<>(expressionHistory));
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
        if (context.containsKey("expressionHistory")) {
            expressionHistory.clear();
            Object hist = context.get("expressionHistory");
            if (hist instanceof List) {
                expressionHistory.addAll((List<Map<String, Object>>) hist);
            }
        }
    }

    // =================================================
    // Inner types
    // =================================================

    /**
     * Output type classification for the expression result.
     */
    public enum OutputType {
        /** Plain text response (chat message, answer). */
        TEXT,
        /** Rich card or form data for frontend rendering. */
        CARD,
        /** System event forwarded to the event bus. */
        EVENT
    }

    /**
     * Result produced by the Expression Area.
     *
     * <p>Contains the rendered content, its output type, and optional metadata
     * for the frontend or downstream consumers.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpressionResult {

        /** The rendered content (String for TEXT, Map/Object for CARD/EVENT). */
        private Object content;

        /** The output type determining how the frontend should render this result. */
        private OutputType type;

        /** Additional metadata (source info, rendering hints, etc.). */
        private Map<String, Object> metadata;
    }
}
