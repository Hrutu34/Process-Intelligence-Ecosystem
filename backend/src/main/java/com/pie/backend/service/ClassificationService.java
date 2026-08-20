package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;

public interface ClassificationService {
    // Updated to return the DTO instead of a String
    ClassificationResultDTO classifyDocument(String content);
}