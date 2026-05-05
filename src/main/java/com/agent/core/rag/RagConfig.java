package com.agent.core.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for RAG pipeline beans.
 */
@Configuration
public class RagConfig {

    /**
     * Create a ChatClient from the auto-configured ChatModel (OpenAI-compatible).
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
