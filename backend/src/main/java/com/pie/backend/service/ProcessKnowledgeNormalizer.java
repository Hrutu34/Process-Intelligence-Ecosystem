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
        return stripTrailingCommas(json);
    }

    /**
     * Removes trailing commas before '}' or ']' (e.g. {"a":[1,2,],}) which LLMs emit
     * frequently and Jackson rejects. String-aware so commas inside values are preserved.
     */
    private String stripTrailingCommas(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);

            if (inString) {
                out.append(current);
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
                out.append(current);
                continue;
            }

            if (current == ',') {
                // Look ahead past whitespace: if the next structural char closes a
                // container, this comma is trailing and must be dropped.
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    continue;
                }
            }

            out.append(current);
        }

        return out.toString();
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

        // 1. Try robust JSON array extraction first (handles malformed arrays better than Jackson)
        if (rawText.contains("{") && rawText.contains("[")) {
            activities.addAll(extractJsonArrayStringsRobust(rawText, "activities"));
            actors.addAll(extractJsonArrayStringsRobust(rawText, "actors"));
            systems.addAll(extractJsonArrayStringsRobust(rawText, "systems"));
            gateways.addAll(extractJsonArrayStringsRobust(rawText, "gateways"));
            events.addAll(extractJsonArrayStringsRobust(rawText, "events"));
            businessRules.addAll(extractJsonArrayStringsRobust(rawText, "businessRules"));
            risks.addAll(extractJsonArrayStringsRobust(rawText, "risks"));
            
            if (!activities.isEmpty() || !actors.isEmpty() || !systems.isEmpty()) {
                List<String> earlyActivities = cleanAndFilter(activities, true);
                List<String> earlyEvents = cleanAndFilter(events, false);

                // A graph needs at least one activity or event. Mirror the default-event
                // behaviour of the line-based parser below.
                if (!earlyActivities.isEmpty() && earlyEvents.isEmpty()) {
                    earlyEvents = List.of("Start Event", "End Event");
                }

                // If regex extraction only found actors/systems, the result is not a usable
                // process graph. Fall through to the line-based parser instead of returning
                // knowledge that is guaranteed to fail downstream validation.
                if (!earlyActivities.isEmpty() || !earlyEvents.isEmpty()) {
                    return new ProcessKnowledgeDTO(
                            earlyActivities,
                            cleanAndFilter(actors, true),
                            List.of(),
                            cleanAndFilter(systems, true),
                            earlyEvents,
                            cleanAndFilter(gateways, false),
                            List.of(),
                            List.of(),
                            cleanAndFilter(businessRules, false),
                            cleanAndFilter(risks, false),
                            List.of(),
                            List.of()
                    );
                }
            }
        }

        Pattern numberedListPattern = Pattern.compile("^\\s*\\d+[.)]\\s*(?:\\*\\*(.*?)\\*\\*:?\\s*)?(.*)$");
        Pattern bulletPattern = Pattern.compile("^\\s*[-*•?]\\s*(?:\\*\\*(.*?)\\*\\*:?\\s*)?(.*)$");

        String[] lines = rawText.split("\n");
        String currentSection = "activities";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;

            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains("role") || lower.contains("actor") || lower.contains("team") || lower.contains("stakeholder")) {
                currentSection = "actors";
                continue;
            } else if (lower.contains("system") || lower.contains("application") || lower.contains("tool") || lower.contains("software")) {
                currentSection = "systems";
                continue;
            } else if (lower.contains("event") || lower.contains("trigger") || lower.contains("start") || lower.contains("end")) {
                currentSection = "events";
                continue;
            } else if (lower.contains("gateway") || lower.contains("decision") || lower.contains("condition") || lower.contains("branch")) {
                currentSection = "gateways";
                continue;
            } else if (lower.contains("rule") || lower.contains("policy") || lower.contains("logic")) {
                currentSection = "businessRules";
                continue;
            } else if (lower.contains("risk") || lower.contains("issue") || lower.contains("conflict") || lower.contains("problem")) {
                currentSection = "risks";
                continue;
            } else if (lower.contains("activity") || lower.contains("step") || lower.contains("task") || lower.contains("action") || lower.contains("process")) {
                currentSection = "activities";
                continue;
            }

            Matcher mNum = numberedListPattern.matcher(line);
            Matcher mBul = bulletPattern.matcher(line);

            String item = null;
            if (mNum.find()) {
                item = (mNum.group(1) != null ? mNum.group(1) + ": " : "") + mNum.group(2);
            } else if (mBul.find()) {
                item = (mBul.group(1) != null ? mBul.group(1) + ": " : "") + mBul.group(2);
            }

            if (item != null && !item.isBlank()) {
                item = item.replaceAll("[\\[\\]\"'{}]", "").trim();
                
                if (currentSection.equals("actors")) {
                    actors.add(item);
                } else if (currentSection.equals("systems")) {
                    systems.add(item);
                } else if (currentSection.equals("gateways")) {
                    gateways.add(item);
                } else if (currentSection.equals("events")) {
                    events.add(item);
                } else if (currentSection.equals("businessRules")) {
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

        if (events.isEmpty() && !cleanActivities.isEmpty()) {
            events.add("Start Event");
            events.add("End Event");
        }

        List<String> cleanEvents = cleanAndFilter(events, false);

        // Fail loudly here rather than returning knowledge that cannot form a graph.
        // Downstream ProcessGraphValidator requires at least one activity or event.
        if (cleanActivities.isEmpty() && cleanEvents.isEmpty()) {
            throw new IllegalArgumentException("No valid process knowledge could be extracted from input: " + rawText);
        }

        return new ProcessKnowledgeDTO(
                cleanActivities,
                cleanActors,
                List.of(),
                cleanAndFilter(systems, true),
                cleanEvents,
                cleanAndFilter(gateways, false),
                List.of(),
                List.of(),
                cleanAndFilter(businessRules, false),
                cleanAndFilter(risks, false),
                List.of(),
                List.of()
        );
    }

    /**
     * Locates the array literal following {@code "key":} in the raw text and returns
     * every top-level string value inside it.
     *
     * <p>Both operations are performed with a string- and bracket-aware scanner rather
     * than a regex, so values containing embedded brackets (e.g. {@code "Submit request
     * [USER_TASK]"}) or nested arrays no longer truncate the match.
     */
    private List<String> extractJsonArrayStringsRobust(String text, String key) {
        List<String> results = new ArrayList<>();

        int keyStart = findKeyOccurrence(text, key);
        if (keyStart < 0) {
            return results;
        }

        int arrayStart = text.indexOf('[', keyStart);
        if (arrayStart < 0) {
            return results;
        }

        int arrayEnd = findMatchingBracket(text, arrayStart);
        if (arrayEnd < 0) {
            return results;
        }

        String arrayContent = text.substring(arrayStart + 1, arrayEnd);
        collectTopLevelStrings(arrayContent, key, results);
        return results;
    }

    /**
     * Finds {@code "key"} in the text but only when it is used as a property name,
     * i.e. immediately followed by a colon (with optional whitespace).
     */
    private int findKeyOccurrence(String text, String key) {
        String quoted = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int idx = text.indexOf(quoted, from);
            if (idx < 0) {
                return -1;
            }
            int after = idx + quoted.length();
            while (after < text.length() && Character.isWhitespace(text.charAt(after))) {
                after++;
            }
            if (after < text.length() && text.charAt(after) == ':') {
                return idx;
            }
            from = idx + 1;
        }
    }

    /**
     * Given the index of an opening bracket, returns the index of its matching closer
     * while respecting string literals and nested brackets/braces, or -1 if unbalanced.
     */
    private int findMatchingBracket(String text, int openIdx) {
        char open = text.charAt(openIdx);
        char close = open == '[' ? ']' : '}';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Walks the array body and collects each top-level double-quoted string. Values
     * inside nested objects/arrays are ignored so we do not accidentally slurp keys
     * or fragments out of malformed sub-structures.
     */
    private void collectTopLevelStrings(String arrayContent, String key, List<String> out) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        StringBuilder current = null;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (inString) {
                if (escaped) {
                    current.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                    current.append(c);
                } else if (c == '"') {
                    inString = false;
                    if (depth == 0) {
                        String val = current.toString().trim();
                        if (!val.isBlank() && !val.equals(key)) {
                            out.add(val);
                        }
                    }
                    current = null;
                } else {
                    current.append(c);
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                current = new StringBuilder();
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (depth > 0) {
                    depth--;
                }
            }
        }
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