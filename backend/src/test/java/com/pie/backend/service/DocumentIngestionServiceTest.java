package com.pie.backend.service;

import com.pie.shared.dto.ClassificationResultDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {

    @Test
    void ingestText_doesNotReportCrossDocumentConflicts() {
        DocumentParsingService parsingService = mock(DocumentParsingService.class);
        KnowledgeExtractionService extractionService = mock(KnowledgeExtractionService.class);
        ClassificationService classificationService = mock(ClassificationService.class);

        ProcessKnowledgeDTO extractedKnowledge = new ProcessKnowledgeDTO(
                List.of("Submit request"),
                List.of("Requester"),
                List.of("Employee"),
                List.of("Portal"),
                List.of("Request received"),
                List.of(),
                List.of("Request details"),
                List.of("Submitted request"),
                List.of("Requests require approval"),
                List.of(),
                List.of("Contradiction incorrectly inferred within one text input")
        );

        when(classificationService.classifyDocument("A single process description."))
                .thenReturn(new ClassificationResultDTO("Process Description", 90));
        when(extractionService.extractKnowledge("A single process description."))
                .thenReturn(extractedKnowledge);

        DocumentIngestionService service = new DocumentIngestionService(
                parsingService, extractionService, classificationService);

        ProcessKnowledgeDTO result = service.ingestText("A single process description.", null, null);

        assertEquals(extractedKnowledge.activities(), result.activities());
        assertEquals(List.of(), result.conflicts());
    }
}