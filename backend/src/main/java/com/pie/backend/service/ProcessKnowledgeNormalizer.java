package com.pie.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ProcessKnowledgeNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ProcessKnowledgeNormalizer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessKnowledgeDTO parseAndNormalize(String rawLlmResponse) {
        if (rawLlmResponse == null || rawLlmResponse.isBlank()) {
            throw new IllegalArgumentException("LLM response cannot be null or blank");
        }

        // 1. JSON Repair: Extract content between first '{' and last '}'
        String repairedJson = repairJson(rawLlmResponse);

        try {
            // 2. JSON Validation: Parse into tree structure
            JsonNode root = objectMapper.readTree(repairedJson);

            // 3. Normalization: Extract, clean, strip empty values, and deduplicate
            return new ProcessKnowledgeDTO(
                    normalizeList(root, "activities", true),    // Deduplicate
                    normalizeList(root, "actors", true),        // Deduplicate
                    normalizeList(root, "roles", true),
                    normalizeList(root, "systems", true),
                    normalizeList(root, "events", false),
                    normalizeList(root, "gateways", false),
                    normalizeList(root, "inputs", false),
                    normalizeList(root, "outputs", false),
                    normalizeList(root, "businessRules", false),
                    normalizeList(root, "risks", false),
                    normalizeList(root, "conflicts", false)
            );
        } catch (Exception e) {
            log.error("Failed to parse and normalize LLM JSON response: {}", e.getMessage());
            throw new RuntimeException("Invalid JSON structure received from LLM: " + e.getMessage(), e);
        }
    }

    private String repairJson(String raw) {
        String trimmed = raw.trim();

        // Strip markdown code fences if present
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');

        if (firstBrace == -1 || lastBrace == -1 || firstBrace >= lastBrace) {
            throw new IllegalArgumentException("No valid JSON object bounds found in output: " + raw);
        }

        return trimmed.substring(firstBrace, lastBrace + 1);
    }

    private List<String> normalizeList(JsonNode rootNode, String fieldName, boolean deduplicate) {
        JsonNode fieldNode = rootNode.get(fieldName);
        if (fieldNode == null || !fieldNode.isArray()) {
            return List.of();
        }

        List<String> rawItems = new ArrayList<>();
        for (JsonNode node : fieldNode) {
            if (node.isTextual()) {
                rawItems.add(node.asText());
            } else if (node.isArray()) {
                for (JsonNode subNode : node) {
                    rawItems.add(subNode.isTextual() ? subNode.asText() : subNode.toString());
                }
            } else if (node.isObject()) {
                if (node.has("description")) {
                    rawItems.add(node.get("description").asText());
                } else {
                    rawItems.add(node.toString());
                }
            } else {
                rawItems.add(node.asText());
            }
        }

        // Apply rules: Empty values removed, whitespace trimmed, case-aware deduplication
        Set<String> cleanSet = new LinkedHashSet<>();
        List<String> cleanList = new ArrayList<>();

        for (String item : rawItems) {
            if (item != null) {
                String clean = item.trim();
                // Rule: Remove empty/blank values
                if (!clean.isEmpty()) {
                    if (deduplicate) {
                        cleanSet.add(clean);
                    } else {
                        cleanList.add(clean);
                    }
                }
            }
        }

        return deduplicate ? new ArrayList<>(cleanSet) : cleanList;
    }
}