package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiClassificationService implements ClassificationService {

    private final ChatClient chatClient;

    public AiClassificationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ClassificationResultDTO classifyDocument(String content) {
        String systemPrompt = """
            You are a Document Classification Engine. Analyze the provided text and categorize it into EXACTLY ONE of the following categories based on these definitions:
            
            - "Standard Operating Procedure": Step-by-step technical or operational instructions.
            - "Policy Document": Rules, corporate memos, mandates, or high-level architecture changes.
            - "Process Description": High-level overviews of business flows.
            - "Workflow Specification": System, database, or API data flow descriptions.
            - "Meeting Notes": Transcripts, minutes, or action items from discussions.
            - "Requirements Document": Feature requests, user stories, or technical specifications.
            - "Unknown": If it absolutely does not fit the above.
            
            RULES:
            1. Calculate a confidence score between 0 and 100 based on how strongly the text matches the category.
            2. Return ONLY a valid JSON object matching the requested schema.
            """;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(content)
                .call()
                .entity(ClassificationResultDTO.class);
    }
}