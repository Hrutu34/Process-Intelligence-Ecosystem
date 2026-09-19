package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body returned by the LLMaaS embeddings endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingApiResponse(String model, List<EmbeddingApiData> data, UsageInfo usage) {
}
