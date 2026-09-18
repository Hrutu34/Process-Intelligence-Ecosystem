package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body returned by the LLMaaS chat completions endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        String id,
        String model,
        List<ChatCompletionChoice> choices,
        UsageInfo usage
) {
}
