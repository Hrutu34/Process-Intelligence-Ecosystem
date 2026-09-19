package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for the LLMaaS chat completions endpoint
 * ({@code POST /chat/completions}), matching the OpenAI-compatible schema.
 */
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Boolean stream,
        @JsonProperty("max_tokens") Integer maxTokens
) {
}
