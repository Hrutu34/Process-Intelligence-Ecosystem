package com.pie.shared.dto.llmaas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound request body for {@code POST /api/llm/embeddings}.
 */
public record EmbeddingRequestDTO(
        @NotBlank(message = "input must not be blank")
        @Size(max = 8000, message = "input must be at most 8000 characters")
        String input
) {
}
