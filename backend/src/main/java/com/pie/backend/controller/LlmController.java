package com.pie.backend.controller;

import com.pie.backend.service.VwLlmaasService;
import com.pie.shared.dto.llmaas.ChatRequestDTO;
import com.pie.shared.dto.llmaas.ChatResponseDTO;
import com.pie.shared.dto.llmaas.EmbeddingRequestDTO;
import com.pie.shared.dto.llmaas.EmbeddingResponseDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for VW Group LLMaaS operations (chat completions and embeddings).
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final VwLlmaasService llmaasService;

    @Value("${vw.llm.api.chat-model}")
    private String chatModel;

    public LlmController(VwLlmaasService llmaasService) {
        this.llmaasService = llmaasService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDTO> chat(@Valid @RequestBody ChatRequestDTO request) {
        String answer = llmaasService.chat(request.prompt());
        return ResponseEntity.ok(new ChatResponseDTO(answer, chatModel));
    }

    @PostMapping("/embeddings")
    public ResponseEntity<EmbeddingResponseDTO> embeddings(@Valid @RequestBody EmbeddingRequestDTO request) {
        EmbeddingResponseDTO response = llmaasService.embed(request.input());
        return ResponseEntity.ok(response);
    }
}
