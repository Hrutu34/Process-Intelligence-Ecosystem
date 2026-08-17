package com.pie.backend.service;

import com.pie.backend.model.DocumentRecord;

public interface ClassificationService {
    ClassificationResult classify(String extractedText) throws Exception;

    public static class ClassificationResult {
        public String category;
        public int confidence;

        public ClassificationResult() {
        }

        public ClassificationResult(String category, int confidence) {
            this.category = category;
            this.confidence = confidence;
        }
    }
}
