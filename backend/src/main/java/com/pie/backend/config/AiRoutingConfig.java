package com.pie.backend.config;

import com.pie.backend.service.VwLlmaasService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
public class AiRoutingConfig {

    // When the real 'prod' profile (Postgres) is active, make Gemini the default ChatModel
    @Bean
    @Primary
    @Profile("prod")
    public ChatModel prodChatModel(@Qualifier("googleGenAiChatModel") ChatModel geminiModel) {
        return geminiModel;
    }

    // 'prod-h2' uses VW Group LLMaaS (gpt-4o via Cloud IDP OAuth2) as the default ChatModel
    @Bean
    @Primary
    @Profile("prod-h2")
    public ChatModel prodH2ChatModel(VwLlmaasService vwLlmaasService) {
        return new VwLlmaasChatModel(vwLlmaasService);
    }

    // When NEITHER Gemini-backed nor LLMaaS-backed profile is active (local, e2e), make Ollama the default
    @Bean
    @Primary
    @Profile("!prod & !prod-h2")
    public ChatModel localChatModel(@Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        return ollamaModel;
    }
}