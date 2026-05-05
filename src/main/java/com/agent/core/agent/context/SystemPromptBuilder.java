package com.agent.core.agent.context;

import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builder for constructing configurable System Prompts with clearly delimited sections.
 * <p>
 * Each section is optional; only sections that have been set will appear in the final
 * prompt.  Custom sections can be added via {@link #customSection(String, String)}.
 */
@NoArgsConstructor
public class SystemPromptBuilder {

    private String platformIntro;
    private String roleDefinition;
    private String workMechanism;
    private String inputFormat;
    private String outputFormat;
    private List<String> availableTools;
    private List<String> principles;
    private List<String> experiences;
    private final Map<String, String> customSections = new LinkedHashMap<>();

    // ---- fluent setters ----

    /**
     * Set the platform introduction section.
     */
    public SystemPromptBuilder platformIntro(String platformIntro) {
        this.platformIntro = platformIntro;
        return this;
    }

    /**
     * Define the agent's role.
     */
    public SystemPromptBuilder roleDefinition(String roleDefinition) {
        this.roleDefinition = roleDefinition;
        return this;
    }

    /**
     * Describe how the agent works internally.
     */
    public SystemPromptBuilder workMechanism(String workMechanism) {
        this.workMechanism = workMechanism;
        return this;
    }

    /**
     * Specify the expected input format.
     */
    public SystemPromptBuilder inputFormat(String inputFormat) {
        this.inputFormat = inputFormat;
        return this;
    }

    /**
     * Specify the expected output format (e.g. Python code block).
     */
    public SystemPromptBuilder outputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
        return this;
    }

    /**
     * Provide available tool descriptions (each formatted as a Python class stub).
     */
    public SystemPromptBuilder availableTools(List<String> toolDescriptions) {
        this.availableTools = toolDescriptions;
        return this;
    }

    /**
     * Important principles / rules the agent must follow.
     */
    public SystemPromptBuilder principles(List<String> principles) {
        this.principles = principles;
        return this;
    }

    /**
     * Past usage experiences / few-shot examples.
     */
    public SystemPromptBuilder experiences(List<String> experiences) {
        this.experiences = experiences;
        return this;
    }

    /**
     * Add a custom named section.
     *
     * @param name    section title
     * @param content section body
     */
    public SystemPromptBuilder customSection(String name, String content) {
        this.customSections.put(name, content);
        return this;
    }

    // ---- build ----

    /**
     * Assemble all configured sections into the final System Prompt string.
     * Sections are separated by clear markers for readability.
     *
     * @return the fully assembled system prompt
     */
    public String build() {
        List<String> parts = new ArrayList<>();

        if (platformIntro != null && !platformIntro.isEmpty()) {
            parts.add(section("PLATFORM INTRODUCTION", platformIntro));
        }

        if (roleDefinition != null && !roleDefinition.isEmpty()) {
            parts.add(section("ROLE DEFINITION", roleDefinition));
        }

        if (workMechanism != null && !workMechanism.isEmpty()) {
            parts.add(section("WORK MECHANISM", workMechanism));
        }

        if (inputFormat != null && !inputFormat.isEmpty()) {
            parts.add(section("INPUT FORMAT", inputFormat));
        }

        if (outputFormat != null && !outputFormat.isEmpty()) {
            parts.add(section("OUTPUT FORMAT", outputFormat));
        }

        if (availableTools != null && !availableTools.isEmpty()) {
            String toolsBody = availableTools.stream()
                    .map(tool -> "```python\n" + tool + "\n```")
                    .collect(Collectors.joining("\n\n"));
            parts.add(section("AVAILABLE TOOLS", toolsBody));
        }

        if (principles != null && !principles.isEmpty()) {
            String principlesBody = formatNumberedList(principles);
            parts.add(section("PRINCIPLES", principlesBody));
        }

        if (experiences != null && !experiences.isEmpty()) {
            String experiencesBody = formatNumberedList(experiences);
            parts.add(section("EXPERIENCES", experiencesBody));
        }

        for (Map.Entry<String, String> entry : customSections.entrySet()) {
            parts.add(section(entry.getKey().toUpperCase(), entry.getValue()));
        }

        return String.join("\n\n", parts);
    }

    // ---- helpers ----

    private static String section(String title, String body) {
        return "=== " + title + " ===\n" + body;
    }

    private static String formatNumberedList(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append(i + 1).append(". ").append(items.get(i));
            if (i < items.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
