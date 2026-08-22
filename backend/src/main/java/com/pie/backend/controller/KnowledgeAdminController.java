package com.pie.backend.controller;

import com.pie.backend.model.ProcessKnowledgeEntity;
import com.pie.backend.repository.ProcessKnowledgeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class KnowledgeAdminController {

    private final ProcessKnowledgeRepository repo;

    public KnowledgeAdminController(ProcessKnowledgeRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/admin/knowledge/document/{documentId}")
    public List<ProcessKnowledgeEntity> findByDocument(@PathVariable String documentId) {
        return repo.findByDocumentId(documentId);
    }
}
