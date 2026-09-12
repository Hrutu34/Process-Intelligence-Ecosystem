package com.pie.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.backend.service.BpmnEditService;
import com.pie.shared.dto.CanonicalProcessGraph;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.shared.dto.ProcessQualityReportDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/process")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_XML_CHARS = 12000;
    private static final int MAX_HISTORY_TURNS = 8;

    private final ChatClient chatClient;
    private final BpmnEditService bpmnEditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("classpath:prompts/chat-prompt.txt")
    private Resource chatPromptResource;

    @Value("classpath:prompts/chat-edit-prompt.txt")
    private Resource chatEditPromptResource;

    @Value("classpath:prompts/narrative-prompt.txt")
    private Resource narrativePromptResource;

    public ChatController(ChatClient.Builder chatClientBuilder, BpmnEditService bpmnEditService) {
        this.chatClient = chatClientBuilder.build();
        this.bpmnEditService = bpmnEditService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResponse("Please ask a question."));
        }

        boolean hasContext = hasProcessContext(request.context());
        boolean questionSeemsProcessSpecific = looksProcessSpecific(request.question());

        // Refuse to fabricate: if user asks about "this process" but nothing is loaded, tell them.
        if (!hasContext && questionSeemsProcessSpecific) {
            return ResponseEntity.ok(new ChatResponse(
                    "I do not have any process loaded right now. Please import a BPMN file or paste a process description first — then ask me about it. "
                            + "If you meant a general BPMN question, rephrase without 'this process'."));
        }

        try {
            String systemPrompt = loadSystemPrompt();
            String contextBlock = buildContextBlock(request.context());
            String historyBlock = buildHistoryBlock(request.history());

            log.info("Chat request: contextChars={}, historyTurns={}, question=\"{}\"",
                    contextBlock.length(),
                    request.history() != null ? request.history().size() : 0,
                    request.question());

            String userMessage = "CONTEXT:\n" + contextBlock
                    + "\n\nCONVERSATION HISTORY:\n" + historyBlock
                    + "\n\nUSER QUESTION:\n" + request.question()
                    + (hasContext
                        ? ""
                        : "\n\nNOTE: No process is loaded. Answer only from general BPMN 2.0 knowledge and say so.");

            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                answer = "I could not generate an answer. Please try rephrasing your question.";
            }

            return ResponseEntity.ok(new ChatResponse(answer.trim()));
        } catch (Exception e) {
            log.error("Chat call failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ChatResponse(
                    "The AI service is temporarily unavailable. Please make sure Ollama is running and try again."));
        }
    }

    private boolean hasProcessContext(ChatContext ctx) {
        if (ctx == null) return false;
        boolean hasXml = ctx.bpmnXml() != null && !ctx.bpmnXml().isBlank();
        boolean hasGraph = ctx.graph() != null && ctx.graph().getNodes() != null && !ctx.graph().getNodes().isEmpty();
        boolean hasKnowledge = ctx.knowledge() != null
                && ctx.knowledge().activities() != null
                && !ctx.knowledge().activities().isEmpty();
        return hasXml || hasGraph || hasKnowledge;
    }

    private boolean looksProcessSpecific(String question) {
        if (question == null) return false;
        String q = question.toLowerCase(java.util.Locale.ROOT);
        String[] triggers = {
                "this process", "this bpmn", "this diagram", "this flow", "this model",
                "the process", "the bpmn", "the diagram", "the flow", "the model",
                "summarize", "summarise", "explain the", "what is wrong", "what defects",
                "list defects", "list the defects", "audit", "review this", "quality report"
        };
        for (String t : triggers) if (q.contains(t)) return true;
        return false;
    }

    private String loadSystemPrompt() {
        return loadPrompt(chatPromptResource, "prompts/chat-prompt.txt",
                "You are a BPMN process intelligence copilot. Answer the user's question using the provided context.");
    }

    private String loadEditPrompt() {
        return loadPrompt(chatEditPromptResource, "prompts/chat-edit-prompt.txt",
                "You are a BPMN edit assistant. Respond with a strict JSON object containing 'plan' and 'operations'.");
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

    @PostMapping("/chat-edit")
    public ResponseEntity<ChatEditResponse> chatEdit(@RequestBody ChatEditRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new ChatEditResponse("Please describe what you want to change.", List.of(), null, false, "Empty request"));
        }
        if (request.context() == null || request.context().bpmnXml() == null || request.context().bpmnXml().isBlank()) {
            return ResponseEntity.ok(new ChatEditResponse(
                    "I need a BPMN model loaded before I can make edits. Please import or generate a BPMN first.",
                    List.of(), null, false, null));
        }

        try {
            String systemPrompt = loadEditPrompt();
            String contextBlock = buildContextBlock(request.context());
            String historyBlock = buildHistoryBlock(request.history());

            String userMessage = "CONTEXT:\n" + contextBlock
                    + "\n\nCONVERSATION HISTORY:\n" + historyBlock
                    + "\n\nUSER REQUEST:\n" + request.question();

            String raw = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (raw == null || raw.isBlank()) {
                return ResponseEntity.ok(new ChatEditResponse(
                        "The model returned no response. Please try rephrasing.", List.of(), null, false, "Empty LLM response"));
            }

            String jsonBlock = extractJson(raw.trim());
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(jsonBlock, Map.class);

            String plan = String.valueOf(parsed.getOrDefault("plan", "Proposed change"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> operations = (List<Map<String, Object>>) parsed.getOrDefault("operations", new ArrayList<>());

            String updatedXml = null;
            boolean applyRequested = request.autoApply();
            String errorNote = null;

            if (applyRequested && !operations.isEmpty()) {
                try {
                    BpmnEditService.EditResult result = bpmnEditService.applyOperations(
                            request.context().bpmnXml(), operations);
                    updatedXml = result.xml();
                    if (!result.failed().isEmpty()) {
                        errorNote = "Some operations failed: " + String.join("; ", result.failed());
                    }
                } catch (Exception applyErr) {
                    log.error("Applying edit ops failed: {}", applyErr.getMessage(), applyErr);
                    errorNote = "Failed to apply changes: " + applyErr.getMessage();
                }
            }

            return ResponseEntity.ok(new ChatEditResponse(plan, operations, updatedXml, applyRequested, errorNote));

        } catch (Exception e) {
            log.error("Chat edit call failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ChatEditResponse(
                    "I could not process that edit request. Make sure Ollama is running and try a more specific instruction.",
                    List.of(), null, false, e.getMessage()));
        }
    }

    @PostMapping("/narrative")
    public ResponseEntity<NarrativeResponse> narrative(@RequestBody NarrativeRequest request) {
        if (request == null || request.context() == null) {
            return ResponseEntity.badRequest().body(new NarrativeResponse(null, "Missing context"));
        }
        try {
            String systemPrompt = loadPrompt(narrativePromptResource, "prompts/narrative-prompt.txt",
                    "Generate a plain-English business narrative for the BPMN process described in the CONTEXT.");
            String contextBlock = buildContextBlock(request.context());
            String userMessage = "CONTEXT:\n" + contextBlock
                    + "\n\nGenerate the narrative now using the required markdown sections.";

            String markdown = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (markdown == null || markdown.isBlank()) {
                return ResponseEntity.ok(new NarrativeResponse(null, "Model returned empty response"));
            }
            return ResponseEntity.ok(new NarrativeResponse(markdown.trim(), null));
        } catch (Exception e) {
            log.error("Narrative generation failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new NarrativeResponse(null,
                    "AI service unavailable. Make sure Ollama is running."));
        }
    }

    @PostMapping("/apply-edits")
    public ResponseEntity<ApplyEditsResponse> applyEdits(@RequestBody ApplyEditsRequest request) {
        if (request == null || request.bpmnXml() == null || request.bpmnXml().isBlank()) {
            return ResponseEntity.badRequest().body(new ApplyEditsResponse(null, List.of(), List.of("Missing BPMN XML")));
        }
        try {
            BpmnEditService.EditResult result = bpmnEditService.applyOperations(
                    request.bpmnXml(), request.operations());
            return ResponseEntity.ok(new ApplyEditsResponse(result.xml(), result.applied(), result.failed()));
        } catch (Exception e) {
            log.error("Apply edits failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ApplyEditsResponse(null, List.of(), List.of(e.getMessage())));
        }
    }

    private String extractJson(String raw) {
        String s = raw.trim();
        // Strip markdown fences if present
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

    private String buildContextBlock(ChatContext ctx) {
        if (ctx == null) {
            return "(no process context provided)";
        }

        StringBuilder sb = new StringBuilder();

        if (ctx.processName() != null && !ctx.processName().isBlank()) {
            sb.append("Process name: ").append(ctx.processName()).append("\n\n");
        }

        if (ctx.knowledge() != null) {
            sb.append("EXTRACTED KNOWLEDGE:\n");
            appendList(sb, "Activities", ctx.knowledge().activities());
            appendList(sb, "Actors", ctx.knowledge().actors());
            appendList(sb, "Roles", ctx.knowledge().roles());
            appendList(sb, "Gateways / Decisions", ctx.knowledge().gateways());
            appendList(sb, "Systems", ctx.knowledge().systems());
            appendList(sb, "Events", ctx.knowledge().events());
            appendList(sb, "Inputs", ctx.knowledge().inputs());
            appendList(sb, "Outputs", ctx.knowledge().outputs());
            appendList(sb, "Business Rules", ctx.knowledge().businessRules());
            appendList(sb, "Risks", ctx.knowledge().risks());
            sb.append("\n");
        }

        if (ctx.graph() != null && ctx.graph().getNodes() != null) {
            sb.append("GRAPH SUMMARY:\n");
            sb.append("- Nodes: ").append(ctx.graph().getNodes().size()).append("\n");
            sb.append("- Edges: ")
                    .append(ctx.graph().getEdges() != null ? ctx.graph().getEdges().size() : 0)
                    .append("\n");
            long activityCount = ctx.graph().getNodes().stream()
                    .filter(n -> n.getType() != null && "Activity".equalsIgnoreCase(n.getType().name()))
                    .count();
            long gatewayCount = ctx.graph().getNodes().stream()
                    .filter(n -> n.getType() != null && "Gateway".equalsIgnoreCase(n.getType().name()))
                    .count();
            long roleCount = ctx.graph().getNodes().stream()
                    .filter(n -> n.getType() != null && "Role".equalsIgnoreCase(n.getType().name()))
                    .count();
            sb.append("- Activities: ").append(activityCount)
                    .append(", Gateways: ").append(gatewayCount)
                    .append(", Roles: ").append(roleCount).append("\n\n");
        }

        if (ctx.qualityReport() != null && ctx.qualityReport().issues() != null) {
            sb.append("QUALITY REPORT:\n");
            sb.append("- Score: ").append(ctx.qualityReport().qualityScore()).append("/100\n");
            sb.append("- Total issues: ").append(ctx.qualityReport().issues().size()).append("\n");
            ctx.qualityReport().issues().stream().limit(20).forEach(issue ->
                    sb.append("  * [").append(issue.severity()).append("] ")
                            .append(issue.ruleId()).append(": ")
                            .append(issue.issue()).append("\n"));
            sb.append("\n");
        }

        if (ctx.bpmnXml() != null && !ctx.bpmnXml().isBlank()) {
            String xml = ctx.bpmnXml();
            if (xml.length() > MAX_XML_CHARS) {
                xml = xml.substring(0, MAX_XML_CHARS) + "\n... [truncated] ...";
            }
            sb.append("BPMN XML:\n").append(xml).append("\n");
        }

        if (sb.length() == 0) {
            return "(no process context provided)";
        }
        return sb.toString();
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

    public record ChatRequest(String question, List<ChatMessage> history, ChatContext context) {}

    public record ChatMessage(String role, String content) {}

    public record ChatContext(
            String processName,
            String bpmnXml,
            ProcessKnowledgeDTO knowledge,
            CanonicalProcessGraph graph,
            ProcessQualityReportDTO qualityReport
    ) {}

    public record ChatResponse(String answer) {}

    public record ChatEditRequest(
            String question,
            List<ChatMessage> history,
            ChatContext context,
            boolean autoApply
    ) {}

    public record ChatEditResponse(
            String plan,
            List<Map<String, Object>> operations,
            String updatedXml,
            boolean applied,
            String error
    ) {}

    public record ApplyEditsRequest(String bpmnXml, List<Map<String, Object>> operations) {}

    public record ApplyEditsResponse(String updatedXml, List<String> applied, List<String> failed) {}

    public record NarrativeRequest(ChatContext context) {}

    public record NarrativeResponse(String markdown, String error) {}
}
