package com.pie.shared.dto.llmaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A single embedding vector entry as returned by the LLMaaS embeddings endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingApiData(Integer index, List<Double> embedding) {
}
