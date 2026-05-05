package com.agent.core.agent.toolkit;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for all {@link ToolkitTemplate} instances.
 * <p>
 * Toolkits are registered by name and can be looked up individually or rendered
 * collectively as Python stubs for inclusion in the system prompt.
 */
@Component
public class ToolkitRegistry {

    private final Map<String, ToolkitTemplate> toolkits = new ConcurrentHashMap<>();

    /**
     * Register a toolkit template. If a template with the same name already exists
     * it will be replaced.
     *
     * @param template the toolkit template to register
     */
    public void register(ToolkitTemplate template) {
        if (template == null || template.getName() == null) {
            throw new IllegalArgumentException("ToolkitTemplate and its name must not be null");
        }
        toolkits.put(template.getName(), template);
    }

    /**
     * Remove a toolkit from the registry.
     *
     * @param name toolkit name
     * @return the removed template, or {@code null} if not found
     */
    public ToolkitTemplate unregister(String name) {
        return toolkits.remove(name);
    }

    /**
     * Get all registered toolkit templates.
     *
     * @return unmodifiable collection of all templates
     */
    public Collection<ToolkitTemplate> getAll() {
        return Collections.unmodifiableCollection(toolkits.values());
    }

    /**
     * Look up a toolkit by name.
     *
     * @param name toolkit name
     * @return the template, or {@code null} if not found
     */
    public ToolkitTemplate getByName(String name) {
        return toolkits.get(name);
    }

    /**
     * Generate Python class stubs for <em>all</em> registered toolkits and concatenate
     * them into a single string.
     *
     * @return combined Python source containing all toolkit stubs
     */
    public String generateAllPythonStubs() {
        return toolkits.values().stream()
                .map(ToolkitTemplate::toPythonClass)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Format all registered toolkits as a section suitable for embedding in a system
     * prompt.  Each toolkit's Python stub is wrapped in a fenced code block.
     *
     * @return formatted system prompt section
     */
    public String toSystemPromptSection() {
        if (toolkits.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("The following toolkits are available for use:\n\n");

        for (ToolkitTemplate template : toolkits.values()) {
            sb.append("```python\n");
            sb.append(template.toPythonClass());
            sb.append("```\n\n");
        }

        return sb.toString().trim();
    }
}
