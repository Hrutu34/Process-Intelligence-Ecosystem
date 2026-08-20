package com.pie.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_knowledge")
public class ProcessKnowledgeEntity {
    @Id
    public String id;

    public String documentId;

    @Lob
    public String contentJson;

    public Instant createdAt;

    public ProcessKnowledgeEntity() {
    }

    public ProcessKnowledgeEntity(String documentId, String contentJson) {
        this.id = UUID.randomUUID().toString();
        this.documentId = documentId;
        this.contentJson = contentJson;
        this.createdAt = Instant.now();
    }
}
