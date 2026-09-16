package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.shared.dto.GraphEdge;
import com.pie.shared.dto.GraphNode;
import com.pie.shared.dto.ProcessGraphDTO;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.shared.dto.ProcessQualityReportDTO;
import com.pie.shared.dto.ValidationIssueDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiProcessQualityService {

    private static final Logger log = LoggerFactory.getLogger(AiProcessQualityService.class);
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiProcessQualityService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ProcessQualityReportDTO enhance(ProcessKnowledgeDTO knowledge,
                                           ProcessGraphDTO graph,
                                           ProcessQualityReportDTO deterministicReport) {
        try {
            String prompt = new String(
                    new ClassPathResource("prompts/process-quality-review-prompt.txt")
                            .getContentAsByteArray(),
                    StandardCharsets.UTF_8);
            String response = chatClient.prompt()
                    .system(prompt)
                    .user(buildReviewInput(knowledge, graph))
                    .call()
                    .content();
            SemanticReview review = null;
            try {
                review = objectMapper.readValue(extractJson(response), SemanticReview.class);
            } catch (Exception parseException) {
                log.warn("Standard JSON parsing failed. Attempting robust Regex parser for LLM response...");
                review = parseReviewRobustly(response);
            }

            int semanticScore = Math.max(0, Math.min(100, review.semanticScore()));
            int blendedScore = Math.round(deterministicReport.qualityScore() * 0.70f + semanticScore * 0.30f);

            List<ValidationIssueDTO> issues = new ArrayList<>(deterministicReport.issues());
            review.findings().stream().limit(3).forEach(finding -> issues.add(new ValidationIssueDTO(
                    "AI_SEMANTIC_REVIEW",
                    "LOW",
                    null,
                    finding,
                        "Review this semantic observation against the source process documents.",
                        0.70
            )));
            List<String> recommendations = new ArrayList<>(deterministicReport.recommendations());
            review.recommendations().stream().limit(3).forEach(recommendations::add);
            return new ProcessQualityReportDTO(deterministicReport.valid(), blendedScore, issues, recommendations);
        } catch (Exception exception) {
            log.warn("AI process quality review unavailable; using deterministic score: {}", exception.getMessage());
            return deterministicReport;
        }
    }

    private SemanticReview parseReviewRobustly(String text) {
        int score = 80; // default safe score
        List<String> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        java.util.regex.Matcher mScore = java.util.regex.Pattern.compile("\"semanticScore\"\\s*:\\s*(\\d+)").matcher(text);
        if (mScore.find()) {
            try { score = Integer.parseInt(mScore.group(1)); } catch (Exception ignored) {}
        }

        extractJsonArrayStrings(text, "findings").forEach(findings::add);
        extractJsonArrayStrings(text, "recommendations").forEach(recommendations::add);

        return new SemanticReview(score, findings, recommendations);
    }

    private List<String> extractJsonArrayStrings(String text, String key) {
        List<String> results = new ArrayList<>();
        java.util.regex.Matcher mArray = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL).matcher(text);
        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            java.util.regex.Matcher mStrings = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(arrayContent);
            while (mStrings.find()) {
                results.add(mStrings.group(1));
            }
        }
        return results;
    }

    private String buildReviewInput(ProcessKnowledgeDTO knowledge, ProcessGraphDTO graph) {
        StringBuilder input = new StringBuilder("KNOWLEDGE:\n").append(knowledge).append("\nGRAPH NODES:\n");
        for (GraphNode node : graph.getNodes()) {
            input.append(node.getType()).append(" | ").append(node.getLabel()).append("\n");
        }
        input.append("GRAPH FLOWS:\n");
        for (GraphEdge edge : graph.getEdges()) {
            input.append(edge.getFrom()).append(" -> ").append(edge.getTo())
                    .append(" [").append(edge.getEdgeType()).append("]\n");
        }
        return input.toString();
    }

    private String extractJson(String response) {
        if (response == null) throw new IllegalArgumentException("Empty AI quality response");
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("AI quality response is not JSON");
        return response.substring(start, end + 1);
    }

    public record SemanticReview(int semanticScore, List<String> findings, List<String> recommendations) {
        public SemanticReview {
            findings = findings == null ? List.of() : findings;
            recommendations = recommendations == null ? List.of() : recommendations;
        }
    }
}
