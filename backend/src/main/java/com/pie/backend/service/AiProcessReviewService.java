package com.pie.backend.service;

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

            return chatClient.prompt()
                    .system(prompt)
                    .user("BPMN Structured Context:\n\n" + structuredContext)
                    .call()
                    .entity(ReviewReportDTO.class);
        } catch (IllegalArgumentException e) {
            log.error("Invalid BPMN input", e);
            throw e;
        } catch (Exception exception) {
            log.error("Error generating AI process review summary", exception);
            throw new RuntimeException("Failed to generate process review summary", exception);
        }
    }
}

