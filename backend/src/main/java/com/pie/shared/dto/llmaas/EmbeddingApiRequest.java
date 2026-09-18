package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for the LLMaaS embeddings endpoint ({@code POST /embeddings}).
 */
public record EmbeddingApiRequest(
        String model,
        String input,
        @JsonProperty("encoding_format") String encodingFormat
) {
}
