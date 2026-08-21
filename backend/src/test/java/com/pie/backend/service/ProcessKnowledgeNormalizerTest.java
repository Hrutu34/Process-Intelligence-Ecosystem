package com.pie.backend.service;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProcessKnowledgeNormalizerTest {

    private ProcessKnowledgeNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ProcessKnowledgeNormalizer();
    }

    @Test
    void parseAndNormalize_removesDuplicatesAndEmptyValues() {
        String dirtyJson = """
            ```json
            Here is the extraction:
            {
              "activities": ["Submit Request", "Submit Request", "  ", "", "Review Request"],
              "actors": ["Manager", "Manager", "Employee", null],
              "roles": [],
              "systems": ["ERP", "ERP"],
              "events": [],
              "gateways": [],
              "inputs": [],
              "outputs": [],
              "businessRules": [],
              "risks": [],
              "conflicts": []
            }
            ```
            """;

        ProcessKnowledgeDTO dto = normalizer.parseAndNormalize(dirtyJson);

        assertNotNull(dto);
        // Rule: Duplicate activities and actors removed
        assertEquals(List.of("Submit Request", "Review Request"), dto.activities());
        assertEquals(List.of("Manager", "Employee"), dto.actors());
        // Rule: Systems deduplicated and empty values stripped
        assertEquals(List.of("ERP"), dto.systems());
    }

    @Test
    void parseAndNormalize_repairsWrappedAndPrefacedJson() {
        String wrappedJson = "Random AI text... {\"activities\":[\"Task 1\"], \"actors\":[\"Actor 1\"]} ...trailing text";

        ProcessKnowledgeDTO dto = normalizer.parseAndNormalize(wrappedJson);

        assertNotNull(dto);
        assertEquals(List.of("Task 1"), dto.activities());
        assertEquals(List.of("Actor 1"), dto.actors());
    }

    @Test
    void parseAndNormalize_throwsExceptionOnInvalidJson() {
        String nonJson = "Error: Model unavailable or empty output";
        assertThrows(IllegalArgumentException.class, () -> normalizer.parseAndNormalize(nonJson));
    }
}