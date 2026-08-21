package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
public class DocumentIngestionService {

    // Define the SLF4J logger for this class
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentParsingService parsingService;
    private final KnowledgeExtractionService extractionService;
    private final ClassificationService classificationService;

    public DocumentIngestionService(
            DocumentParsingService parsingService,
            KnowledgeExtractionService extractionService,
            ClassificationService classificationService) {
        this.parsingService = parsingService;
        this.extractionService = extractionService;
        this.classificationService = classificationService;
    }

    public ProcessKnowledgeDTO ingestText(String content, String uploaderId, String tenantId) {
        ClassificationResultDTO classification = classificationService.classifyDocument(content);
        
        // Formatted Spring Boot log output
        log.info("Classified content as: '{}' with confidence: {}%", 
                classification.category(), classification.confidence());

        return extractionService.extractKnowledge(content);
    }

    public ProcessKnowledgeDTO ingestFilesCombined(List<MultipartFile> files, String uploaderId, String tenantId) {
        StringBuilder combinedText = new StringBuilder();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String extractedText = parsingService.parseDocument(file);
            
            log.info("Parsing file [{}/{}]: {}", i + 1, files.size(), file.getOriginalFilename());

            // Classify each file individually and log the result
            ClassificationResultDTO classification = classificationService.classifyDocument(extractedText);
            log.info("File [{}] classified as: '{}' with confidence: {}%", 
                    file.getOriginalFilename(), classification.category(), classification.confidence());

            combinedText.append("--- BEGIN DOCUMENT ").append(i + 1)
                        .append(": ").append(file.getOriginalFilename())
                        .append(" ---\n")
                        .append(extractedText)
                        .append("\n--- END DOCUMENT ").append(i + 1).append(" ---\n\n");
        }

        return extractionService.extractKnowledge(combinedText.toString());
    }
}