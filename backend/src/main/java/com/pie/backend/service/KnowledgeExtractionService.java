package com.pie.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("classpath:prompts/knowledge-extraction-prompt.txt")
    private Resource extractionPromptResource;

    public KnowledgeExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ProcessKnowledgeDTO extractKnowledge(String documentContent) {
        try {
            String systemPrompt = new String(extractionPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);

            String rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(documentContent)
                    .call()
                    .content();

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new IllegalStateException("LLM returned an empty response");
            }

            // Extract only the substring between the first '{' and the last '}'
            int startIndex = rawResponse.indexOf('{');
            int endIndex = rawResponse.lastIndexOf('}');

            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                throw new IllegalStateException("No valid JSON object found in model output: " + rawResponse);
            }

            String cleanJson = rawResponse.substring(startIndex, endIndex + 1);

            JsonNode root = objectMapper.readTree(cleanJson);

            return new ProcessKnowledgeDTO(
                    extractStringList(root, "activities"),
                    extractStringList(root, "actors"),
                    extractStringList(root, "roles"),
                    extractStringList(root, "systems"),
                    extractStringList(root, "events"),
                    extractStringList(root, "gateways"),
                    extractStringList(root, "inputs"),
                    extractStringList(root, "outputs"),
                    extractStringList(root, "businessRules"),
                    extractStringList(root, "risks"),
                    extractStringList(root, "conflicts")
            );

        } catch (Exception e) {
            log.error("Failed to extract knowledge: {}", e.getMessage());
            throw new RuntimeException("Knowledge extraction failed: " + e.getMessage(), e);
        }
    }

    private List<String> extractStringList(JsonNode rootNode, String fieldName) {
        List<String> result = new ArrayList<>();
        JsonNode fieldNode = rootNode.get(fieldName);

        if (fieldNode == null || !fieldNode.isArray()) {
            return result;
        }

        for (JsonNode item : fieldNode) {
            if (item.isTextual()) {
                result.add(item.asText());
            } else if (item.isArray()) {
                for (JsonNode subItem : item) {
                    result.add(subItem.isTextual() ? subItem.asText() : subItem.toString());
                }
            } else if (item.isObject()) {
                if (item.has("description")) {
                    result.add(item.get("description").asText());
                } else {
                    result.add(item.toString());
                }
            } else {
                result.add(item.asText());
            }
        }
        return result;
    }
}