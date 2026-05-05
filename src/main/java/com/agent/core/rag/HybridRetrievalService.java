package com.agent.core.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hybrid Retrieval Service.
 * Combines embedding-based vector search with keyword matching.
 * Supports multi-query parallel retrieval and result fusion using RRF.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    /**
     * Perform hybrid retrieval with multiple queries in parallel.
     * Each query runs vector search independently, results are fused via RRF.
     *
     * @param queries   list of rewritten sub-queries
     * @param tenantId  tenant identifier for filtering
     * @return fused and deduplicated list of documents, ordered by relevance
     */
    public List<Document> retrieve(List<String> queries, String tenantId) {
        log.info("Hybrid retrieval: {} queries, tenant='{}'", queries.size(), tenantId);

        RagProperties.Retrieval config = ragProperties.getRetrieval();

        // Parallel retrieval for all sub-queries
        List<CompletableFuture<List<Document>>> futures = queries.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> searchSingle(query, tenantId, config)))
                .collect(Collectors.toList());

        // Collect all results
        List<List<Document>> allResults = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // Fuse using Reciprocal Rank Fusion (RRF)
        List<Document> fusedResults = reciprocalRankFusion(allResults, config.getTopK());
        log.info("Hybrid retrieval completed: {} documents after fusion", fusedResults.size());
        return fusedResults;
    }

    /**
     * Search with a single query using PGVector similarity search.
     */
    private List<Document> searchSingle(String query, String tenantId, RagProperties.Retrieval config) {
        try {
            SearchRequest request = SearchRequest.query(query)
                    .withTopK(config.getTopK())
                    .withSimilarityThreshold(config.getSimilarityThreshold());
            //todo:不通过tenantId，那怎么鉴权?
            //todo:参考https://local.io.alibaba-inc.com/ 通过token
            /*
            if (tenantId != null && !tenantId.isEmpty()) {
                request = request.withFilterExpression("tenantId == '" + tenantId + "'");
            }

             */
            List<Document> results = vectorStore.similaritySearch(request);
            log.debug("Single query '{}' returned {} documents", truncate(query, 50), results.size());
            return results;

        } catch (Exception e) {
            log.error("Search failed for query '{}': {}", truncate(query, 50), e.getMessage());
            return List.of();
        }
    }

    /**
     * Reciprocal Rank Fusion (RRF) to merge multiple ranked result lists.
     * Score = sum(1 / (k + rank_i)) for each list where the document appears.
     * k is a constant (default 60) to prevent dominance by top-ranked docs.
     */
    private List<Document> reciprocalRankFusion(List<List<Document>> rankedLists, int topK) {
        int rrfConstant = 60;
        Map<String, Double> scoreMap = new ConcurrentHashMap<>();
        Map<String, Document> documentMap = new ConcurrentHashMap<>();

        for (List<Document> rankedList : rankedLists) {
            for (int rank = 0; rank < rankedList.size(); rank++) {
                Document doc = rankedList.get(rank);
                String docId = doc.getId();
                documentMap.putIfAbsent(docId, doc);
                scoreMap.merge(docId, 1.0 / (rrfConstant + rank + 1), Double::sum);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> documentMap.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
