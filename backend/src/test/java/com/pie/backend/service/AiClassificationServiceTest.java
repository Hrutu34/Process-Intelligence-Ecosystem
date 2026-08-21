package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AiClassificationServiceTest {

    @Test
    void classifyDocument_returnsDtoFromChatClient() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        String rawJsonResponse = """
                {
                  "category": "Standard Operating Procedure",
                  "confidence": 95
                }
                """;

        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(rawJsonResponse);

        AiClassificationService service = new AiClassificationService(builder);
        ClassificationResultDTO result = service.classifyDocument("Sample process text");

        assertNotNull(result);
        assertEquals("Standard Operating Procedure", result.category());
        assertEquals(95, result.confidence());
    }
}