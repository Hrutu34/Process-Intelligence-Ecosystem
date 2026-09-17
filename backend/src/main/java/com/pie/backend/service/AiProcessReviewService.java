package com.pie.backend.service;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pie.backend.util.BpmnParser;
import com.pie.shared.dto.ReviewReportDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AiProcessReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiProcessReviewService.class);
    private final ChatClient chatClient;

    /**
     * Lenient mapper for LLM output. Models routinely emit unquoted or single-quoted
     * property names, trailing commas, and commentary, none of which a strict bind
     * tolerates. Unknown properties are ignored so an extra field never fails the call.
     */
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AiProcessReviewService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ReviewReportDTO generateSummary(String bpmnXml) {
        try {
            // Parse XML and prepare structured context to avoid feeding raw XML to the LLM
            String structuredContext = BpmnParser.parseToStructuredContext(bpmnXml);

            String prompt = new String(
                    new ClassPathResource("prompts/process-review-summary-prompt.txt")
                            .getContentAsByteArray(),
                    StandardCharsets.UTF_8);

            // Take the raw completion rather than a strict .entity() bind so that
            // recoverable JSON defects are repaired instead of collapsing to fallback.
            String raw = chatClient.prompt()
                    .system(prompt)
                    .user("BPMN Structured Context:\n\n" + structuredContext)
                    .call()
                    .content();

            return LENIENT_MAPPER.readValue(extractJsonObject(raw), ReviewReportDTO.class);
        } catch (IllegalArgumentException e) {
            log.error("Invalid BPMN input", e);
            throw e;
        } catch (Exception exception) {
            log.error("Error generating AI process review summary", exception);
            throw new RuntimeException("Failed to generate process review summary", exception);
        }
    }

    /**
     * Isolates the JSON object from a completion that may carry markdown fences or
     * prose around it.
     *
     * <p>Throws {@link IllegalStateException} rather than {@link IllegalArgumentException}
     * so that unusable model output is treated as an upstream failure (triggering the
     * fallback) instead of being reported to the caller as a bad request.
     */
    private String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("LLM returned an empty process review summary");
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();

        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first == -1 || last <= first) {
            throw new IllegalStateException("No JSON object found in process review summary output");
        }

        return trimmed.substring(first, last + 1);
    }
}

