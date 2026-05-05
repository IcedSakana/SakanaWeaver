package com.agent.core.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rerank Service.
 * Re-scores and re-orders retrieved documents using LLM-based relevance scoring.
 * Takes Top-K documents from retrieval and narrows down to Top-N most relevant ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final ChatClient chatClient;
    private final RagProperties ragProperties;

    private static final String RERANK_PROMPT_TEMPLATE = """
            你是一个文档相关性评分专家。请评估以下文档片段与用户问题的相关性。
            
            用户问题: %s
            
            请对以下每个文档片段打分（0-10分，10分表示完全相关）：
            
            %s
            
            请按以下格式输出（每行一个，仅输出编号和分数）：
            1:8
            2:6
            3:9
            ...以此类推
            """;

    /**
     * Rerank documents by relevance to the query.
     * Uses LLM to score each document, then returns the top-N highest scored.
     *
     * @param query     the original user query
     * @param documents candidate documents from retrieval
     * @return reranked and filtered list of documents (top-N)
     */
    public List<Document> rerank(String query, List<Document> documents) {
        if (!ragProperties.getRerank().isEnabled() || documents.isEmpty()) {
            log.debug("Rerank disabled or no documents, returning as-is");
            return documents;
        }

        int topN = ragProperties.getRerank().getTopN();
        if (documents.size() <= topN) {
            log.debug("Documents count ({}) <= topN ({}), skipping rerank", documents.size(), topN);
            return documents;
        }

        try {
            List<ScoredDocument> scoredDocuments = scoreDocuments(query, documents);

            List<Document> reranked = scoredDocuments.stream()
                    .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                    .limit(topN)
                    .map(ScoredDocument::document)
                    .collect(Collectors.toList());

            log.info("Rerank completed: {} -> {} documents", documents.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("Rerank failed, returning top-N by original order: {}", e.getMessage());
            return documents.subList(0, Math.min(topN, documents.size()));
        }
    }

    /**
     * Score each document using LLM.
     */
    private List<ScoredDocument> scoreDocuments(String query, List<Document> documents) {
        String documentList = buildDocumentList(documents);
        String prompt = String.format(RERANK_PROMPT_TEMPLATE, query, documentList);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return parseScores(response, documents);
    }

    /**
     * Build a numbered list of document snippets for the LLM prompt.
     */
    private String buildDocumentList(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            String content = documents.get(i).getContent();
            String truncated = content.length() > 500 ? content.substring(0, 500) + "..." : content;
            sb.append(String.format("[文档%d]\n%s\n\n", i + 1, truncated));
        }
        return sb.toString();
    }

    /**
     * Parse the LLM scoring response into ScoredDocument records.
     */
    private List<ScoredDocument> parseScores(String response, List<Document> documents) {
        List<ScoredDocument> scored = new ArrayList<>();
        Pattern pattern = Pattern.compile("(\\d+)\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)");

        if (response != null) {
            Matcher matcher = pattern.matcher(response);
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                double score = Double.parseDouble(matcher.group(2));
                if (index >= 0 && index < documents.size()) {
                    scored.add(new ScoredDocument(documents.get(index), score));
                }
            }
        }

        // For any unscored documents, assign a default low score
        if (scored.size() < documents.size()) {
            for (int i = 0; i < documents.size(); i++) {
                int idx = i;
                boolean alreadyScored = scored.stream()
                        .anyMatch(sd -> sd.document().equals(documents.get(idx)));
                if (!alreadyScored) {
                    scored.add(new ScoredDocument(documents.get(i), 0.0));
                }
            }
        }

        return scored;
    }

    /**
     * Internal record for a document with its relevance score.
     */
    private record ScoredDocument(Document document, double score) {}
}
