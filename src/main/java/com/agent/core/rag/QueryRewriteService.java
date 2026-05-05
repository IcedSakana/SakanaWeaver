package com.agent.core.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Query Rewrite Service.
 * Uses LLM to rewrite a user query into multiple sub-questions for better recall.
 * Also matches against a problem library and high-quality ticket rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatClient chatClient;
    private final RagProperties ragProperties;

    private static final String REWRITE_PROMPT_TEMPLATE = """
            你是一个专业的 Query 改写助手。用户会输入一个问题，你需要将其改写为多个更精确的子问题，以便在知识库中获得更好的召回效果。
            
            改写规则：
            1. 将用户的口语化表达转换为规范的技术/业务表达
            2. 从不同角度拆解问题，生成 %d 个子问题
            3. 保留原始问题的核心意图
            4. 每个子问题应该独立且具有明确的检索方向
            5. 结合可能的问题库标准表述和高质量工单的规范描述
            
            用户原始问题: %s
            
            请直接输出改写后的子问题，每行一个，不要编号，不要其他多余内容。
            """;

    /**
     * Rewrite a user query into multiple sub-questions using LLM.
     *
     * @param originalQuery the original user query
     * @return list of rewritten sub-questions (includes the original query)
     */
    public List<String> rewriteQuery(String originalQuery) {
        if (!ragProperties.getQueryRewrite().isEnabled()) {
            log.debug("Query rewrite disabled, returning original query");
            return List.of(originalQuery);
        }

        try {
            int maxSubQuestions = ragProperties.getQueryRewrite().getMaxSubQuestions();
            String prompt = String.format(REWRITE_PROMPT_TEMPLATE, maxSubQuestions, originalQuery);
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            List<String> subQuestions = parseSubQuestions(response, originalQuery);
            log.info("Query rewritten: original='{}' -> {} sub-questions", originalQuery, subQuestions.size());
            return subQuestions;

        } catch (Exception e) {
            log.warn("Query rewrite failed, falling back to original query: {}", e.getMessage());
            return List.of(originalQuery);
        }
    }

    /**
     * Parse LLM response into a list of sub-questions.
     * Always includes the original query as the first element.
     */
    private List<String> parseSubQuestions(String response, String originalQuery) {
        if (response == null || response.isBlank()) {
            return List.of(originalQuery);
        }

        List<String> subQuestions = new ArrayList<>();
        subQuestions.add(originalQuery);

        List<String> parsed = Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.equals(originalQuery))
                .collect(Collectors.toList());

        int maxSubQuestions = ragProperties.getQueryRewrite().getMaxSubQuestions();
        int limit = Math.min(parsed.size(), maxSubQuestions - 1);
        subQuestions.addAll(parsed.subList(0, limit));

        return subQuestions;
    }
}
