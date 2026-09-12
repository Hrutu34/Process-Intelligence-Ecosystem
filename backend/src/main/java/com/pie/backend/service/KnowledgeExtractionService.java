package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class KnowledgeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionService.class);
    private static final String FALLBACK_PROMPT =
            "You are an expert Knowledge Extraction Agent. Extract structured JSON.";

    private final ChatClient chatClient;
    private final ProcessKnowledgeNormalizer normalizer;
    private String cachedPrompt = FALLBACK_PROMPT;

    @Value("classpath:prompts/knowledge-extraction-prompt.txt")
    private Resource extractionPromptResource;

    public KnowledgeExtractionService(ChatClient.Builder chatClientBuilder, ProcessKnowledgeNormalizer normalizer) {
        this.chatClient = chatClientBuilder.build();
        this.normalizer = normalizer;
    }

    @PostConstruct
    void loadPrompt() {
        try {
            if (extractionPromptResource != null && extractionPromptResource.exists()) {
                cachedPrompt = new String(extractionPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
                return;
            }
            Resource defaultRes = new ClassPathResource("prompts/knowledge-extraction-prompt.txt");
            cachedPrompt = new String(defaultRes.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Falling back to inline knowledge extraction prompt: {}", ex.getMessage());
            cachedPrompt = FALLBACK_PROMPT;
        }
    }

    public ProcessKnowledgeDTO extractKnowledge(String documentContent) {
        try {
            String rawResponse = chatClient.prompt()
                    .system(cachedPrompt)
                    .user(documentContent != null ? documentContent : "")
                    .call()
                    .content();

            return normalizer.parseAndNormalize(rawResponse);

        } catch (Exception e) {
            log.error("Knowledge extraction process failed: {}", e.getMessage());
            throw new RuntimeException("Knowledge extraction failed: " + e.getMessage(), e);
        }
    }
}