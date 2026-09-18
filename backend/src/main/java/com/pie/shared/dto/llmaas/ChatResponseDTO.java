package com.pie.shared.dto.llmaas;

/**
 * Outbound response body for {@code POST /api/llm/chat}.
 */
public record ChatResponseDTO(String answer, String model) {
}
