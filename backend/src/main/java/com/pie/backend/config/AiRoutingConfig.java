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

    // When ANY profile OTHER than 'prod' is active (local, e2e), make Ollama the default
    @Bean
    @Primary
    @Profile("!prod")
    public ChatModel localChatModel(@Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        return ollamaModel;
    }
}