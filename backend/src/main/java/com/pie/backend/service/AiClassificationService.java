package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AiClassificationService implements ClassificationService {

    private final ChatClient chatClient;

    // Inject the externalized prompt resource from the classpath
    @Value("classpath:prompts/document-classification-prompt.txt")
    private Resource classificationPromptResource;

    public AiClassificationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ClassificationResultDTO classifyDocument(String content) {
        try {
            // Read the file contents securely at runtime
            String systemPrompt = new String(classificationPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(content)
                    .call()
                    .entity(ClassificationResultDTO.class);
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document classification prompt template", e);
        }
    }
}