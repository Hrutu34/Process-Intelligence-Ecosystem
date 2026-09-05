package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.shared.dto.ClassificationResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AiClassificationService implements ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AiClassificationService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("classpath:prompts/document-classification-prompt.txt")
    private Resource classificationPromptResource;

    public AiClassificationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ClassificationResultDTO classifyDocument(String content) {
        try {
            String systemPrompt;
            if (classificationPromptResource != null && classificationPromptResource.exists()) {
                systemPrompt = new String(classificationPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
            } else {
                try {
                    Resource defaultRes = new org.springframework.core.io.ClassPathResource("prompts/document-classification-prompt.txt");
                    systemPrompt = new String(defaultRes.getContentAsByteArray(), StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    systemPrompt = "Classify the input document into a category with confidence.";
                }
            }

            // Use only the first 2,000 characters for classification to prevent context exhaustion
            String snippet = (content != null && content.length() > 2000) 
                    ? content.substring(0, 2000) 
                    : content;

            String rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(snippet != null ? snippet : "")
                    .call()
                    .content();

            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("Model returned empty classification response, defaulting to Unknown");
                return new ClassificationResultDTO("Unknown", 0);
            }

            return objectMapper.readValue(extractJsonObject(rawResponse), ClassificationResultDTO.class);

        } catch (Exception e) {
            log.error("Failed to classify document: {}", e.getMessage());
            // Fallback gracefully so ingestion is not aborted
            return new ClassificationResultDTO("Unknown", 0);
        }
    }

    private String extractJsonObject(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("Classification response contains no JSON object");
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return trimmed.substring(start, i + 1);
            }
        }

        throw new IllegalArgumentException("Classification response contains an incomplete JSON object");
    }
}