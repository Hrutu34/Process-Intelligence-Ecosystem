package com.pie.backend.controller;

import com.pie.backend.service.DocumentParsingService;
import com.pie.backend.service.KnowledgeExtractionService;
import com.pie.shared.dto.ProcessDocumentDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseEntity extractFromText(@RequestBody ProcessDocumentDTO request) {
        if (request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(extractionService.extractKnowledge(request.content()));
    }

    @PostMapping(value = "/extract-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity extractFromFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // 1. Extract raw text from PDF/DOCX/TXT/XLSX
        String extractedText = parsingService.parseDocument(file);
        
        // 2. Pass text to AI Agent
        return ResponseEntity.ok(extractionService.extractKnowledge(extractedText));
    }
}