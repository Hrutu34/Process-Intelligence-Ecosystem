package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class KnowledgeExtractionServiceTest {

    @Test
    void extractKnowledge_returnsDtoFromChatClient() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        // Updated mock DTO matching the new full scope fields
        ProcessKnowledgeDTO dto = new ProcessKnowledgeDTO(
                List.of("Activity A"), // activities
                List.of("Actor 1"),    // actors
                List.of("Role R"),     // roles
                List.of("System X"),   // systems
                List.of("Event E"),    // events
                List.of("Gateway G"),  // gateways
                List.of("Input I"),    // inputs
                List.of("Output O"),   // outputs
                List.of("Rule R"),     // businessRules
                List.of("Risk R")      // risks
        );

        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(ProcessKnowledgeDTO.class)))
                .thenReturn(dto);

        // Updated constructor to match our clean YAGNI implementation
        KnowledgeExtractionService svc = new KnowledgeExtractionService(builder);
        
        ProcessKnowledgeDTO res = svc.extractKnowledge("# Car Manufacturing Process – Detailed Process Document\r\n" + //
                "\r\n" + //
                "## 1. Purpose\r\n" + //
                "\r\n" + //
                "The purpose of this document is to describe the major processes and important steps involved in manufacturing a car...\r\n");

        assertNotNull(res);
        assertEquals(dto.activities(), res.activities());
        assertEquals(dto.actors(), res.actors());
        assertEquals(dto.systems(), res.systems());
        assertEquals(dto.events(), res.events());
        assertEquals(dto.businessRules(), res.businessRules());
        assertEquals(dto.risks(), res.risks());
    }
}