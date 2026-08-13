package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeExtractionService {

    private final ChatClient chatClient;

    // Spring automatically injects the Builder pointing to your @Primary ChatModel (Ollama)
    public KnowledgeExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ProcessKnowledgeDTO extractKnowledge(String unstructuredText) {
        
        // Move instructions to a System prompt for better LLM adherence
        String systemPrompt = """
            You are an expert Business Process Analyst. Your task is to extract structural process intelligence from the provided unstructured text.
            
            Instructions:
            1. Identify all activities, actors, software systems, events, and decisions.
            2. If any category is empty, return an empty array.
            """;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(unstructuredText)
                .call()
                .entity(ProcessKnowledgeDTO.class); // Automatically maps to your Record and handles type-safety!
    }
}