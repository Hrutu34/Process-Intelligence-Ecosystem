package com.pie.backend.controller;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.backend.service.KnowledgeExtractionService;
import com.pie.backend.service.DocumentIngestionService;
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
    private final DocumentIngestionService ingestionService;

    public ProcessController(KnowledgeExtractionService extractionService, DocumentIngestionService ingestionService) {
        this.extractionService = extractionService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/extract-text")
    public ResponseEntity<ProcessKnowledgeDTO> extractFromText(@RequestBody TextPayload payload) {
        return ResponseEntity.ok(ingestionService.ingestText(payload.content(), null, null));
    }

    @PostMapping(value = "/extract-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessKnowledgeDTO> extractFromFile(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ingestionService.ingestFilesCombined(files, null, null));
    }

    public record TextPayload(String content) {
    }
}