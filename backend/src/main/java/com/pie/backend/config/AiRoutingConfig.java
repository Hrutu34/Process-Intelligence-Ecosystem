package com.pie.backend.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
public class AiRoutingConfig {

    // When the 'prod' profile is active, make Gemini the default ChatModel
    @Bean
    @Primary
    @Profile("prod")
    public ChatModel prodChatModel(@Qualifier("googleGenAiChatModel") ChatModel geminiModel) {
        return geminiModel;
    }

    // Local development uses the Ollama model.
    @Bean
    @Primary
    @Profile("local")
    public ChatModel localChatModel(@Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        return ollamaModel;
    }

    // E2E runs against cloud PostgreSQL and the Groq OpenAI-compatible endpoint.
    @Bean
    @Primary
    @Profile("e2e")
    public ChatModel e2eChatModel(@Qualifier("openAiChatModel") ChatModel groqModel) {
        return groqModel;
    }
}