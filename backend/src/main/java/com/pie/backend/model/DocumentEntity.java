package com.pie.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "documents")
public class DocumentEntity {
    @Id
    public String documentId;
    public String fileName;
    public String inputType;
    public String originalFilePath;
    public String extractedText;
    public String category;
    public Integer confidence;
    public String uploadedBy;
    public String groupId;
    public Instant createdAt;
    public Instant updatedAt;

    public DocumentEntity() {
    }

    public DocumentEntity(String documentId) {
        this.documentId = documentId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
