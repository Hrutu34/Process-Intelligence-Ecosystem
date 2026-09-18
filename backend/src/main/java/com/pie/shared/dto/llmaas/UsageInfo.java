package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage accounting returned alongside chat completions and embeddings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageInfo(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens
) {
}
