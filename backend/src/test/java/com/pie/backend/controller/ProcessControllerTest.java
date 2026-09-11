package com.pie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.backend.service.CanonicalProcessGraphBuilder;
import com.pie.backend.service.DocumentIngestionService;
import com.pie.backend.service.ProcessGraphValidator;
import com.pie.backend.service.ProcessQualityValidator;
import com.pie.shared.dto.CanonicalProcessGraph;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);
        ProcessGraphValidator validator = new ProcessGraphValidator();
        CanonicalProcessGraphBuilder graphBuilder = new CanonicalProcessGraphBuilder(validator);
        ProcessQualityValidator qualityValidator = new ProcessQualityValidator();
        com.pie.backend.service.BpmnXmlParser bpmnParser = new com.pie.backend.service.BpmnXmlParser();

        ProcessController controller = new ProcessController(ingestionService, graphBuilder, qualityValidator, bpmnParser);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/process/graph returns CanonicalProcessGraph with correct structure")
    void testBuildGraphEndpoint() throws Exception {
        ProcessKnowledgeDTO payload = new ProcessKnowledgeDTO(
                List.of("Submit Travel Request", "Review Request", "Validate Budget", "Book Travel"),
                List.of("Employee", "Manager", "Finance", "Travel Desk"),
                List.of(), List.of(), List.of(),
                List.of("Manager Approval"),
                List.of(), List.of(),
                List.of("If approved, Finance validates budget"),
                List.of(), List.of()
        );

        mockMvc.perform(post("/api/v1/process/graph")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graphId").value("graph-submit-travel-request"))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(9))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(9))
                .andExpect(jsonPath("$.nodes[?(@.id == 'activity-submit-travel-request')].type").value("Activity"))
                .andExpect(jsonPath("$.nodes[?(@.id == 'role-employee')].type").value("Role"))
                .andExpect(jsonPath("$.edges[?(@.edgeType == 'conditional' && @.label == 'approved')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/process/validate-knowledge returns ProcessQualityReportDTO")
    void testValidateKnowledgeEndpoint() throws Exception {
        ProcessKnowledgeDTO payload = new ProcessKnowledgeDTO(
                List.of("Submit Request", "Approve Request", "Complete Task"),
                List.of("Employee", "Manager"),
                List.of(), List.of(),
                List.of("Start", "End"),
                List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        mockMvc.perform(post("/api/v1/process/validate-knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qualityScore").isNumber())
                .andExpect(jsonPath("$.issues").isArray());
    }
}
