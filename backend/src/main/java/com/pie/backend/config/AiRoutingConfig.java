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

    // 'staging' uses Groq (high-end LLM e.g. llama-3.3-70b-versatile via OpenAI-compatible API)
    @Bean
    @Primary
    @Profile("staging")
    public ChatModel stagingChatModel(@Qualifier("openAiChatModel") ChatModel openAiModel) {
        return openAiModel;
    }

    // When NONE of the remote cloud AI profiles are active (local, e2e, test), make Ollama the default
    @Bean
    @Primary
    @Profile("!prod & !prod-h2 & !staging")
    public ChatModel localChatModel(@Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        return ollamaModel;
    }
}