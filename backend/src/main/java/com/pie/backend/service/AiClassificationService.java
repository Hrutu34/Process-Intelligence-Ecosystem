package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class AiClassificationService implements ClassificationService {

    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(AiClassificationService.class);

    public AiClassificationService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public ClassificationResult classify(String extractedText) throws Exception {
        if (extractedText == null || extractedText.isBlank()) {
            return new ClassificationResult("Unknown", 0);
        }

        String systemPrompt = "You are a document classifier. Classify the user-provided text into exactly one of the following categories: "
                +
                "Process Description, Standard Operating Procedure, Policy Document, Workflow Specification, Meeting Notes, Requirements Document, Unknown. "
                +
                "If the content does not confidently match any category, respond with Unknown. Output EXACTLY a JSON object with two fields: category (string) and confidence (integer 0-100). "
                +
                "Do not output any additional text. Confidence should reflect your estimate of correctness.";

        // Use the extraction text as the user message
        Object response = chatClient.prompt()
                .system(systemPrompt)
                .user(extractedText)
                .call()
                .entity(java.util.Map.class);

        // response expected as a Map {"category":"...","confidence":94}
        try {
            log.debug("Raw classifier response (object): {}", response);
            if (response instanceof java.util.Map<?, ?> map) {
                Object catObj = map.get("category");
                Object confObj = map.get("confidence");
                String category = catObj == null ? null : catObj.toString();
                int confidence = -1;
                if (confObj instanceof Number n)
                    confidence = n.intValue();
                else if (confObj != null) {
                    try {
                        confidence = Integer.parseInt(confObj.toString());
                    } catch (Exception ignored) {
                    }
                }

                if (category == null || category.isBlank() || confidence < 0) {
                    log.warn(
                            "Malformed classifier Map response: missing category or confidence - returning Unknown. Raw map={}",
                            map);
                    return new ClassificationResult("Unknown", 0);
                }
                return new ClassificationResult(category, Math.max(0, Math.min(100, confidence)));
            }

            // Fallback: if model returned text, try parse as JSON string
            String text = response == null ? null : response.toString();
            log.debug("Raw classifier response (text): {}", text);
            var node = mapper.readTree(text == null ? "" : text);
            String category = node.path("category").asText(null);
            int confidence = node.path("confidence").asInt(-1);
            if (category == null || category.isBlank() || confidence < 0) {
                log.warn(
                        "Malformed classifier JSON response: missing category or confidence - returning Unknown. Raw text={}",
                        text);
                return new ClassificationResult("Unknown", 0);
            }
            return new ClassificationResult(category, Math.max(0, Math.min(100, confidence)));
        } catch (Exception e) {
            log.error("Failed to parse classification response; returning Unknown", e);
            return new ClassificationResult("Unknown", 0);
        }
    }
}
