package com.pie.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProcessKnowledgeNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ProcessKnowledgeNormalizer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessKnowledgeDTO parseAndNormalize(String rawLlmResponse) {
        return parseAndNormalize(rawLlmResponse, false);
    }

    public ProcessKnowledgeDTO parseAndNormalize(String rawLlmResponse, boolean allowCrossDocumentConflicts) {
        if (rawLlmResponse == null || rawLlmResponse.isBlank()) {
            throw new IllegalArgumentException("LLM response cannot be null or blank");
        }

        try {
            // 1. Attempt JSON Repair & Parsing
            String repairedJson = repairJson(rawLlmResponse);
            JsonNode root = objectMapper.readTree(repairedJson);

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
                        allowCrossDocumentConflicts ? normalizeList(root, "conflicts", false) : List.of()
            );
        } catch (IllegalArgumentException e) {
            // Re-throw argument exceptions from repairJson if no JSON found
            log.warn("JSON parsing of LLM response failed: {}. Attempting fallback heuristic text parser.", e.getMessage());
            return parseFallbackMarkdownText(rawLlmResponse);
        } catch (Exception e) {
            log.warn("JSON parsing of LLM response failed: {}. Attempting fallback heuristic text parser.", e.getMessage());
            return parseFallbackMarkdownText(rawLlmResponse);
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
        if (firstBrace == -1) {
            throw new IllegalArgumentException("No valid JSON object bounds found in output");
        }

        String candidate = trimmed.substring(firstBrace);
        Deque<Character> openDelimiters = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        int end = candidate.length();

        for (int i = 0; i < candidate.length(); i++) {
            char current = candidate.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                openDelimiters.push(current);
            } else if (current == '}' || current == ']') {
                if (openDelimiters.isEmpty() || !matches(openDelimiters.peek(), current)) {
                    throw new IllegalArgumentException("Malformed JSON delimiters in output");
                }
                openDelimiters.pop();
                if (openDelimiters.isEmpty()) {
                    end = i + 1;
                    break;
                }
            }
        }

        if (inString) {
            throw new IllegalArgumentException("Incomplete JSON string in output");
        }

        String json = candidate.substring(0, end).trim();
        while (!openDelimiters.isEmpty()) {
            json += openDelimiters.pop() == '[' ? "]" : "}";
        }
        return json;
    }

    private boolean matches(char opening, char closing) {
        return (opening == '{' && closing == '}') || (opening == '[' && closing == ']');
    }

    private List<String> normalizeList(JsonNode rootNode, String fieldName, boolean deduplicate) {
        JsonNode fieldNode = rootNode.get(fieldName);
        if (fieldNode == null || !fieldNode.isArray()) {
            return List.of();
        }

        List<String> rawItems = new ArrayList<>();
        for (JsonNode node : fieldNode) {
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isTextual()) {
                rawItems.add(node.asText());
            } else if (node.isArray()) {
                for (JsonNode subNode : node) {
                    if (subNode != null && !subNode.isNull()) {
                        rawItems.add(subNode.isTextual() ? subNode.asText() : subNode.toString());
                    }
                }
            } else if (node.isObject()) {
                if (node.has("description") && !node.get("description").isNull()) {
                    rawItems.add(node.get("description").asText());
                } else {
                    rawItems.add(node.toString());
                }
            } else {
                rawItems.add(node.asText());
            }
        }

        return cleanAndFilter(rawItems, deduplicate);
    }

    private ProcessKnowledgeDTO parseFallbackMarkdownText(String rawText) {
        List<String> activities = new ArrayList<>();
        List<String> actors = new ArrayList<>();
        List<String> systems = new ArrayList<>();
        List<String> gateways = new ArrayList<>();
        List<String> events = new ArrayList<>();
        List<String> businessRules = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        Pattern numberedListPattern = Pattern.compile("^\\s*\\d+[.)]\\s*(?:\\*\\*(.*?)\\*\\*:?\\s*)?(.*)$");
        Pattern bulletPattern = Pattern.compile("^\\s*[-*•]\\s*(?:\\*\\*(.*?)\\*\\*:?\\s*)?(.*)$");

        String[] lines = rawText.split("\n");
        String currentSection = "activities";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;

            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains("role") || lower.contains("actor") || lower.contains("team") || lower.contains("stakeholder")) {
                currentSection = "actors";
            } else if (lower.contains("system") || lower.contains("tool") || lower.contains("software") || lower.contains("platform")) {
                currentSection = "systems";
            } else if (lower.contains("control") || lower.contains("rule") || lower.contains("policy") || lower.contains("factor")) {
                currentSection = "rules";
            } else if (lower.contains("risk") || lower.contains("defect") || lower.contains("hazard")) {
                currentSection = "risks";
            } else if (lower.contains("process") || lower.contains("step") || lower.contains("activit") || lower.contains("overview")) {
                currentSection = "activities";
            }

            Matcher numMatcher = numberedListPattern.matcher(trimmed);
            Matcher bulletMatcher = bulletPattern.matcher(trimmed);

            String item = null;
            if (numMatcher.find()) {
                String title = numMatcher.group(1);
                String desc = numMatcher.group(2);
                item = (title != null && !title.isBlank()) ? (title + (desc != null && !desc.isBlank() ? ": " + desc : "")) : desc;
            } else if (bulletMatcher.find()) {
                String title = bulletMatcher.group(1);
                String desc = bulletMatcher.group(2);
                item = (title != null && !title.isBlank()) ? (title + (desc != null && !desc.isBlank() ? ": " + desc : "")) : desc;
            }

            if (item != null && !item.isBlank()) {
                item = item.replaceAll("[*#_]", "").trim();
                if (item.endsWith("?")) {
                    gateways.add(item);
                } else if (currentSection.equals("actors")) {
                    actors.add(item);
                } else if (currentSection.equals("systems")) {
                    systems.add(item);
                } else if (currentSection.equals("rules")) {
                    businessRules.add(item);
                } else if (currentSection.equals("risks")) {
                    risks.add(item);
                } else {
                    activities.add(item);
                }
            }
        }

        List<String> cleanActivities = cleanAndFilter(activities, true);
        List<String> cleanActors = cleanAndFilter(actors, true);

        if (cleanActivities.isEmpty() && cleanActors.isEmpty() && cleanAndFilter(systems, true).isEmpty()) {
            throw new IllegalArgumentException("No valid process knowledge could be extracted from input: " + rawText);
        }

        if (events.isEmpty() && !cleanActivities.isEmpty()) {
            events.add("Start Process");
            events.add("Process Completed");
        }

        return new ProcessKnowledgeDTO(
                cleanActivities,
                cleanActors,
                cleanActors,
                cleanAndFilter(systems, true),
                events,
                gateways,
                List.of(),
                List.of(),
                businessRules,
                risks,
                List.of()
        );
    }

    private List<String> cleanAndFilter(List<String> items, boolean deduplicate) {
        if (items == null) return List.of();

        Collection<String> resultCollection = deduplicate ? new LinkedHashSet<>() : new ArrayList<>();

        for (String raw : items) {
            if (raw == null) continue;
            String cleaned = raw.trim();
            if (cleaned.isBlank() || cleaned.equalsIgnoreCase("none") || cleaned.equalsIgnoreCase("n/a") || cleaned.equalsIgnoreCase("null")) {
                continue;
            }
            resultCollection.add(cleaned);
        }

        return new ArrayList<>(resultCollection);
    }
}