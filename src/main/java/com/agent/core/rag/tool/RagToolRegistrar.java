package com.agent.core.rag.tool;

import com.agent.core.agent.code.Py4jGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Automatically registers RAG-related toolkit handlers with the Py4jGateway
 * when the application starts up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagToolRegistrar {

    private final Py4jGateway py4jGateway;
    private final KnowledgeSearchTool knowledgeSearchTool;

    /**
     * Register the KnowledgeSearchTool with Py4jGateway on application startup.
     * This makes the RAG knowledge base available as a toolkit for LLM-generated Python code.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerRagTools() {
        py4jGateway.registerHandler(
                knowledgeSearchTool.getToolkitName(),
                knowledgeSearchTool
        );
        log.info("Registered RAG tool '{}' with Py4jGateway", knowledgeSearchTool.getToolkitName());
    }
}
