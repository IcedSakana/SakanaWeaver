package com.agent.core.agent.context;

import com.agent.core.agent.segment.Segment;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builder for constructing User Prompts from a combination of typed {@link Segment}s,
 * Fill-In-Middle (FIM) formatting, and injected knowledge / experience context.
 */
@NoArgsConstructor
public class UserPromptBuilder {

    private List<Segment> historySegments;
    private List<Segment> segments;
    private String knowledgeContext;
    private String experienceContext;

    // FIM fields
    private boolean useFIM = false;
    private String fimPrefix;
    private String fimSuffix;

    /**
     * Set the primary segments that form the user prompt body.
     *
     * @param segments list of typed segments
     * @return this builder
     */
    public UserPromptBuilder fromSegments(List<Segment> segments) {
        this.segments = segments;
        return this;
    }

    /**
     * Prepend history segments before the primary segments.
     *
     * @param history previous conversation segments
     * @return this builder
     */
    public UserPromptBuilder withHistorySegments(List<Segment> history) {
        this.historySegments = history;
        return this;
    }

    /**
     * Enable Fill-In-Middle (FIM) formatting.
     * When FIM is active the output uses the {@code <fim_prefix>}, {@code <fim_suffix>},
     * and {@code <fim_middle>} markers instead of the normal segment layout.
     *
     * @param prefix code/text before the cursor
     * @param suffix code/text after the cursor
     * @return this builder
     */
    public UserPromptBuilder withFIM(String prefix, String suffix) {
        this.useFIM = true;
        this.fimPrefix = prefix;
        this.fimSuffix = suffix;
        return this;
    }

    /**
     * Inject relevant knowledge context (e.g. retrieved documents).
     *
     * @param knowledge knowledge text
     * @return this builder
     */
    public UserPromptBuilder withKnowledgeContext(String knowledge) {
        this.knowledgeContext = knowledge;
        return this;
    }

    /**
     * Inject experience context (e.g. past similar tasks).
     *
     * @param experience experience text
     * @return this builder
     */
    public UserPromptBuilder withExperienceContext(String experience) {
        this.experienceContext = experience;
        return this;
    }

    /**
     * Assemble the final User Prompt string.
     *
     * @return the fully assembled user prompt
     */
    public String build() {
        // --- FIM mode ---
        if (useFIM) {
            return buildFIMPrompt();
        }

        // --- Normal mode ---
        return buildNormalPrompt();
    }

    // ---- private helpers ----

    private String buildFIMPrompt() {
        StringBuilder sb = new StringBuilder();

        appendContextSections(sb);

        sb.append("<fim_prefix>").append(fimPrefix != null ? fimPrefix : "")
                .append("<fim_suffix>").append(fimSuffix != null ? fimSuffix : "")
                .append("<fim_middle>");

        return sb.toString();
    }

    private String buildNormalPrompt() {
        StringBuilder sb = new StringBuilder();

        appendContextSections(sb);

        // History segments
        if (historySegments != null && !historySegments.isEmpty()) {
            sb.append(formatSegments(historySegments));
            sb.append("\n\n");
        }

        // Primary segments
        if (segments != null && !segments.isEmpty()) {
            sb.append(formatSegments(segments));
        }

        return sb.toString().trim();
    }

    private void appendContextSections(StringBuilder sb) {
        if (knowledgeContext != null && !knowledgeContext.isEmpty()) {
            sb.append("[KNOWLEDGE CONTEXT]\n");
            sb.append(knowledgeContext);
            sb.append("\n\n");
        }

        if (experienceContext != null && !experienceContext.isEmpty()) {
            sb.append("[EXPERIENCE CONTEXT]\n");
            sb.append(experienceContext);
            sb.append("\n\n");
        }
    }

    private static String formatSegments(List<Segment> segmentList) {
        return segmentList.stream()
                .map(Segment::toPromptFormat)
                .collect(Collectors.joining("\n"));
    }
}
