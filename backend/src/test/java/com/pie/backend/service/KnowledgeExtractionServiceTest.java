package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class KnowledgeExtractionServiceTest {

    @Test
    void extractKnowledge_returnsDtoFromChatClient() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        ProcessKnowledgeNormalizer normalizer = new ProcessKnowledgeNormalizer();

        String rawJsonResponse = """
                {
                  "activities": ["Activity A"],
                  "actors": ["Actor 1"],
                  "roles": ["Role R"],
                  "systems": ["System X"],
                  "events": ["Event E"],
                  "gateways": ["Gateway G"],
                  "inputs": ["Input I"],
                  "outputs": ["Output O"],
                  "businessRules": ["Rule R"],
                  "risks": ["Risk R"],
                  "conflicts": ["Conflict between doc1 and doc2"]
                }
                """;

        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(rawJsonResponse);

        KnowledgeExtractionService svc = new KnowledgeExtractionService(builder, normalizer);

        ProcessKnowledgeDTO res = svc.extractKnowledge("Car manufacturing documentation sample text");

        assertNotNull(res);
        assertEquals(List.of("Activity A"), res.activities());
        assertEquals(List.of("Actor 1"), res.actors());
        assertEquals(List.of("Role R"), res.roles());
        assertEquals(List.of("System X"), res.systems());
        assertEquals(List.of("Event E"), res.events());
        assertEquals(List.of("Gateway G"), res.gateways());
        assertEquals(List.of("Input I"), res.inputs());
        assertEquals(List.of("Output O"), res.outputs());
        assertEquals(List.of("Rule R"), res.businessRules());
        assertEquals(List.of("Risk R"), res.risks());
        assertTrue(res.conflicts().isEmpty());
    }

      @Test
      void extractKnowledge_keepsConflictsForExplicitCombinedDocuments() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
            .thenReturn("{\"activities\":[\"Activity A\"],\"conflicts\":[\"Doc 1 differs from Doc 2\"]}");

        KnowledgeExtractionService service = new KnowledgeExtractionService(builder, new ProcessKnowledgeNormalizer());
        ProcessKnowledgeDTO result = service.extractKnowledge(
            "--- BEGIN DOCUMENT 1: first.txt ---\nFirst\n--- END DOCUMENT 1 ---\n"
                + "--- BEGIN DOCUMENT 2: second.txt ---\nSecond\n--- END DOCUMENT 2 ---");

        assertEquals(List.of("Doc 1 differs from Doc 2"), result.conflicts());
      }
}