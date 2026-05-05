package com.agent.core.agent.react;

import com.agent.core.agent.BaseAct;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Input preprocessor act.
 * Preprocesses user input before intent recognition:
 * - Text normalization
 * - Context injection (user info, environment)
 * - History context assembly
 */
@Slf4j
public class InputPreprocessor extends BaseAct {

    private Map<String, Object> userContext;

    public InputPreprocessor() {
        super("InputPreprocessor");
        this.userContext = new HashMap<>();
    }

    @Override
    public Object execute(Object input) {
        if (!shouldContinue()) return null;

        String userInput = (String) input;
        log.debug("Preprocessing input: {}", userInput);

        // Build preprocessed input with context
        Map<String, Object> preprocessed = new HashMap<>();
        preprocessed.put("originalInput", userInput);
        preprocessed.put("normalizedInput", normalizeInput(userInput));
        preprocessed.put("userContext", userContext);
        preprocessed.put("timestamp", System.currentTimeMillis());

        return preprocessed;
    }

    /**
     * Normalize user input text.
     */
    private String normalizeInput(String input) {
        if (input == null) return "";
        return input.trim();
    }

    /**
     * Set user context information.
     */
    public void setUserContext(Map<String, Object> context) {
        this.userContext = context != null ? context : new HashMap<>();
    }

    @Override
    public Map<String, Object> dumpContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userContext", userContext);
        return ctx;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadContext(Map<String, Object> context) {
        if (context != null && context.containsKey("userContext")) {
            this.userContext = (Map<String, Object>) context.get("userContext");
        }
    }
}
