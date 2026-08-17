package com.pie.backend.service;

import com.pie.backend.model.DocumentRecord;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class DocumentIngestionService {

    private final DocumentStorageService storageService;
    private final DocumentParsingService parsingService;
    private final ClassificationService classificationService;
    private final KnowledgeExtractionService extractionService;

    public DocumentIngestionService(DocumentStorageService storageService,
            DocumentParsingService parsingService,
            ClassificationService classificationService,
            KnowledgeExtractionService extractionService) {
        this.storageService = storageService;
        this.parsingService = parsingService;
        this.classificationService = classificationService;
        this.extractionService = extractionService;
    }

    public ProcessKnowledgeDTO ingestFile(MultipartFile file, String uploadedBy, String groupId) {
        try {
            String docId = storageService.createDocumentId();
            // extract text once
            String extracted = parsingService.parseDocument(file);
            String normalized = normalize(extracted);

            DocumentRecord rec = storageService.persistFileAndExtractedText(docId, file, normalized);

            ClassificationService.ClassificationResult result = classificationService.classify(normalized);
            if (result == null || result.category == null) {
                throw new RuntimeException("Invalid classification result");
            }

            rec.category = result.category;
            rec.confidence = result.confidence;
            rec.uploadedBy = uploadedBy;
            rec.groupId = groupId;
            storageService.updateMetadata(rec);

            // route to extraction using persisted extracted text and persist extracted
            // knowledge
            return extractionService.extractKnowledge(normalized, docId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist document: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    public ProcessKnowledgeDTO ingestFilesCombined(java.util.List<MultipartFile> files, String uploadedBy,
            String groupId) {
        try {
            StringBuilder combinedText = new StringBuilder();

            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file.isEmpty())
                    continue;
                String docId = storageService.createDocumentId();
                String extracted = parsingService.parseDocument(file); // extract once per file
                String normalized = normalize(extracted);
                DocumentRecord rec = storageService.persistFileAndExtractedText(docId, file, normalized);

                ClassificationService.ClassificationResult result = classificationService.classify(rec.extractedText);
                rec.category = result == null ? "Unknown" : result.category;
                rec.confidence = result == null ? 0 : result.confidence;
                rec.uploadedBy = uploadedBy;
                rec.groupId = groupId;
                storageService.updateMetadata(rec);

                combinedText.append("\n\n--- BEGIN DOCUMENT ").append(i + 1)
                        .append(" (").append(rec.fileName).append(") ---\n");
                combinedText.append(rec.extractedText);
                combinedText.append("\n--- END DOCUMENT ").append(i + 1).append(" ---\n");
            }

            return extractionService.extractKnowledge(combinedText.toString(), null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist document(s): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    public ProcessKnowledgeDTO ingestText(String text, String uploadedBy, String groupId) {
        try {
            String docId = storageService.createDocumentId();
            String normalized = normalize(text);
            DocumentRecord rec = storageService.persistTextOnly(docId, "input_text", normalized);

            ClassificationService.ClassificationResult result = classificationService.classify(normalized);
            if (result == null || result.category == null) {
                throw new RuntimeException("Invalid classification result");
            }

            rec.category = result.category;
            rec.confidence = result.confidence;
            rec.uploadedBy = uploadedBy;
            rec.groupId = groupId;
            storageService.updateMetadata(rec);

            return extractionService.extractKnowledge(normalized, docId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist text: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    private String normalize(String text) {
        if (text == null)
            return "";
        return text.trim().replaceAll("\r\n", "\n");
    }
}
