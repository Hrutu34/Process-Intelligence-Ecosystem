package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
public class DocumentIngestionService {

    private final DocumentParsingService parsingService;
    private final KnowledgeExtractionService extractionService;
    private final ClassificationService classificationService; // 1. Inject the new Classification Layer

    public DocumentIngestionService(
            DocumentParsingService parsingService,
            KnowledgeExtractionService extractionService,
            ClassificationService classificationService) {
        this.parsingService = parsingService;
        this.extractionService = extractionService;
        this.classificationService = classificationService;
    }

    public ProcessKnowledgeDTO ingestText(String content, String uploaderId, String tenantId) {
        // 2. Classify the raw text
        ClassificationResultDTO classification = classificationService.classifyDocument(content);
        System.out.println("✅ AI Classification: " + classification.category() + " (Confidence: " + classification.confidence() + "%)");

        // TODO: Store 'classification.category()' in your Document metadata database here

        // 3. Proceed to Extraction
        return extractionService.extractKnowledge(content);
    }

    public ProcessKnowledgeDTO ingestFilesCombined(List<MultipartFile> files, String uploaderId, String tenantId) {
        StringBuilder combinedText = new StringBuilder();

        for (int i = 0; i < files.size(); i++) {
            String extractedText = parsingService.parseDocument(files.get(i)); // Ensure parsingService has this method
            combinedText.append("--- BEGIN DOCUMENT ").append(i + 1).append(" ---\n");
            combinedText.append(extractedText).append("\n");
            
            // Optional: Classify each document individually before combining
            ClassificationResultDTO docClass = classificationService.classifyDocument(extractedText);
            System.out.println("📄 File " + files.get(i).getOriginalFilename() + " classified as: " + docClass.category());
        }

        // Proceed to Extraction on the combined text
        return extractionService.extractKnowledge(combinedText.toString());
    }
}