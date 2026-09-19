package com.pie.shared.dto.llmaas;

import java.util.List;

/**
 * Outbound response body for {@code POST /api/llm/embeddings}.
 */
public record EmbeddingResponseDTO(List<Double> embedding, String model, int dimensions) {
}
