package com.pie.backend.controller;

import com.pie.backend.service.DocumentIngestionService;
import com.pie.backend.service.AiProcessQualityService;
import com.pie.backend.service.BpmnDomainModelMapper;
import com.pie.backend.service.BpmnXmlGenerationService;
import com.pie.backend.service.ProcessGraphBuilder;
import com.pie.backend.service.ProcessQualityValidator;
import com.pie.shared.dto.ProcessGraphDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.shared.dto.ProcessQualityReportDTO;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List; 

@RestController
@RequestMapping("/api/v1/process")
@CrossOrigin(origins = "http://localhost:5173")
public class ProcessController {

    private final DocumentIngestionService ingestionService;
    private final ProcessGraphBuilder graphBuilder;
    private final ProcessQualityValidator qualityValidator;
    private final AiProcessQualityService aiQualityService;
    private final BpmnDomainModelMapper bpmnMapper;
    private final BpmnXmlGenerationService bpmnXmlService;
    private final com.pie.backend.service.AiBpmnRefinementService aiBpmnRefinementService;
    private final com.pie.backend.service.AiProcessReviewService aiProcessReviewService;

    public ProcessController(
            DocumentIngestionService ingestionService,
            ProcessGraphBuilder graphBuilder,
            ProcessQualityValidator qualityValidator) {
        this(ingestionService, graphBuilder, qualityValidator, null, null, null, null, null);
    }

    @Autowired
    public ProcessController(
            DocumentIngestionService ingestionService,
            ProcessGraphBuilder graphBuilder,
            ProcessQualityValidator qualityValidator,
            AiProcessQualityService aiQualityService,
            BpmnDomainModelMapper bpmnMapper,
            BpmnXmlGenerationService bpmnXmlService,
            com.pie.backend.service.AiBpmnRefinementService aiBpmnRefinementService,
            com.pie.backend.service.AiProcessReviewService aiProcessReviewService) {
        this.ingestionService = ingestionService;
        this.graphBuilder = graphBuilder;
        this.qualityValidator = qualityValidator;
        this.aiQualityService = aiQualityService;
        this.bpmnMapper = bpmnMapper;
        this.bpmnXmlService = bpmnXmlService;
        this.aiBpmnRefinementService = aiBpmnRefinementService;
        this.aiProcessReviewService = aiProcessReviewService;
    }

    @PostMapping("/extract-text")
    public ResponseEntity<ProcessKnowledgeDTO> extractFromText(@RequestBody TextPayload payload) {
        return ResponseEntity.ok(ingestionService.ingestText(payload.content(), null, null));
    }

    @PostMapping(value = "/extract-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessKnowledgeDTO> extractFromFile(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ingestionService.ingestFilesCombined(files, null, null));
    }

    @PostMapping("/graph")
    public ResponseEntity<ProcessGraphDTO> buildGraph(@RequestBody ProcessKnowledgeDTO knowledge) {
        return ResponseEntity.ok(graphBuilder.build(knowledge));
    }

    // MATCHES FRONTEND FETCH CALL: /api/v1/process/bpmn/generate
    @PostMapping(value = "/bpmn/generate", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> buildBpmn(@RequestBody com.pie.shared.dto.BpmnGenerateRequestPayload payload) {
        if (bpmnMapper == null || bpmnXmlService == null) {
            return ResponseEntity.internalServerError().build();
        }
        // Generate draft
        String draftXml = bpmnXmlService.generate(bpmnMapper.map(payload.graph()));
        
        // Refine with AI if knowledge is provided
        if (payload.knowledge() != null && aiBpmnRefinementService != null) {
            String refinedXml = aiBpmnRefinementService.refineBpmn(draftXml, payload.knowledge());
            return ResponseEntity.ok(refinedXml);
        }
        
        return ResponseEntity.ok(draftXml);
    }

    @PostMapping("/validate")
    public ResponseEntity<ProcessQualityReportDTO> validateGraph(@RequestBody ProcessGraphDTO graph) {
        return ResponseEntity.ok(qualityValidator.validateQuality(graph));
    }

    @PostMapping("/validate-knowledge")
    public ResponseEntity<ProcessQualityReportDTO> validateKnowledge(@RequestBody ProcessKnowledgeDTO knowledge) {
        ProcessGraphDTO graph = graphBuilder.build(knowledge);
        ProcessQualityReportDTO report = qualityValidator.validateQuality(graph);
        if (aiQualityService != null) {
            report = aiQualityService.enhance(knowledge, graph, report);
        }
        return ResponseEntity.ok(report);
    }

    @PostMapping("/review/summary")
    public ResponseEntity<com.pie.shared.dto.ReviewReportDTO> generateReviewSummary(@RequestBody TextPayload payload) {
        if (aiProcessReviewService == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(aiProcessReviewService.generateSummary(payload.content()));
    }

    public record TextPayload(String content) {
    }
}