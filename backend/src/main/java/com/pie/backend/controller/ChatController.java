package com.pie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.backend.service.BpmnEditService;
import com.pie.backend.service.BpmnVersionService;
import com.pie.backend.service.BpmnXmlParser;
import com.pie.backend.service.BpmnXmlGenerationService;
import com.pie.backend.service.BpmnDomainModelMapper;
import com.pie.backend.service.ProcessQualityValidator;
import com.pie.shared.dto.GraphEdge;
import com.pie.shared.dto.GraphNode;
import com.pie.shared.dto.ProcessGraphDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.shared.dto.ProcessQualityReportDTO;
import com.pie.shared.dto.ValidationIssueDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/process")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_HISTORY_TURNS = 8;
    private static final Pattern UNDO_PATTERN = Pattern.compile("(?i)^(?:undo|revert)(?:\\s+(?:that|last|the\\s+last)?(?:\\s+(\\d+))?\\s*(?:changes?|steps?)?)?\\.?$");

    private final ChatClient chatClient;
    private final BpmnEditService bpmnEditService;
    private final BpmnVersionService versionService;
    private final BpmnXmlParser bpmnParser;
    private final ProcessQualityValidator qualityValidator;
    private final BpmnXmlGenerationService generationService;
    private final BpmnDomainModelMapper bpmnMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("classpath:prompts/chat-prompt.txt")
    private Resource chatPromptResource;

    @Value("classpath:prompts/chat-edit-prompt.txt")
    private Resource chatEditPromptResource;

    public ChatController(ChatClient.Builder chatClientBuilder,
                          BpmnEditService bpmnEditService,
                          BpmnVersionService versionService,
                          BpmnXmlParser bpmnParser,
                          ProcessQualityValidator qualityValidator,
                          BpmnXmlGenerationService generationService,
                          BpmnDomainModelMapper bpmnMapper) {
        this.chatClient = chatClientBuilder.build();
        this.bpmnEditService = bpmnEditService;
        this.versionService = versionService;
        this.bpmnParser = bpmnParser;
        this.qualityValidator = qualityValidator;
        this.generationService = generationService;
        this.bpmnMapper = bpmnMapper;
    }

    private ChatContext enrichContext(ChatContext ctx) {
        if (ctx == null) return null;
        boolean hasXml = ctx.bpmnXml() != null && !ctx.bpmnXml().isBlank();
        if (!hasXml) return ctx;

        boolean needsGraph = ctx.graph() == null
                || ctx.graph().getNodes() == null
                || ctx.graph().getNodes().isEmpty();
        boolean needsKnowledge = ctx.knowledge() == null
                || ctx.knowledge().activities() == null
                || ctx.knowledge().activities().isEmpty();
        boolean needsReport = ctx.qualityReport() == null;

        if (!needsGraph && !needsKnowledge && !needsReport) return ctx;

        try {
            var parsed = bpmnParser.parse(ctx.bpmnXml());
            ProcessGraphDTO graph = needsGraph ? parsed.graph() : ctx.graph();
            ProcessKnowledgeDTO knowledge = needsKnowledge ? parsed.knowledge() : ctx.knowledge();
            String name = (ctx.processName() != null && !ctx.processName().isBlank())
                    ? ctx.processName() : parsed.processName();
            ProcessQualityReportDTO report = ctx.qualityReport();
            if (needsReport && graph != null) {
                try {
                    report = qualityValidator.validateQuality(graph);
                } catch (Exception ignored) {}
            }
            return new ChatContext(name, ctx.bpmnXml(), knowledge, graph, report, ctx.selectedElement(), ctx.sourceText());
        } catch (Exception e) {
            log.warn("Chat context BPMN enrichment failed: {}", e.getMessage());
            return ctx;
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResponse("Please ask a question.", null, null));
        }

        String q = request.question().trim();
        String processId = resolveProcessId(request.context());

        // Check for natural language Undo request
        Matcher undoMatcher = UNDO_PATTERN.matcher(q);
        if (undoMatcher.matches()) {
            int steps = 1;
            String stepGroup = undoMatcher.group(1);
            if (stepGroup != null && !stepGroup.isBlank()) {
                try { steps = Integer.parseInt(stepGroup); } catch (Exception ignored) {}
            }
            var undone = versionService.undo(processId, steps);
            if (undone.isPresent()) {
                var v = undone.get();
                return ResponseEntity.ok(new ChatResponse(
                        "Reverted to Version " + v.versionNumber() + " (" + v.description() + ").",
                        v.xml(),
                        v.versionNumber()
                ));
            } else {
                return ResponseEntity.ok(new ChatResponse(
                        "No prior version found to undo.",
                        null,
                        null
                ));
            }
        }

        try {
            String systemPrompt = loadSystemPrompt();
            ChatContext ctx = enrichContext(request.context());
            String contextBlock = buildContextBlock(ctx);
            String historyBlock = buildHistoryBlock(request.history());

            String userMessage = "CONTEXT:\n" + contextBlock
                    + "\n\nCONVERSATION HISTORY:\n" + historyBlock
                    + "\n\nUSER QUESTION:\n" + q;

            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                answer = "I could not generate an answer. Please try rephrasing your question.";
            }

            return ResponseEntity.ok(new ChatResponse(answer.trim(), null, null));
        } catch (Exception e) {
            log.error("Chat call failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ChatResponse(
                    "The AI service is temporarily unavailable. Please make sure Ollama is running and try again.", null, null));
        }
    }

    @PostMapping("/chat-edit")
    public ResponseEntity<ChatEditResponse> chatEdit(@RequestBody ChatEditRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ChatEditResponse("Please describe what you want to change.", List.of(), List.of(), null, false, "Empty request", null, null));
        }
        if (request.context() == null || request.context().bpmnXml() == null || request.context().bpmnXml().isBlank()) {
            return ResponseEntity.ok(new ChatEditResponse(
                    "I need a BPMN model loaded before I can make edits. Please import or generate a BPMN first.",
                    List.of(), List.of(), null, false, null, null, null));
        }

        String q = request.question().trim();
        String processId = resolveProcessId(request.context());

        // Check for direct undo command in edit mode
        Matcher undoMatcher = UNDO_PATTERN.matcher(q);
        if (undoMatcher.matches()) {
            int steps = 1;
            String stepGroup = undoMatcher.group(1);
            if (stepGroup != null && !stepGroup.isBlank()) {
                try { steps = Integer.parseInt(stepGroup); } catch (Exception ignored) {}
            }
            var undone = versionService.undo(processId, steps);
            if (undone.isPresent()) {
                var v = undone.get();
                return ResponseEntity.ok(new ChatEditResponse(
                        "Reverted to Version " + v.versionNumber() + " (" + v.description() + ").",
                        List.of("Revert to Version " + v.versionNumber()),
                        List.of(Map.of("op", "undo", "steps", steps)),
                        v.xml(),
                        true,
                        null,
                        null,
                        v.versionNumber()
                ));
            }
        }

        try {
            String systemPrompt = loadEditPrompt();
            ChatContext enriched = enrichContext(request.context());
            String contextBlock = buildContextBlock(enriched);
            String historyBlock = buildHistoryBlock(request.history());

            String userMessage = "CONTEXT:\n" + contextBlock
                    + "\n\nCONVERSATION HISTORY:\n" + historyBlock
                    + "\n\nUSER REQUEST:\n" + q;

            String raw = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (raw == null || raw.isBlank()) {
                return ResponseEntity.ok(new ChatEditResponse(
                        "The model returned no response. Please try rephrasing.", List.of(), List.of(), null, false, "Empty LLM response", null, null));
            }

            String jsonBlock = extractJson(raw.trim());
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(jsonBlock, Map.class);

            String plan = String.valueOf(parsed.getOrDefault("plan", "Proposed change"));
            @SuppressWarnings("unchecked")
            List<String> steps = (List<String>) parsed.getOrDefault("steps", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> operations = (List<Map<String, Object>>) parsed.getOrDefault("operations", new ArrayList<>());

            // Check if any operation is an undo op
            if (!operations.isEmpty() && "undo".equalsIgnoreCase(String.valueOf(operations.get(0).get("op")))) {
                int undoSteps = 1;
                Object s = operations.get(0).get("steps");
                if (s instanceof Number num) undoSteps = num.intValue();
                var undone = versionService.undo(processId, undoSteps);
                if (undone.isPresent()) {
                    var v = undone.get();
                    return ResponseEntity.ok(new ChatEditResponse(
                            "Reverted to Version " + v.versionNumber() + " (" + v.description() + ").",
                            steps,
                            operations,
                            v.xml(),
                            true,
                            null,
                            null,
                            v.versionNumber()
                    ));
                }
            }

            String updatedXml = null;
            boolean applyRequested = request.autoApply();
            String errorNote = null;
            ValidationSummary validationSummary = null;
            Integer newVersionNumber = null;

            if (applyRequested && !operations.isEmpty()) {
                try {
                    ApplyResult applyResult = executeOperationsAndValidate(
                            request.context().bpmnXml(), operations, plan, processId);
                    updatedXml = applyResult.xml();
                    validationSummary = applyResult.validation();
                    newVersionNumber = applyResult.versionNumber();
                    if (!applyResult.failed().isEmpty()) {
                        errorNote = "Some operations could not be applied: " + String.join("; ", applyResult.failed());
                    }
                } catch (Exception applyErr) {
                    log.error("Applying edit ops failed: {}", applyErr.getMessage(), applyErr);
                    errorNote = "Failed to apply changes: " + applyErr.getMessage();
                }
            }

            return ResponseEntity.ok(new ChatEditResponse(
                    plan, steps, operations, updatedXml, applyRequested, errorNote, validationSummary, newVersionNumber));

        } catch (Exception e) {
            log.error("Chat edit call failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ChatEditResponse(
                    "I could not process that edit request. Make sure Ollama is running and try a more specific instruction.",
                    List.of(), List.of(), null, false, e.getMessage(), null, null));
        }
    }

    @PostMapping("/apply-edits")
    public ResponseEntity<ApplyEditsResponse> applyEdits(@RequestBody ApplyEditsRequest request) {
        if (request == null || request.bpmnXml() == null || request.bpmnXml().isBlank()) {
            return ResponseEntity.badRequest().body(new ApplyEditsResponse(null, List.of(), List.of("Missing BPMN XML"), null, null));
        }
        try {
            String processId = request.processId() != null && !request.processId().isBlank() ? request.processId() : "default";
            ApplyResult applyResult = executeOperationsAndValidate(
                    request.bpmnXml(), request.operations(), request.plan() != null ? request.plan() : "Manual edit applied", processId);

            return ResponseEntity.ok(new ApplyEditsResponse(
                    applyResult.xml(),
                    applyResult.applied(),
                    applyResult.failed(),
                    applyResult.validation(),
                    applyResult.versionNumber()
            ));
        } catch (Exception e) {
            log.error("Apply edits failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ApplyEditsResponse(null, List.of(), List.of(e.getMessage()), null, null));
        }
    }

    @PostMapping("/undo")
    public ResponseEntity<UndoResponse> undo(@RequestBody UndoRequest request) {
        String processId = request != null && request.processId() != null ? request.processId() : "default";
        int steps = request != null && request.steps() > 0 ? request.steps() : 1;

        var versionOpt = versionService.undo(processId, steps);
        if (versionOpt.isEmpty()) {
            return ResponseEntity.ok(new UndoResponse(false, "No previous version available", null, null));
        }
        var v = versionOpt.get();
        return ResponseEntity.ok(new UndoResponse(
                true,
                "Restored Version " + v.versionNumber() + " (" + v.description() + ")",
                v.xml(),
                v.versionNumber()
        ));
    }

    @PostMapping("/redo")
    public ResponseEntity<UndoResponse> redo(@RequestBody UndoRequest request) {
        String processId = request != null && request.processId() != null ? request.processId() : "default";
        int steps = request != null && request.steps() > 0 ? request.steps() : 1;

        var versionOpt = versionService.redo(processId, steps);
        if (versionOpt.isEmpty()) {
            return ResponseEntity.ok(new UndoResponse(false, "No forward version available", null, null));
        }
        var v = versionOpt.get();
        return ResponseEntity.ok(new UndoResponse(
                true,
                "Restored Version " + v.versionNumber() + " (" + v.description() + ")",
                v.xml(),
                v.versionNumber()
        ));
    }

    @GetMapping("/versions")
    public ResponseEntity<BpmnVersionService.VersionState> getVersions(@RequestParam(defaultValue = "default") String processId) {
        return ResponseEntity.ok(versionService.getVersionState(processId));
    }

    // =========================================================================
    // EXECUTION & VALIDATION PIPELINE
    // =========================================================================

    private record ApplyResult(
            String xml,
            List<String> applied,
            List<String> failed,
            ValidationSummary validation,
            int versionNumber
    ) {}

    private ApplyResult executeOperationsAndValidate(
            String initialXml,
            List<Map<String, Object>> operations,
            String plan,
            String processId) throws Exception {

        // 1. Apply graph operations directly to DOM
        BpmnEditService.EditResult editResult = bpmnEditService.applyOperations(initialXml, operations);

        // 2. Parse resulting XML into structured graph
        var parsedGraph = bpmnParser.parse(editResult.xml());

        // 3. Validate process quality & defects
        ProcessQualityReportDTO report = null;
        try {
            report = qualityValidator.validateQuality(parsedGraph.graph());
        } catch (Exception e) {
            log.warn("Post-edit quality validation warning: {}", e.getMessage());
        }

        // 4. Generate refreshed layout with clean BPMNDI and routing
        String updatedXml;
        try {
            updatedXml = generationService.generate(bpmnMapper.map(parsedGraph.graph()));
        } catch (Exception genErr) {
            log.warn("Generation failed, falling back to modified DOM XML: {}", genErr.getMessage());
            updatedXml = editResult.xml();
        }

        // 5. Save new version into version stack
        var savedVersion = versionService.saveVersion(processId, updatedXml, plan, operations);

        // 6. Build validation summary
        ValidationSummary summary = null;
        if (report != null) {
            List<String> topIssues = report.issues() != null
                    ? report.issues().stream().limit(5).map(ValidationIssueDTO::issue).collect(Collectors.toList())
                    : List.of();
            summary = new ValidationSummary(
                    report.valid(),
                    report.qualityScore(),
                    report.issues() != null ? report.issues().size() : 0,
                    topIssues
            );
        }

        return new ApplyResult(updatedXml, editResult.applied(), editResult.failed(), summary, savedVersion.versionNumber());
    }

    private String resolveProcessId(ChatContext ctx) {
        if (ctx != null && ctx.processName() != null && !ctx.processName().isBlank()) {
            return ctx.processName().replaceAll("[^a-zA-Z0-9_-]", "_");
        }
        return "default";
    }

    private String extractJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        s = s.trim();

        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    // =========================================================================
    // CONTEXT BUILDER: SEMANTIC BPMN REPRESENTATION
    // =========================================================================

    private String buildContextBlock(ChatContext ctx) {
        if (ctx == null) {
            return "(no process context provided)";
        }

        StringBuilder sb = new StringBuilder();

        if (ctx.processName() != null && !ctx.processName().isBlank()) {
            sb.append("PROCESS NAME: ").append(ctx.processName()).append("\n\n");
        }

        // 1. Selected Element Context (Critical for "this", "that", "here")
        if (ctx.selectedElement() != null && ctx.selectedElement().name() != null) {
            SelectedElementContext sel = ctx.selectedElement();
            sb.append("CURRENTLY SELECTED ELEMENT (FOCUS OF USER COMMANDS):\n");
            sb.append("- ID: ").append(sel.id()).append("\n");
            sb.append("- Name: ").append(sel.name()).append("\n");
            sb.append("- Type: ").append(sel.type()).append("\n");
            if (sel.incoming() != null && !sel.incoming().isEmpty()) {
                sb.append("- Incoming from: ").append(String.join(", ", sel.incoming())).append("\n");
            }
            if (sel.outgoing() != null && !sel.outgoing().isEmpty()) {
                sb.append("- Outgoing to: ").append(String.join(", ", sel.outgoing())).append("\n");
            }
            if (sel.x() != null && sel.y() != null) {
                sb.append("- Position: x=").append(sel.x()).append(", y=").append(sel.y()).append("\n");
            }
            sb.append("\n");
        }

        // 2. Semantic BPMN Process Graph (Structured, not raw XML)
        if (ctx.graph() != null && ctx.graph().getNodes() != null && !ctx.graph().getNodes().isEmpty()) {
            sb.append("BPMN PROCESS STRUCTURE (SEMANTIC GRAPH):\n");
            sb.append("Elements (Nodes):\n");
            for (GraphNode node : ctx.graph().getNodes()) {
                String nodeType = node.getType() != null ? node.getType().name() : "Task";
                if (node.getMetadata() != null && node.getMetadata().getTaskType() != null) {
                    nodeType = node.getMetadata().getTaskType();
                } else if (node.getMetadata() != null && node.getMetadata().getGatewayType() != null) {
                    nodeType = node.getMetadata().getGatewayType().name() + "Gateway";
                } else if (node.getMetadata() != null && node.getMetadata().getEventType() != null) {
                    nodeType = node.getMetadata().getEventType().name() + "Event";
                }

                String role = node.getMetadata() != null ? node.getMetadata().getRoleRef() : null;
                List<String> incoming = findIncomingLabels(ctx.graph(), node.getId());
                List<String> outgoing = findOutgoingLabels(ctx.graph(), node.getId());

                sb.append("  • [id=").append(node.getId()).append("] ")
                        .append(node.getLabel()).append(" (").append(nodeType).append(")");
                if (role != null) sb.append(" [Role: ").append(role).append("]");
                if (!incoming.isEmpty()) sb.append(" | In: ").append(String.join(", ", incoming));
                if (!outgoing.isEmpty()) sb.append(" | Out: ").append(String.join(", ", outgoing));
                sb.append("\n");
            }

            if (ctx.graph().getEdges() != null && !ctx.graph().getEdges().isEmpty()) {
                sb.append("\nSequence Flows:\n");
                for (GraphEdge edge : ctx.graph().getEdges()) {
                    sb.append("  • ").append(edge.getFrom()).append(" ──");
                    if (edge.getLabel() != null && !edge.getLabel().isBlank()) {
                        sb.append("[").append(edge.getLabel()).append("]──");
                    }
                    sb.append("→ ").append(edge.getTo()).append("\n");
                }
            }
            sb.append("\n");
        }

        // 3. Source Document / SOP Text (For Traceability)
        if (ctx.sourceText() != null && !ctx.sourceText().isBlank()) {
            String text = ctx.sourceText();
            if (text.length() > 3500) {
                text = text.substring(0, 3500) + "\n... [truncated] ...";
            }
            sb.append("SOURCE DOCUMENT / SOP TEXT:\n").append(text).append("\n\n");
        }

        // 4. Extracted Process Knowledge (Entities & Rules)
        if (ctx.knowledge() != null) {
            sb.append("EXTRACTED PROCESS KNOWLEDGE:\n");
            appendList(sb, "Activities", ctx.knowledge().activities());
            appendList(sb, "Actors / Roles", ctx.knowledge().actors());
            appendList(sb, "Gateways / Decisions", ctx.knowledge().gateways());
            appendList(sb, "Business Rules", ctx.knowledge().businessRules());
            appendList(sb, "Identified Risks", ctx.knowledge().risks());
            appendList(sb, "Systems", ctx.knowledge().systems());
            sb.append("\n");
        }

        // 5. Machine-Verified Quality Defects
        if (ctx.qualityReport() != null && ctx.qualityReport().issues() != null) {
            sb.append("QUALITY & DEFECT REPORT (VALIDATOR AUDIT):\n");
            sb.append("- Quality Score: ").append(ctx.qualityReport().qualityScore()).append("/100\n");
            sb.append("- Status: ").append(ctx.qualityReport().valid() ? "COMPLIANT" : "DEFECTS DETECTED").append("\n");
            for (ValidationIssueDTO issue : ctx.qualityReport().issues()) {
                sb.append("  * [").append(issue.severity()).append("] ")
                        .append(issue.ruleId()).append(": ")
                        .append(issue.issue()).append("\n");
            }
            sb.append("\n");
        }

        if (sb.length() == 0) {
            return "(no process context provided)";
        }
        return sb.toString();
    }

    private List<String> findIncomingLabels(ProcessGraphDTO graph, String nodeId) {
        if (graph.getEdges() == null) return List.of();
        return graph.getEdges().stream()
                .filter(e -> nodeId.equals(e.getTo()))
                .map(GraphEdge::getFrom)
                .collect(Collectors.toList());
    }

    private List<String> findOutgoingLabels(ProcessGraphDTO graph, String nodeId) {
        if (graph.getEdges() == null) return List.of();
        return graph.getEdges().stream()
                .filter(e -> nodeId.equals(e.getFrom()))
                .map(e -> e.getTo() + (e.getLabel() != null ? " [" + e.getLabel() + "]" : ""))
                .collect(Collectors.toList());
    }

    private void appendList(StringBuilder sb, String label, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append("- ").append(label).append(": ").append(String.join(", ", items)).append("\n");
    }

    private String buildHistoryBlock(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "(no prior messages)";
        }
        int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            String role = "user".equalsIgnoreCase(m.role()) ? "User" : "Assistant";
            sb.append(role).append(": ").append(m.content()).append("\n");
        }
        return sb.toString();
    }

    private String loadSystemPrompt() {
        return loadPrompt(chatPromptResource, "prompts/chat-prompt.txt",
                "You are a BPMN process intelligence copilot. Answer the user's question using the provided context.");
    }

    private String loadEditPrompt() {
        return loadPrompt(chatEditPromptResource, "prompts/chat-edit-prompt.txt",
                "You are a BPMN edit assistant. Respond with a strict JSON object containing 'plan', 'steps' and 'operations'.");
    }

    private String loadPrompt(Resource primary, String classpathPath, String fallbackText) {
        try {
            if (primary != null && primary.exists()) {
                return new String(primary.getContentAsByteArray(), StandardCharsets.UTF_8);
            }
            Resource fallback = new ClassPathResource(classpathPath);
            return new String(fallback.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return fallbackText;
        }
    }

    // =========================================================================
    // DTO RECORDS
    // =========================================================================

    public record SelectedElementContext(
            String id,
            String name,
            String type,
            List<String> incoming,
            List<String> outgoing,
            Integer x,
            Integer y
    ) {}

    public record ChatRequest(
            String question,
            List<ChatMessage> history,
            ChatContext context
    ) {}

    public record ChatMessage(String role, String content) {}

    public record ChatContext(
            String processName,
            String bpmnXml,
            ProcessKnowledgeDTO knowledge,
            ProcessGraphDTO graph,
            ProcessQualityReportDTO qualityReport,
            SelectedElementContext selectedElement,
            String sourceText
    ) {}

    public record ChatResponse(
            String answer,
            String updatedXml,
            Integer currentVersion
    ) {}

    public record ChatEditRequest(
            String question,
            List<ChatMessage> history,
            ChatContext context,
            boolean autoApply
    ) {}

    public record ValidationSummary(
            boolean valid,
            int qualityScore,
            int issueCount,
            List<String> topIssues
    ) {}

    public record ChatEditResponse(
            String plan,
            List<String> steps,
            List<Map<String, Object>> operations,
            String updatedXml,
            boolean applied,
            String error,
            ValidationSummary validation,
            Integer currentVersion
    ) {}

    public record ApplyEditsRequest(
            String bpmnXml,
            List<Map<String, Object>> operations,
            String plan,
            String processId
    ) {}

    public record ApplyEditsResponse(
            String updatedXml,
            List<String> applied,
            List<String> failed,
            ValidationSummary validation,
            Integer currentVersion
    ) {}

    public record UndoRequest(
            String processId,
            int steps
    ) {}

    public record UndoResponse(
            boolean success,
            String message,
            String updatedXml,
            Integer currentVersion
    ) {}
}
