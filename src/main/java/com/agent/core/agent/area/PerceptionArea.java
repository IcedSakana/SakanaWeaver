package com.agent.core.agent.area;

import com.agent.core.agent.BaseAct;
import com.agent.model.event.Event;
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
 * Perception Area -- the sensory cortex of the agent.
 *
 * <p>Responsible for receiving and normalizing all external information before
 * it reaches the cognitive pipeline. Input sources include user messages,
 * card/button interaction events, sub-agent result callbacks, and system
 * environment signals.</p>
 *
 * <h3>Registered Acts</h3>
 * <ul>
 *   <li><b>LanguageDetection</b> -- detect the language of the incoming text</li>
 *   <li><b>FuzzyParsing</b> -- normalize typos, abbreviations, and ambiguous input</li>
 *   <li><b>SceneAnalysis</b> -- classify the interaction scene / domain</li>
 *   <li><b>QueryEnhancement</b> -- rewrite / expand the query for downstream use</li>
 *   <li><b>EnvironmentSensing</b> -- parse environment context data (device, timezone, etc.)</li>
 *   <li><b>AttachmentParsing</b> -- extract structured info from attachments (files, images)</li>
 * </ul>
 *
 * <p>The main entry point {@link #perceive(Event)} runs all perception acts
 * in sequence and assembles a {@link PerceptionResult}.</p>
 *
 * @author agent-framework
 */
@Slf4j
public class PerceptionArea extends BaseArea {

    private static final String AREA_NAME = "PerceptionArea";

    /** History of perceived inputs for contextual reference. */
    private final List<String> perceptionHistory = new ArrayList<>();

    public PerceptionArea() {
        // Sub-classes or the orchestrator may register concrete Act implementations
        // via registerAct(). The acts listed here are logical placeholders that
        // document the expected pipeline; concrete implementations are injected
        // at agent construction time.
    }

    // -------------------------------------------------
    // Main entry point
    // -------------------------------------------------

    /**
     * Perceive an incoming {@link Event}.
     *
     * <p>Runs every registered perception act in registration order and
     * aggregates the results into a single {@link PerceptionResult}.</p>
     *
     * @param event the incoming event (user message, card event, sub-agent result, etc.)
     * @return a fully populated {@link PerceptionResult}, or {@code null} if
     *         the area has been signalled to stop
     */
    public PerceptionResult perceive(Event event) {
        if (!shouldContinue()) {
            log.debug("{}: skipped -- sleep/stop signal received", AREA_NAME);
            return null;
        }

        log.info("{}: perceiving event type={}, source={}",
                AREA_NAME,
                event.getEventType(),
                event.getEventSource());

        String originalInput = extractTextFromEvent(event);

        PerceptionResult result = PerceptionResult.builder()
                .originalInput(originalInput)
                .normalizedInput(originalInput)
                .build();

        // --- Run each perception act in pipeline order ---

        // 1. Language detection
        BaseAct languageDetection = getAct("LanguageDetection");
        if (languageDetection != null && shouldContinue()) {
            try {
                Object langResult = languageDetection.execute(originalInput);
                if (langResult instanceof String) {
                    result.setDetectedLanguage((String) langResult);
                }
            } catch (Exception e) {
                log.warn("{}: LanguageDetection act failed", AREA_NAME, e);
                result.setDetectedLanguage("unknown");
            }
        }

        // 2. Fuzzy parsing -- normalizes the raw input
        BaseAct fuzzyParsing = getAct("FuzzyParsing");
        if (fuzzyParsing != null && shouldContinue()) {
            try {
                Object normalized = fuzzyParsing.execute(originalInput);
                if (normalized instanceof String) {
                    result.setNormalizedInput((String) normalized);
                }
            } catch (Exception e) {
                log.warn("{}: FuzzyParsing act failed, using original input", AREA_NAME, e);
            }
        }

        // 3. Scene analysis
        BaseAct sceneAnalysis = getAct("SceneAnalysis");
        if (sceneAnalysis != null && shouldContinue()) {
            try {
                Object scene = sceneAnalysis.execute(result.getNormalizedInput());
                if (scene instanceof String) {
                    result.setScene((String) scene);
                }
            } catch (Exception e) {
                log.warn("{}: SceneAnalysis act failed", AREA_NAME, e);
            }
        }

        // 4. Query enhancement
        BaseAct queryEnhancement = getAct("QueryEnhancement");
        if (queryEnhancement != null && shouldContinue()) {
            try {
                Object enhanced = queryEnhancement.execute(result.getNormalizedInput());
                if (enhanced instanceof String) {
                    result.setEnhancedQuery((String) enhanced);
                }
            } catch (Exception e) {
                log.warn("{}: QueryEnhancement act failed, using normalized input", AREA_NAME, e);
                result.setEnhancedQuery(result.getNormalizedInput());
            }
        }

        // 5. Environment sensing data parsing
        BaseAct environmentSensing = getAct("EnvironmentSensing");
        if (environmentSensing != null && shouldContinue()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> envCtx = (Map<String, Object>) environmentSensing.execute(event);
                result.setEnvironmentContext(envCtx);
            } catch (Exception e) {
                log.warn("{}: EnvironmentSensing act failed", AREA_NAME, e);
            }
        }

        // 6. Attachment data parsing
        BaseAct attachmentParsing = getAct("AttachmentParsing");
        if (attachmentParsing != null && shouldContinue()) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> attachments =
                        (List<Map<String, Object>>) attachmentParsing.execute(event);
                result.setAttachments(attachments);
            } catch (Exception e) {
                log.warn("{}: AttachmentParsing act failed", AREA_NAME, e);
            }
        }

        // Record history
        perceptionHistory.add(originalInput);

        log.info("{}: perception complete -- language={}, scene={}, enhanced={}",
                AREA_NAME,
                result.getDetectedLanguage(),
                result.getScene(),
                result.getEnhancedQuery() != null
                        ? result.getEnhancedQuery().substring(0, Math.min(result.getEnhancedQuery().length(), 80))
                        : null);

        return result;
    }

    // -------------------------------------------------
    // BaseArea contract
    // -------------------------------------------------

    @Override
    public Object execute(Object input) {
        if (input instanceof Event) {
            return perceive((Event) input);
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
    // Persistence
    // -------------------------------------------------

    @Override
    public Map<String, Object> dumpContext() {
        Map<String, Object> context = super.dumpContext();
        context.put("perceptionHistory", new ArrayList<>(perceptionHistory));
        return context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadContext(Map<String, Object> context) {
        super.loadContext(context);
        if (context != null && context.containsKey("perceptionHistory")) {
            perceptionHistory.clear();
            Object hist = context.get("perceptionHistory");
            if (hist instanceof List) {
                perceptionHistory.addAll((List<String>) hist);
            }
        }
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    /**
     * Extract raw text content from an Event's first artifact part.
     */
    private String extractTextFromEvent(Event event) {
        if (event.getArtifact() != null
                && event.getArtifact().getParts() != null
                && !event.getArtifact().getParts().isEmpty()) {
            Object data = event.getArtifact().getParts().get(0).getData();
            return data != null ? data.toString() : "";
        }
        return "";
    }

    // =================================================
    // Inner result class
    // =================================================

    /**
     * Aggregated result produced by the Perception Area.
     *
     * <p>Contains the original input, normalized form, detected language,
     * scene classification, enhanced query, environment context, and any
     * parsed attachments.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerceptionResult {

        /** The raw, unmodified input text. */
        private String originalInput;

        /** Input after fuzzy-parsing / normalization. */
        private String normalizedInput;

        /** ISO 639-1 language code detected from the input (e.g., "en", "zh"). */
        private String detectedLanguage;

        /** Classified interaction scene or domain (e.g., "customer_support", "code_review"). */
        private String scene;

        /** Rewritten or expanded query for downstream areas. */
        private String enhancedQuery;

        /** Environment context data (device info, timezone, user preferences, etc.). */
        private Map<String, Object> environmentContext;

        /** Parsed attachment data list (file metadata, image descriptions, etc.). */
        private List<Map<String, Object>> attachments;
    }
}
