package com.agent.core.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the RAG pipeline.
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** Retrieval mode: "mcp" uses L.O.C.A.L MCP API, "pgvector" uses local PGVector. */
    private String mode = "mcp";

    private Mcp mcp = new Mcp();
    private QueryRewrite queryRewrite = new QueryRewrite();
    private Retrieval retrieval = new Retrieval();
    private Rerank rerank = new Rerank();
    private Ingestion ingestion = new Ingestion();

    @Data
    public static class Mcp {
        /** L.O.C.A.L MCP Server endpoint (e.g. https://local.io.alibaba-inc.com/api/mcp). */
        private String endpoint = "";
        /** Authentication token for L.O.C.A.L MCP Server. */
        private String token = "";
        /** Knowledge repo IDs to search. Corresponds to the L.O.C.A.L knowledge base IDs. */
        private List<Integer> repoIds = new ArrayList<>();
        /** Knowledge group IDs to search (optional, alternative to repoIds). */
        private List<Integer> groupIds = new ArrayList<>();
        /** Search mode: preciseRetrieval or retrievalUserKnowledge. */
        private String searchMode = "preciseRetrieval";
        /** Number of top results to return from MCP search. */
        private int topK = 20;
        /** Rerank score threshold (0.0 means no filtering). */
        private double scoreThreshold = 0.0;
        /** Whether to request MCP to generate a summary of search results. */
        private boolean summary = false;
        /** Connect timeout in seconds. */
        private int connectTimeoutSeconds = 30;
        /** Read timeout in seconds. */
        private int readTimeoutSeconds = 60;
    }

    @Data
    public static class QueryRewrite {
        /** Whether query rewriting is enabled. */
        private boolean enabled = true;
        /** Maximum number of sub-questions to generate from the original query. */
        private int maxSubQuestions = 5;
    }

    @Data
    public static class Retrieval {
        /** Number of documents returned by hybrid search per sub-query. */
        private int topK = 10;
        /** Minimum similarity threshold for vector search. */
        private double similarityThreshold = 0.5;
        /** Weight of keyword search in hybrid scoring (embedding weight = 1 - keywordWeight). */
        private double keywordWeight = 0.3;
    }

    @Data
    public static class Rerank {
        /** Whether reranking is enabled. */
        private boolean enabled = true;
        /** Final number of documents after reranking. */
        private int topN = 5;
    }

    @Data
    public static class Ingestion {
        /** Chunk size in tokens. */
        private int chunkSize = 800;
        /** Overlap between chunks in tokens. */
        private int chunkOverlap = 200;
    }
}
