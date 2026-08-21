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
    void extractKnowledge_returnsDtoFromChatClient() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        // Sample JSON string returned by the LLM
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

        KnowledgeExtractionService svc = new KnowledgeExtractionService(builder);

        ProcessKnowledgeDTO res = svc.extractKnowledge("# Car Manufacturing Process – Detailed Process Document\r\n" +
                "## 1. Purpose\r\n" +
                "The purpose of this document is to describe the major processes...");

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
        assertEquals(List.of("Conflict between doc1 and doc2"), res.conflicts());
    }
}