package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.shared.dto.ClassificationResultDTO;
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
public class AiClassificationService implements ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AiClassificationService.class);
    private static final String FALLBACK_PROMPT = "Classify the input document into a category with confidence.";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String cachedPrompt = FALLBACK_PROMPT;

    @Value("classpath:prompts/document-classification-prompt.txt")
    private Resource classificationPromptResource;

    public AiClassificationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostConstruct
    void loadPrompt() {
        try {
            if (classificationPromptResource != null && classificationPromptResource.exists()) {
                cachedPrompt = new String(classificationPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
                return;
            }
            Resource defaultRes = new ClassPathResource("prompts/document-classification-prompt.txt");
            cachedPrompt = new String(defaultRes.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Falling back to inline classification prompt: {}", ex.getMessage());
            cachedPrompt = FALLBACK_PROMPT;
        }
    }

    @Override
    public ClassificationResultDTO classifyDocument(String content) {
        try {
            // Use only the first 2,000 characters for classification to prevent context exhaustion
            String snippet = (content != null && content.length() > 2000)
                    ? content.substring(0, 2000)
                    : content;

            String rawResponse = chatClient.prompt()
                    .system(cachedPrompt)
                    .user(snippet != null ? snippet : "")
                    .call()
                    .content();

            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("Model returned empty classification response, defaulting to Unknown");
                return new ClassificationResultDTO("Unknown", 0);
            }

            // Strip markdown formatting if present
            String cleanJson = rawResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            } else if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            return objectMapper.readValue(cleanJson, ClassificationResultDTO.class);

        } catch (Exception e) {
            log.error("Failed to classify document: {}", e.getMessage());
            // Fallback gracefully so ingestion is not aborted
            return new ClassificationResultDTO("Unknown", 0);
        }
    }
}