package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pie.backend.model.DocumentEntity;
import com.pie.backend.model.DocumentRecord;
import com.pie.backend.repository.DocumentRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private final Path storageRoot = Path.of("storage", "documents");
    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentRepository repository;

    public DocumentStorageService(Optional<DocumentRepository> repository) throws IOException {
        Files.createDirectories(storageRoot);
        this.repository = repository.orElse(null);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String createDocumentId() {
        return "doc_" + UUID.randomUUID().toString();
    }

    public DocumentRecord persistFileAndExtractedText(String documentId, MultipartFile file, String extractedText)
            throws IOException {
        Path docRoot = storageRoot.resolve(documentId);
        Path originalDir = docRoot.resolve("original");
        Path extractedDir = docRoot.resolve("extracted");
        Files.createDirectories(originalDir);
        Files.createDirectories(extractedDir);

        String originalFileName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        Path originalPath = originalDir.resolve(originalFileName);
        Files.copy(file.getInputStream(), originalPath);

        Path extractedPath = extractedDir.resolve("content.txt");
        Files.writeString(extractedPath, extractedText == null ? "" : extractedText);

        DocumentRecord rec = new DocumentRecord(documentId);
        rec.fileName = originalFileName;
        rec.inputType = "file";
        rec.originalFilePath = originalPath.toString();
        rec.extractedText = extractedText;
        mapper.writeValue(docRoot.resolve("metadata.json").toFile(), rec);

        if (repository != null) {
            DocumentEntity ent = new DocumentEntity(documentId);
            BeanUtils.copyProperties(rec, ent);
            repository.save(ent);
        }

        return rec;
    }

    public DocumentRecord persistTextOnly(String documentId, String name, String extractedText) throws IOException {
        Path docRoot = storageRoot.resolve(documentId);
        Path extractedDir = docRoot.resolve("extracted");
        Files.createDirectories(extractedDir);

        Path extractedPath = extractedDir.resolve("content.txt");
        Files.writeString(extractedPath, extractedText == null ? "" : extractedText);

        DocumentRecord rec = new DocumentRecord(documentId);
        rec.fileName = name;
        rec.inputType = "text";
        rec.originalFilePath = null;
        rec.extractedText = extractedText;
        mapper.writeValue(docRoot.resolve("metadata.json").toFile(), rec);

        if (repository != null) {
            DocumentEntity ent = new DocumentEntity(documentId);
            BeanUtils.copyProperties(rec, ent);
            repository.save(ent);
        }

        return rec;
    }

    public void updateMetadata(DocumentRecord rec) throws IOException {
        Path docRoot = storageRoot.resolve(rec.documentId);
        mapper.writeValue(docRoot.resolve("metadata.json").toFile(), rec);

        if (repository != null) {
            DocumentEntity ent = new DocumentEntity(rec.documentId);
            BeanUtils.copyProperties(rec, ent);
            repository.save(ent);
        }
    }
}
