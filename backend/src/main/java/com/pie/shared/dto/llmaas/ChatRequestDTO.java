package com.pie.shared.dto.llmaas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound request body for {@code POST /api/llm/chat}.
 */
public record ChatRequestDTO(
        @NotBlank(message = "prompt must not be blank")
        @Size(max = 8000, message = "prompt must be at most 8000 characters")
        String prompt
) {
}
