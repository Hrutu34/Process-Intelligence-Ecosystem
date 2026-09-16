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
    private final AgentLogger agentLogger;

    public DocumentIngestionService(
            DocumentParsingService parsingService,
            KnowledgeExtractionService extractionService,
            ClassificationService classificationService,
            AgentLogger agentLogger) {
        this.parsingService = parsingService;
        this.extractionService = extractionService;
        this.classificationService = classificationService;
        this.agentLogger = agentLogger;
    }

    public ProcessKnowledgeDTO ingestText(String content, String uploaderId, String tenantId) {
        long classStart = System.currentTimeMillis();
        agentLogger.logStart("CLASSIFICATION");
        ClassificationResultDTO classification;
        try {
            classification = classificationService.classifyDocument(content);
            agentLogger.logSuccess("CLASSIFICATION", System.currentTimeMillis() - classStart);
        } catch (Exception e) {
            agentLogger.logError("CLASSIFICATION", System.currentTimeMillis() - classStart, e.getClass().getSimpleName());
            throw e;
        }
        
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

        long extractStart = System.currentTimeMillis();
        agentLogger.logStart("KNOWLEDGE_EXTRACTION");
        ProcessKnowledgeDTO extractedKnowledge;
        try {
            extractedKnowledge = extractionService.extractKnowledge(content);
            agentLogger.logSuccess("KNOWLEDGE_EXTRACTION", System.currentTimeMillis() - extractStart);
        } catch (Exception e) {
            agentLogger.logError("KNOWLEDGE_EXTRACTION", System.currentTimeMillis() - extractStart, e.getClass().getSimpleName());
            throw e;
        }

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

        long extractFileStart = System.currentTimeMillis();
        agentLogger.logStart("DOCUMENT_EXTRACTION");
        try {
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
            agentLogger.logSuccess("DOCUMENT_EXTRACTION", System.currentTimeMillis() - extractFileStart);
        } catch (Exception e) {
            agentLogger.logError("DOCUMENT_EXTRACTION", System.currentTimeMillis() - extractFileStart, e.getClass().getSimpleName());
            throw e;
        }

        long extractStart = System.currentTimeMillis();
        agentLogger.logStart("KNOWLEDGE_EXTRACTION");
        ProcessKnowledgeDTO extractedKnowledge;
        try {
            extractedKnowledge = extractionService.extractKnowledge(combinedText.toString());
            agentLogger.logSuccess("KNOWLEDGE_EXTRACTION", System.currentTimeMillis() - extractStart);
        } catch (Exception e) {
            agentLogger.logError("KNOWLEDGE_EXTRACTION", System.currentTimeMillis() - extractStart, e.getClass().getSimpleName());
            throw e;
        }

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