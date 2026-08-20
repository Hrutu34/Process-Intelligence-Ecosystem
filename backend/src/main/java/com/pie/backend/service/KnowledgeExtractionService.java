package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class KnowledgeExtractionService {

    private final ChatClient chatClient;

    // Inject the prompt file directly using Spring's @Value annotation
    @Value("classpath:prompts/knowledge-extraction-prompt.txt")
    private Resource extractionPromptResource;

    public KnowledgeExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ProcessKnowledgeDTO extractKnowledge(String documentContent) {
        try {
            // Read the text contents of the prompt file safely
            String systemPrompt = new String(extractionPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(documentContent)
                    .call()
                    .entity(ProcessKnowledgeDTO.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load knowledge extraction prompt template", e);
        }
    }
}