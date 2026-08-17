package com.pie.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentRecord {
    public String documentId;
    public String fileName;
    public String inputType; // file | text
    public String originalFilePath;
    public String extractedText;
    public String category;
    public Integer confidence; // 0-100
    public String uploadedBy;
    public String groupId;
    public Instant createdAt;
    public Instant updatedAt;

    public DocumentRecord() {
    }

    public DocumentRecord(String documentId) {
        this.documentId = documentId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
