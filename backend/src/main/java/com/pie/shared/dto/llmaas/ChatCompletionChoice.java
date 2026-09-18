package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single completion choice returned by the LLMaaS chat completions endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionChoice(
        Integer index,
        ChatMessage message,
        @JsonProperty("finish_reason") String finishReason
) {
}
