package com.pie.backend.controller;

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
import java.util.List;

@RestController
@RequestMapping("/api/v1/process")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_XML_CHARS = 12000;
    private static final int MAX_HISTORY_TURNS = 8;

    private final ChatClient chatClient;

    @Value("classpath:prompts/chat-prompt.txt")
    private Resource chatPromptResource;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResponse("Please ask a question."));
        }

        try {
            String systemPrompt = loadSystemPrompt();
            String contextBlock = buildContextBlock(request.context());
            String historyBlock = buildHistoryBlock(request.history());

            String userMessage = "CONTEXT:\n" + contextBlock
                    + "\n\nCONVERSATION HISTORY:\n" + historyBlock
                    + "\n\nUSER QUESTION:\n" + request.question();

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

    private String loadSystemPrompt() {
        try {
            if (chatPromptResource != null && chatPromptResource.exists()) {
                return new String(chatPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
            }
            Resource fallback = new ClassPathResource("prompts/chat-prompt.txt");
            return new String(fallback.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "You are a BPMN process intelligence copilot. Answer the user's question using the provided context.";
        }
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
}
