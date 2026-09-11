package com.pie.backend.controller;

import com.pie.backend.service.DocumentIngestionService;
import com.pie.backend.service.ProcessGraphBuilder;
import com.pie.backend.service.ProcessQualityValidator;
import com.pie.shared.dto.CanonicalProcessGraph;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.shared.dto.ProcessQualityReportDTO;
import org.springframework.http.MediaType;
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
    private final com.pie.backend.service.BpmnXmlParser bpmnParser;

    public ProcessController(
            DocumentIngestionService ingestionService,
            ProcessGraphBuilder graphBuilder,
            ProcessQualityValidator qualityValidator,
            com.pie.backend.service.BpmnXmlParser bpmnParser) {
        this.ingestionService = ingestionService;
        this.graphBuilder = graphBuilder;
        this.qualityValidator = qualityValidator;
        this.bpmnParser = bpmnParser;
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
    public ResponseEntity<CanonicalProcessGraph> buildGraph(@RequestBody ProcessKnowledgeDTO knowledge) {
        return ResponseEntity.ok(graphBuilder.build(knowledge));
    }

    @PostMapping("/validate")
    public ResponseEntity<ProcessQualityReportDTO> validateGraph(@RequestBody CanonicalProcessGraph graph) {
        return ResponseEntity.ok(qualityValidator.validateQuality(graph));
    }

    @PostMapping("/validate-knowledge")
    public ResponseEntity<ProcessQualityReportDTO> validateKnowledge(@RequestBody ProcessKnowledgeDTO knowledge) {
        CanonicalProcessGraph graph = graphBuilder.build(knowledge);
        return ResponseEntity.ok(qualityValidator.validateQuality(graph));
    }

    @PostMapping("/import-bpmn")
    public ResponseEntity<BpmnImportResponseDTO> importBpmnXml(@RequestBody BpmnXmlPayload payload) {
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
            CanonicalProcessGraph graph,
            ProcessKnowledgeDTO knowledge,
            String processName,
            String processId,
            ProcessQualityReportDTO qualityReport
    ) {
    }
}