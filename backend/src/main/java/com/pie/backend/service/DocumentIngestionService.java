package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;
import com.pie.shared.dto.ProcessDocumentDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        // Package classification into a document DTO
        ProcessDocumentDTO document = new ProcessDocumentDTO(
                UUID.randomUUID().toString(),
                "Direct_Text_Input",
                content,
                "TEXT",
                classification.category(),
                classification.confidence()
        );

        ProcessKnowledgeDTO extractedKnowledge = extractionService.extractKnowledge(content);

        // Reconstruct DTO to attach the classified document
        return new ProcessKnowledgeDTO(
                extractedKnowledge.activities(),
                extractedKnowledge.actors(),
                extractedKnowledge.roles(),
                extractedKnowledge.systems(),
                extractedKnowledge.events(),
                extractedKnowledge.gateways(),
                extractedKnowledge.inputs(),
                extractedKnowledge.outputs(),
                extractedKnowledge.businessRules(),
                extractedKnowledge.risks(),
                extractedKnowledge.conflicts(),
                List.of(document)
        );
    }

    public ProcessKnowledgeDTO ingestFilesCombined(List<MultipartFile> files, String uploaderId, String tenantId) {
        StringBuilder combinedText = new StringBuilder();
        List<ProcessDocumentDTO> documents = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String extractedText = parsingService.parseDocument(file);
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Unknown_File_" + i;
            
            log.info("Parsing file [{}/{}]: {}", i + 1, files.size(), fileName);

            // Classify each file individually and log the result
            ClassificationResultDTO classification = classificationService.classifyDocument(extractedText);
            log.info("File [{}] classified as: '{}' with confidence: {}%", 
                    fileName, classification.category(), classification.confidence());

            // Add classified metadata to our document list
            documents.add(new ProcessDocumentDTO(
                    UUID.randomUUID().toString(),
                    fileName,
                    extractedText,
                    "FILE",
                    classification.category(),
                    classification.confidence()
            ));

            combinedText.append("--- BEGIN DOCUMENT ").append(i + 1)
                        .append(": ").append(fileName)
                        .append(" ---\n")
                        .append(extractedText)
                        .append("\n--- END DOCUMENT ").append(i + 1).append(" ---\n\n");
        }

        ProcessKnowledgeDTO extractedKnowledge = extractionService.extractKnowledge(combinedText.toString());

        // Reconstruct DTO to attach the list of classified documents
        return new ProcessKnowledgeDTO(
                extractedKnowledge.activities(),
                extractedKnowledge.actors(),
                extractedKnowledge.roles(),
                extractedKnowledge.systems(),
                extractedKnowledge.events(),
                extractedKnowledge.gateways(),
                extractedKnowledge.inputs(),
                extractedKnowledge.outputs(),
                extractedKnowledge.businessRules(),
                extractedKnowledge.risks(),
                extractedKnowledge.conflicts(),
                documents
        );
    }
}