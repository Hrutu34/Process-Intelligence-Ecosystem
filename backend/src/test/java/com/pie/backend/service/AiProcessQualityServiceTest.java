package com.pie.backend.service;

import com.pie.shared.dto.GraphNode;
import com.pie.shared.dto.NodeType;
import com.pie.shared.dto.ProcessGraphDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.shared.dto.ProcessQualityReportDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiProcessQualityServiceTest {

    @Test
    void enhance_blendsSemanticScoreWithDeterministicScore() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"semanticScore\":80,\"findings\":[\"One unclear transition\"],\"recommendations\":[\"Clarify the transition\"]}");

        ProcessQualityReportDTO deterministic = new ProcessQualityReportDTO(true, 90, List.of(), List.of());
        ProcessQualityReportDTO result = new AiProcessQualityService(builder)
                .enhance(knowledge(), graph(), deterministic);

        assertEquals(87, result.qualityScore());
        assertEquals(1, result.issues().size());
        assertEquals(1, result.recommendations().size());
    }

    @Test
    void enhance_fallsBackWhenModelResponseIsInvalid() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Model unavailable");

        ProcessQualityReportDTO deterministic = new ProcessQualityReportDTO(true, 90, List.of(), List.of());
        ProcessQualityReportDTO result = new AiProcessQualityService(builder)
                .enhance(knowledge(), graph(), deterministic);

        assertSame(deterministic, result);
    }

    private ProcessKnowledgeDTO knowledge() {
        return new ProcessKnowledgeDTO(
                List.of("Submit request"), List.of("Employee"), List.of("Employee"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private ProcessGraphDTO graph() {
        return ProcessGraphDTO.builder()
                .graphId("graph-test")
                .addNode(GraphNode.builder().id("activity-submit").type(NodeType.Activity).label("Submit request").build())
                .build();
    }
}
