package com.agent.core.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * RAG Pipeline Orchestrator.
 *
 * Orchestrates the full RAG pipeline:
 * <ol>
 *   <li>Query Rewrite — LLM rewrites user query into multiple sub-questions</li>
 *   <li>Multi-path Recall — parallel retrieval with all sub-queries</li>
 *   <li>Hybrid Search — embedding + keyword search via PGVector, returns top-10</li>
 *   <li>Rerank — LLM-based re-scoring, narrows to top-5 with re-ordering</li>
 *   <li>Prompt Assembly — combines retrieved context into a structured prompt</li>
 *   <li>LLM Summarization — generates the final answer</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final QueryRewriteService queryRewriteService;
    private final HybridRetrievalService hybridRetrievalService;
    private final RerankService rerankService;
    private final McpKnowledgeService mcpKnowledgeService;
    private final RagProperties ragProperties;
    private final ChatClient chatClient;

    private static final String RAG_ANSWER_PROMPT_TEMPLATE = """
            你是一个专业的知识库问答助手。请根据以下检索到的参考资料，回答用户的问题。
            
            回答规则：
            1. 只基于提供的参考资料回答，不要编造信息
            2. 如果参考资料不足以回答问题，请明确说明
            3. 回答要清晰、结构化，必要时使用列表或分步骤说明
            4. 在回答末尾标注引用了哪些参考资料（用来源标识）
            
            参考资料：
            %s
            
            用户问题：%s
            
            请回答：
            """;

    /**
     * Execute the full RAG pipeline and return the final answer.
     *
     * @param query    the original user query
     * @param tenantId tenant identifier for retrieval filtering
     * @return the LLM-generated answer based on retrieved context
     */
    public RagResult query(String query, String tenantId) {
        log.info("RAG pipeline started: query='{}', tenant='{}'", query, tenantId);
        long startTime = System.currentTimeMillis();

        // Step 1: Query Rewrite
        List<String> subQueries = queryRewriteService.rewriteQuery(query);
        log.info("Step 1 - Query rewrite: {} sub-queries generated", subQueries.size());

        String knowledgeContext;
        int retrievedCount;
        int rerankedCount;

        if (isMcpMode()) {
            // MCP mode: L.O.C.A.L MCP handles vector search + rerank internally
            List<McpKnowledgeService.McpDocChunk> mcpChunks =
                    mcpKnowledgeService.searchWithMultipleQueries(subQueries, ragProperties.getMcp().getTopK());
            retrievedCount = mcpChunks.size();
            rerankedCount = mcpChunks.size();
            knowledgeContext = assembleMcpContext(mcpChunks);
            log.info("Step 2-4 (MCP) - Retrieved {} chunks from L.O.C.A.L", mcpChunks.size());
        } else {
            // PGVector mode: local hybrid retrieval + LLM rerank
            List<Document> retrievedDocuments = hybridRetrievalService.retrieve(subQueries, tenantId);
            log.info("Step 2&3 - Hybrid retrieval: {} documents retrieved", retrievedDocuments.size());

            List<Document> rerankedDocuments = rerankService.rerank(query, retrievedDocuments);
            log.info("Step 4 - Rerank: {} documents after reranking", rerankedDocuments.size());

            retrievedCount = retrievedDocuments.size();
            rerankedCount = rerankedDocuments.size();
            knowledgeContext = assembleContext(rerankedDocuments);
        }

        // Step 6: LLM Summarization
        String answer = generateAnswer(query, knowledgeContext);

        long duration = System.currentTimeMillis() - startTime;
        log.info("RAG pipeline completed in {}ms", duration);

        return RagResult.builder()
                .query(query)
                .subQueries(subQueries)
                .retrievedCount(retrievedCount)
                .rerankedCount(rerankedCount)
                .context(knowledgeContext)
                .answer(answer)
                .durationMs(duration)
                .build();
    }

    /**
     * Retrieve context only (without LLM summarization).
     * Useful for injecting into PromptAssembler's knowledgeContext.
     *
     * @param query    the original user query
     * @param tenantId tenant identifier
     * @return assembled knowledge context string
     */
    public String retrieveContext(String query, String tenantId) {
        List<String> subQueries = queryRewriteService.rewriteQuery(query);

        if (isMcpMode()) {
            List<McpKnowledgeService.McpDocChunk> mcpChunks =
                    mcpKnowledgeService.searchWithMultipleQueries(subQueries, ragProperties.getMcp().getTopK());
            return assembleMcpContext(mcpChunks);
        }

        List<Document> retrievedDocuments = hybridRetrievalService.retrieve(subQueries, tenantId);
        List<Document> rerankedDocuments = rerankService.rerank(query, retrievedDocuments);
        return assembleContext(rerankedDocuments);
    }

    /**
     * Assemble retrieved documents into a structured context string.
     */
    private String assembleContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "未找到相关参考资料。";
        }

        return documents.stream()
                .map(doc -> {
                    String source = doc.getMetadata().getOrDefault("source", "未知来源").toString();
                    String category = doc.getMetadata().getOrDefault("category", "").toString();
                    String header = category.isEmpty()
                            ? String.format("[来源: %s]", source)
                            : String.format("[来源: %s | 分类: %s]", source, category);
                    return header + "\n" + doc.getContent();
                })
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * Assemble MCP document chunks into a structured context string.
     */
    private String assembleMcpContext(List<McpKnowledgeService.McpDocChunk> chunks) {
        if (chunks.isEmpty()) {
            return "未找到相关参考资料。";
        }

        return IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    McpKnowledgeService.McpDocChunk chunk = chunks.get(i);
                    String header = chunk.getTitle() != null && !chunk.getTitle().isEmpty()
                            ? String.format("[来源: %s | 标题: %s]", chunk.getSource(), chunk.getTitle())
                            : String.format("[来源: %s]", chunk.getSource());
                    return header + "\n" + chunk.getContent();
                })
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * Check if the current retrieval mode is MCP.
     */
    private boolean isMcpMode() {
        return "mcp".equalsIgnoreCase(ragProperties.getMode());
    }

    /**
     * Generate the final answer using LLM with the assembled context.
     */
    private String generateAnswer(String query, String knowledgeContext) {
        try {
            String prompt = String.format(RAG_ANSWER_PROMPT_TEMPLATE, knowledgeContext, query);

            String answer = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return answer != null ? answer : "抱歉，无法生成回答。";

        } catch (Exception e) {
            log.error("LLM summarization failed: {}", e.getMessage(), e);
            return "抱歉，生成回答时出现错误：" + e.getMessage();
        }
    }

    /**
     * RAG pipeline result containing the full execution trace.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagResult {
        private String query;
        private List<String> subQueries;
        private int retrievedCount;
        private int rerankedCount;
        private String context;
        private String answer;
        private long durationMs;
    }
}
