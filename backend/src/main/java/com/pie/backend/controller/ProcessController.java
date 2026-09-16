package com.pie.backend.controller;

import com.pie.backend.service.BpmnXmlParser;
import com.pie.backend.service.DocumentIngestionService;
import com.pie.backend.service.AiProcessQualityService;
import com.pie.backend.service.BpmnDomainModelMapper;
import com.pie.backend.service.BpmnXmlGenerationService;
import java.util.Map;
import com.pie.backend.service.FallbackMockPipeline;
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
    private final BpmnXmlParser bpmnParser;
    private final FallbackMockPipeline fallbackMockPipeline;
    private final com.pie.backend.service.AgentLogger agentLogger;

    public ProcessController(
            DocumentIngestionService ingestionService,
            ProcessGraphBuilder graphBuilder,
            ProcessQualityValidator qualityValidator) {
        this(ingestionService, graphBuilder, qualityValidator, null, null, null, null, null, null, null, null);
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
            com.pie.backend.service.AiProcessReviewService aiProcessReviewService,
            BpmnXmlParser bpmnParser,
            FallbackMockPipeline fallbackMockPipeline,
            com.pie.backend.service.AgentLogger agentLogger) {
        this.ingestionService = ingestionService;
        this.graphBuilder = graphBuilder;
        this.qualityValidator = qualityValidator;
        this.aiQualityService = aiQualityService;
        this.bpmnMapper = bpmnMapper;
        this.bpmnXmlService = bpmnXmlService;
        this.aiBpmnRefinementService = aiBpmnRefinementService;
        this.aiProcessReviewService = aiProcessReviewService;
        this.bpmnParser = bpmnParser;
        this.fallbackMockPipeline = fallbackMockPipeline;
        this.agentLogger = agentLogger;
    }

    @PostMapping("/extract-text")
    public ResponseEntity<Object> extractFromText(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @RequestBody TextPayload payload) {
        agentLogger.setSessionId(sessionId);
        agentLogger.logStart("INPUT_RECEIVED");
        agentLogger.logSuccess("INPUT_RECEIVED", 0);
        try {
            if (fallbackMockPipeline != null) {
                Object result = fallbackMockPipeline.executeWithFallback(
                    payload.content(), 
                    "knowledge", 
                    ProcessKnowledgeDTO.class, 
                    () -> ingestionService.ingestText(payload.content(), null, null)
                );
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.ok(ingestionService.ingestText(payload.content(), null, null));
        } finally {
            agentLogger.clearSession();
        }
    }

    @PostMapping(value = "/extract-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessKnowledgeDTO> extractFromFile(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @RequestParam("files") List<MultipartFile> files) {
        agentLogger.setSessionId(sessionId);
        agentLogger.logStart("INPUT_RECEIVED");
        agentLogger.logSuccess("INPUT_RECEIVED", 0);
        try {
            if (files == null || files.isEmpty()) {
                agentLogger.logError("INPUT_RECEIVED", 0, "NO_FILES");
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(ingestionService.ingestFilesCombined(files, null, null));
        } finally {
            agentLogger.clearSession();
        }
    }

    @PostMapping("/graph")
    public ResponseEntity<ProcessGraphDTO> buildGraph(@RequestBody ProcessKnowledgeDTO knowledge) {
        return ResponseEntity.ok(graphBuilder.build(knowledge));
    }

    // MATCHES FRONTEND FETCH CALL: /api/v1/process/bpmn/generate
    @PostMapping(value = "/bpmn/generate", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> buildBpmn(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @RequestBody com.pie.shared.dto.BpmnGenerateRequestPayload payload) {
        agentLogger.setSessionId(sessionId);
        agentLogger.logStart("BPMN_MODELLING");
        long start = System.currentTimeMillis();
        try {
            if (bpmnMapper == null || bpmnXmlService == null) {
                agentLogger.logError("BPMN_MODELLING", System.currentTimeMillis() - start, "SERVICES_NULL");
                return ResponseEntity.internalServerError().build();
            }

            if (fallbackMockPipeline != null) {
                String hint = "travel";
                if (payload.knowledge() != null && payload.knowledge().activities() != null) {
                    hint = String.join(" ", payload.knowledge().activities());
                }
                Object result = fallbackMockPipeline.executeWithFallback(
                    hint,
                    "bpmn",
                    String.class,
                    () -> {
                        String draftXml = bpmnXmlService.generate(bpmnMapper.map(payload.graph()));
                        if (payload.knowledge() != null && aiBpmnRefinementService != null) {
                            return aiBpmnRefinementService.refineBpmn(draftXml, payload.knowledge());
                        }
                        return draftXml;
                    }
                );
                // Note: FallbackMockPipeline logs success/error on fallback, but we should log success for the outer HTTP call if it didn't throw
                agentLogger.logSuccess("BPMN_MODELLING", System.currentTimeMillis() - start);
                return ResponseEntity.ok((String) result);
            }

            // Generate draft
            String draftXml = bpmnXmlService.generate(bpmnMapper.map(payload.graph()));
            
            // Refine with AI if knowledge is provided
            if (payload.knowledge() != null && aiBpmnRefinementService != null) {
                String refinedXml = aiBpmnRefinementService.refineBpmn(draftXml, payload.knowledge());
                agentLogger.logSuccess("BPMN_MODELLING", System.currentTimeMillis() - start);
                return ResponseEntity.ok(refinedXml);
            }
            
            agentLogger.logSuccess("BPMN_MODELLING", System.currentTimeMillis() - start);
            return ResponseEntity.ok(draftXml);
        } catch (Exception e) {
            agentLogger.logError("BPMN_MODELLING", System.currentTimeMillis() - start, e.getClass().getSimpleName());
            throw e;
        } finally {
            agentLogger.clearSession();
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<ProcessQualityReportDTO> validateGraph(@RequestBody ProcessGraphDTO graph) {
        return ResponseEntity.ok(qualityValidator.validateQuality(graph));
    }

    @PostMapping("/validate-knowledge")
    public ResponseEntity<Object> validateKnowledge(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @RequestBody ProcessKnowledgeDTO knowledge) {
        agentLogger.setSessionId(sessionId);
        agentLogger.logStart("PROCESS_INTELLIGENCE");
        long start = System.currentTimeMillis();
        try {
            if (fallbackMockPipeline != null) {
                // Find a scenario hint from the knowledge (like process name or first activity)
                String hint = "travel";
                if (knowledge.activities() != null && !knowledge.activities().isEmpty()) {
                    hint = String.join(" ", knowledge.activities());
                }

                Object result = fallbackMockPipeline.executeWithFallback(
                    hint,
                    "review",
                    ProcessQualityReportDTO.class,
                    () -> {
                        ProcessGraphDTO graph = graphBuilder.build(knowledge);
                        ProcessQualityReportDTO report = qualityValidator.validateQuality(graph);
                        if (aiQualityService != null) {
                            report = aiQualityService.enhance(knowledge, graph, report);
                        }
                        return report;
                    }
                );
                agentLogger.logSuccess("PROCESS_INTELLIGENCE", System.currentTimeMillis() - start);
                return ResponseEntity.ok(result);
            }

            ProcessGraphDTO graph = graphBuilder.build(knowledge);
            ProcessQualityReportDTO report = qualityValidator.validateQuality(graph);
            if (aiQualityService != null) {
                report = aiQualityService.enhance(knowledge, graph, report);
            }
            agentLogger.logSuccess("PROCESS_INTELLIGENCE", System.currentTimeMillis() - start);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            agentLogger.logError("PROCESS_INTELLIGENCE", System.currentTimeMillis() - start, e.getClass().getSimpleName());
            throw e;
        } finally {
            agentLogger.clearSession();
        }
    }

    @PostMapping("/review/summary")
    public ResponseEntity<?> generateReviewSummary(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @RequestBody TextPayload payload) {
        agentLogger.setSessionId(sessionId);
        agentLogger.logStart("PROCESS_REVIEW");
        long start = System.currentTimeMillis();
        try {
            if (aiProcessReviewService == null) {
                agentLogger.logError("PROCESS_REVIEW", System.currentTimeMillis() - start, "SERVICE_NULL");
                return ResponseEntity.internalServerError().build();
            }

            if (fallbackMockPipeline != null) {
                Object result = fallbackMockPipeline.executeWithFallback(
                    payload.content(),
                    "summary",
                    com.pie.shared.dto.ReviewReportDTO.class,
                    () -> aiProcessReviewService.generateSummary(payload.content())
                );
                agentLogger.logSuccess("PROCESS_REVIEW", System.currentTimeMillis() - start);
                return ResponseEntity.ok(result);
            }

            try {
                ResponseEntity<?> response = ResponseEntity.ok(aiProcessReviewService.generateSummary(payload.content()));
                agentLogger.logSuccess("PROCESS_REVIEW", System.currentTimeMillis() - start);
                return response;
            } catch (IllegalArgumentException e) {
                agentLogger.logError("PROCESS_REVIEW", System.currentTimeMillis() - start, "BAD_REQUEST");
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                agentLogger.logError("PROCESS_REVIEW", System.currentTimeMillis() - start, "INTERNAL_ERROR");
                return ResponseEntity.internalServerError().body("Error generating review summary.");
            }
        } finally {
            agentLogger.clearSession();
        }
    }

    @PostMapping("/import-bpmn")
    public ResponseEntity<BpmnImportResponseDTO> importBpmnXml(@RequestBody BpmnXmlPayload payload) {
        if (bpmnParser == null) return ResponseEntity.internalServerError().build();
        try {
            var parseResult = bpmnParser.parse(payload.xml());
            ProcessQualityReportDTO qualityReport = qualityValidator.validateQuality(parseResult.graph());
            return ResponseEntity.ok(new BpmnImportResponseDTO(
                    parseResult.graph(),
                    parseResult.knowledge(),
                    parseResult.processName(),
                    parseResult.processId(),
                    qualityReport
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/import-bpmn-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BpmnImportResponseDTO> importBpmnFile(@RequestParam("file") MultipartFile file) {
        if (bpmnParser == null) return ResponseEntity.internalServerError().build();
        try {
            String xmlContent = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var parseResult = bpmnParser.parse(xmlContent);
            ProcessQualityReportDTO qualityReport = qualityValidator.validateQuality(parseResult.graph());
            return ResponseEntity.ok(new BpmnImportResponseDTO(
                    parseResult.graph(),
                    parseResult.knowledge(),
                    parseResult.processName(),
                    parseResult.processId(),
                    qualityReport
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record TextPayload(String content) {
    }

    public record BpmnXmlPayload(String xml) {
    }

    public record BpmnImportResponseDTO(
            ProcessGraphDTO graph,
            ProcessKnowledgeDTO knowledge,
            String processName,
            String processId,
            ProcessQualityReportDTO qualityReport
    ) {
    }
}