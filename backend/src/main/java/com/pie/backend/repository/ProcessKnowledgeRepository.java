package com.pie.backend.repository;

import com.pie.backend.model.ProcessKnowledgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessKnowledgeRepository extends JpaRepository<ProcessKnowledgeEntity, String> {
    List<ProcessKnowledgeEntity> findByDocumentId(String documentId);
}
