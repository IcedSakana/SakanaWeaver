package com.agent.core.rag;

import com.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MCP-based Knowledge Retrieval Service.
 * Calls L.O.C.A.L MCP Server's searchDocChunk API to retrieve relevant document chunks
 * from Yuque/DingTalk knowledge bases that have already been indexed and vectorized.
 *
 * This replaces the need for local PGVector ingestion and retrieval when using MCP mode.
 */
@Slf4j
@Service
public class McpKnowledgeService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private final RagProperties ragProperties;
    private final OkHttpClient httpClient;

    public McpKnowledgeService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        RagProperties.Mcp mcpConfig = ragProperties.getMcp();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(mcpConfig.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(mcpConfig.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Search document chunks from L.O.C.A.L MCP knowledge base.
     *
     * @param query  the search query
     * @param topK   number of top results to return
     * @return list of retrieved document chunks, each as a map with content and metadata
     */
    public List<McpDocChunk> searchDocChunks(String query, int topK) {
        RagProperties.Mcp mcpConfig = ragProperties.getMcp();
        log.info("MCP searchDocChunk: query='{}', topK={}, repoIds={}", truncate(query, 50), topK, mcpConfig.getRepoIds());

        Map<String, Object> toolParams = buildSearchParams(query, topK, mcpConfig);
        Map<String, Object> mcpRequest = buildJsonRpcRequest("tools/call", Map.of(
                "name", "searchDocChunk",
                "arguments", toolParams
        ));

        try {
            Map<String, Object> response = sendRequest(mcpRequest);
            return parseSearchResults(response);
        } catch (Exception e) {
            log.error("MCP searchDocChunk failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Search with multiple queries in parallel and merge results.
     * Deduplicates by document chunk ID.
     *
     * @param queries  list of rewritten sub-queries
     * @param topK     number of top results per query
     * @return merged and deduplicated list of document chunks
     */
    public List<McpDocChunk> searchWithMultipleQueries(List<String> queries, int topK) {
        Map<String, McpDocChunk> mergedResults = new HashMap<>();

        for (String query : queries) {
            List<McpDocChunk> results = searchDocChunks(query, topK);
            for (McpDocChunk chunk : results) {
                mergedResults.putIfAbsent(chunk.getChunkId(), chunk);
            }
        }

        List<McpDocChunk> finalResults = new ArrayList<>(mergedResults.values());
        log.info("MCP multi-query search: {} queries -> {} unique chunks", queries.size(), finalResults.size());
        return finalResults;
    }

    /**
     * Build the search parameters for searchDocChunk tool call.
     */
    private Map<String, Object> buildSearchParams(String query, int topK, RagProperties.Mcp mcpConfig) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("topK", topK);
        params.put("mode", mcpConfig.getSearchMode());
        params.put("scoreThreshold", mcpConfig.getScoreThreshold());
        params.put("summary", mcpConfig.isSummary());

        if (!mcpConfig.getRepoIds().isEmpty()) {
            params.put("repoIds", mcpConfig.getRepoIds());
        }
        if (!mcpConfig.getGroupIds().isEmpty()) {
            params.put("groupIds", mcpConfig.getGroupIds());
        }

        return params;
    }

    /**
     * Build a JSON-RPC 2.0 request envelope.
     */
    private Map<String, Object> buildJsonRpcRequest(String method, Map<String, Object> params) {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", System.currentTimeMillis());
        request.put("method", method);
        request.put("params", params);
        return request;
    }

    /**
     * Send a JSON-RPC request to the MCP Server.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendRequest(Map<String, Object> requestBody) {
        RagProperties.Mcp mcpConfig = ragProperties.getMcp();
        String json = JsonUtils.toJson(requestBody);

        Request.Builder builder = new Request.Builder()
                .url(mcpConfig.getEndpoint())
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json");

        if (mcpConfig.getToken() != null && !mcpConfig.getToken().isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + mcpConfig.getToken());
        }

        Request request = builder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("MCP request failed with status " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            return JsonUtils.fromJson(body, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("MCP request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse the MCP searchDocChunk response into a list of McpDocChunk.
     */
    @SuppressWarnings("unchecked")
    private List<McpDocChunk> parseSearchResults(Map<String, Object> response) {
        List<McpDocChunk> chunks = new ArrayList<>();

        Object result = response.get("result");
        if (result == null) {
            log.warn("MCP response has no result field");
            return chunks;
        }

        // MCP tools/call result typically has a "content" field with the tool output
        List<Map<String, Object>> contentList;
        if (result instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) result;
            Object content = resultMap.get("content");
            if (content instanceof List) {
                contentList = (List<Map<String, Object>>) content;
            } else {
                // Try to parse the result directly as document list
                contentList = extractDocumentsFromResult(resultMap);
            }
        } else if (result instanceof List) {
            contentList = (List<Map<String, Object>>) result;
        } else {
            log.warn("Unexpected MCP result type: {}", result.getClass().getSimpleName());
            return chunks;
        }

        for (Map<String, Object> item : contentList) {
            McpDocChunk chunk = parseChunkItem(item);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }

        log.debug("Parsed {} document chunks from MCP response", chunks.size());
        return chunks;
    }

    /**
     * Try to extract a list of document items from a result map.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDocumentsFromResult(Map<String, Object> resultMap) {
        // The searchDocChunk tool may return content as text with embedded JSON
        for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
            if (entry.getValue() instanceof List) {
                return (List<Map<String, Object>>) entry.getValue();
            }
        }

        // If result itself looks like a single text content, try to parse the text
        Object textContent = resultMap.get("text");
        if (textContent instanceof String) {
            try {
                Object parsed = JsonUtils.fromJson((String) textContent, List.class);
                if (parsed instanceof List) {
                    return (List<Map<String, Object>>) parsed;
                }
            } catch (Exception ignored) {
                // text is not JSON, treat the whole result as a single chunk
                List<Map<String, Object>> singleResult = new ArrayList<>();
                singleResult.add(resultMap);
                return singleResult;
            }
        }

        return List.of();
    }

    /**
     * Parse a single item from the MCP response into McpDocChunk.
     */
    private McpDocChunk parseChunkItem(Map<String, Object> item) {
        try {
            String content = getStringValue(item, "text", getStringValue(item, "content", ""));
            if (content.isEmpty()) {
                return null;
            }

            String chunkId = getStringValue(item, "id", getStringValue(item, "chunkId", String.valueOf(content.hashCode())));
            String title = getStringValue(item, "title", "");
            String source = getStringValue(item, "source", getStringValue(item, "url", "MCP知识库"));
            double score = getDoubleValue(item, "score", getDoubleValue(item, "relevanceScore", 0.0));

            return McpDocChunk.builder()
                    .chunkId(chunkId)
                    .title(title)
                    .content(content)
                    .source(source)
                    .score(score)
                    .rawMetadata(item)
                    .build();
        } catch (Exception e) {
            log.debug("Failed to parse chunk item: {}", e.getMessage());
            return null;
        }
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * Data class representing a document chunk retrieved from MCP.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class McpDocChunk {
        private String chunkId;
        private String title;
        private String content;
        private String source;
        private double score;
        private Map<String, Object> rawMetadata;
    }
}
