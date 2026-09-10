package com.pie.backend.service;


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
            String prompt = new String(
                    new ClassPathResource("prompts/process-review-summary-prompt.txt")
                            .getContentAsByteArray(),
                    StandardCharsets.UTF_8);

            // Strip visual elements to drastically reduce prompt size and improve local LLM processing time
            String cleanedXml = bpmnXml != null ? bpmnXml.replaceAll("(?s)<bpmndi:BPMNDiagram.*?</bpmndi:BPMNDiagram>", "") : "";

            return chatClient.prompt()
                    .system(prompt)
                    .user("BPMN XML:\n" + cleanedXml)
                    .call()
                    .entity(ReviewReportDTO.class);
        } catch (Exception exception) {
            log.error("Error generating AI process review summary", exception);
            throw new RuntimeException("Failed to generate process review summary", exception);
        }
    }
}

