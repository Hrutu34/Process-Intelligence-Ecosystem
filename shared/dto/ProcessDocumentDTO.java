package com.pie.shared.dto;

public record ProcessDocumentDTO(
    String documentId,
    String name,
    String content,
    String sourceType
) {}