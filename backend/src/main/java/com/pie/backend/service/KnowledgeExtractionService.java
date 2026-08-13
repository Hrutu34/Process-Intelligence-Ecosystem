package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeExtractionService {

    private final ChatClient chatClient;

    public KnowledgeExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ProcessKnowledgeDTO extractKnowledge(String unstructuredText) {
        
        String systemPrompt = """
            You are a strict, highly analytical Business Process Architect. Your task is to extract structured intelligence strictly from the provided text.
            
            ABSOLUTE RULES (ANTI-HALLUCINATION):
            - ONLY extract information explicitly stated in the text.
            - DO NOT invent decisions, actors, or systems based on outside knowledge.
            
            The user input may contain multiple documents separated by "--- BEGIN DOCUMENT X ---".
            
            EXTRACTION CATEGORIES:
            1. ACTORS: Strictly human roles, teams, or departments (e.g., 'Hardware Engineer'). NEVER classify hardware, sensors, scripts, or software as actors.
            2. SYSTEMS: Software applications, databases, or physical hardware (e.g., 'ERP', 'Unity Game Engine', 'Java Portal').
            3. DECISIONS: Explicit branching logic or conditional human approvals explicitly mentioned in the text (e.g., 'If the feed is clean...').
            4. EVENTS: Specific triggers that initiate or interrupt a process (e.g., 'Sensor installed').
            5. ACTIVITIES: Actionable steps performed in the workflow.
            
            CROSS-DOCUMENT CONFLICT DETECTION:
            6. CONFLICTS: You are analyzing multiple documents separated by boundaries. You MUST identify contradictions. Check specifically for:
            - Did Document 1 use a system that Document 2 decommissioned or replaced?
            - Did Document 1 have a human actor do a task that Document 2 automated?
            - Are there conflicting rules?
            List every contradiction explicitly. Example: "Document 1 States the use of PLM, but Document 2 mandates use of JIRA."
            
            If any category is empty, return an empty array.
            """;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(unstructuredText)
                .call()
                .entity(ProcessKnowledgeDTO.class);
    }
}