package com.pie.backend.controller;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.backend.service.KnowledgeExtractionService;
import com.pie.backend.service.DocumentParsingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List; // THIS is the critical import that fixes the Object mismatch

@RestController
@RequestMapping("/api/v1/process")
@CrossOrigin(origins = "http://localhost:5173")
public class ProcessController {

    private final KnowledgeExtractionService extractionService;
    private final DocumentParsingService parsingService;

    public ProcessController(KnowledgeExtractionService extractionService, DocumentParsingService parsingService) {
        this.extractionService = extractionService;
        this.parsingService = parsingService;
    }

    @PostMapping("/extract-text")
    public ResponseEntity<ProcessKnowledgeDTO> extractFromText(@RequestBody TextPayload payload) {
        return ResponseEntity.ok(extractionService.extractKnowledge(payload.content()));
    }

    @PostMapping(value = "/extract-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessKnowledgeDTO> extractFromFile(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        StringBuilder combinedText = new StringBuilder();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (!file.isEmpty()) {
                String extractedText = parsingService.parseDocument(file);
                combinedText.append("\n\n--- BEGIN DOCUMENT ").append(i + 1)
                            .append(" (").append(file.getOriginalFilename()).append(") ---\n");
                combinedText.append(extractedText);
                combinedText.append("\n--- END DOCUMENT ").append(i + 1).append(" ---\n");
            }
        }

        return ResponseEntity.ok(extractionService.extractKnowledge(combinedText.toString()));
    }

    public record TextPayload(String content) {}
}