package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class KnowledgeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionService.class);

    private final ChatClient chatClient;
    private final ProcessKnowledgeNormalizer normalizer;

    @Value("classpath:prompts/knowledge-extraction-prompt.txt")
    private Resource extractionPromptResource;

    public KnowledgeExtractionService(ChatClient.Builder chatClientBuilder, ProcessKnowledgeNormalizer normalizer) {
        this.chatClient = chatClientBuilder.build();
        this.normalizer = normalizer;
    }

    public ProcessKnowledgeDTO extractKnowledge(String documentContent) {
        try {
            String systemPrompt = new String(extractionPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);

            String rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(documentContent != null ? documentContent : "")
                    .call()
                    .content();

            // Validate, repair, normalize, and generate standardized DTO
            return normalizer.parseAndNormalize(rawResponse);

        } catch (Exception e) {
            log.error("Knowledge extraction process failed: {}", e.getMessage());
            throw new RuntimeException("Knowledge extraction failed: " + e.getMessage(), e);
        }
    }
}