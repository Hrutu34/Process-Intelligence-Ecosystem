package com.pie.backend.service;

import com.pie.backend.exception.InvalidProcessGraphException;
import com.pie.shared.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CanonicalProcessGraphBuilder implements ProcessGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(CanonicalProcessGraphBuilder.class);

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "(?i)\\b(?:if|when|on|case|is)\\s+([a-zA-Z0-9_-]+(?:\\s+[a-zA-Z0-9_-]+)?)"
    );

    private final ProcessGraphValidator validator;

    public CanonicalProcessGraphBuilder(ProcessGraphValidator validator) {
        this.validator = validator;
    }

    @Override
    public CanonicalProcessGraph build(ProcessKnowledgeDTO knowledge) {
        validator.validateInput(knowledge);
        String graphId = generateDeterministicGraphId(knowledge);
        return build(graphId, knowledge);
    }

    @Override
    public CanonicalProcessGraph build(String graphId, ProcessKnowledgeDTO knowledge) {
        validator.validateInput(knowledge);

        if (graphId == null || graphId.isBlank()) {
            graphId = generateDeterministicGraphId(knowledge);
        }

        Map<String, GraphNode> nodeRegistry = new LinkedHashMap<>();
        Map<String, GraphEdge> edgeRegistry = new LinkedHashMap<>();

        // 1. Create Activity Nodes
        List<GraphNode> activityNodes = createActivityNodes(knowledge, nodeRegistry);

        // 2. Create Role Nodes
        List<GraphNode> roleNodes = createRoleNodes(knowledge, nodeRegistry);

        // 3. Create Gateway Nodes
        List<GraphNode> gatewayNodes = createGatewayNodes(knowledge, nodeRegistry);

        // 4. Create System Nodes
        List<GraphNode> systemNodes = createSystemNodes(knowledge, nodeRegistry);

        // 5. Create Event Nodes
        List<GraphNode> eventNodes = createEventNodes(knowledge, nodeRegistry);

        // 6. Create Data Artifact Nodes
        List<GraphNode> dataNodes = createDataArtifactNodes(knowledge, nodeRegistry);

        // 7. Build Non-Flow Association Edges
        buildRoleAssociations(activityNodes, roleNodes, knowledge, edgeRegistry);
        buildSystemAssociations(activityNodes, systemNodes, knowledge, edgeRegistry);
        buildDataArtifactAssociations(activityNodes, dataNodes, knowledge, edgeRegistry);

        // 8. Build Process Sequence Flow and Gateway Branching Edges
        buildProcessFlowEdges(activityNodes, gatewayNodes, eventNodes, knowledge, edgeRegistry);

        // 9. Assemble Graph and Validate
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId(graphId)
                .addNodes(new ArrayList<>(nodeRegistry.values()))
                .addEdges(new ArrayList<>(edgeRegistry.values()))
                .build();

        validator.validateGraph(graph);

        log.info("Successfully built CanonicalProcessGraph [{}]: {} nodes, {} edges",
                graph.getGraphId(), graph.getNodes().size(), graph.getEdges().size());

        return graph;
    }

    private List<GraphNode> createActivityNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        if (knowledge.activities() == null) {
            return list;
        }

        for (String raw : knowledge.activities()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String label = raw.trim();
            String id = "activity-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Activity)
                        .label(label)
                        .metadata(NodeMetadata.builder().build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private List<GraphNode> createRoleNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        List<String> combinedRoles = new ArrayList<>();

        if (knowledge.actors() != null) {
            combinedRoles.addAll(knowledge.actors());
        }
        if (knowledge.roles() != null) {
            combinedRoles.addAll(knowledge.roles());
        }

        for (String raw : combinedRoles) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String label = raw.trim();
            String id = "role-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Role)
                        .label(label)
                        .metadata(NodeMetadata.builder().build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private List<GraphNode> createGatewayNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        if (knowledge.gateways() == null) {
            return list;
        }

        for (String raw : knowledge.gateways()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String label = raw.trim();
            String id = "gateway-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Gateway)
                        .label(label)
                        .metadata(NodeMetadata.builder().gatewayType(GatewayType.exclusive).build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private List<GraphNode> createSystemNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        if (knowledge.systems() == null) {
            return list;
        }

        for (String raw : knowledge.systems()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String label = raw.trim();
            String id = "system-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.System)
                        .label(label)
                        .metadata(NodeMetadata.builder().build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private List<GraphNode> createEventNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        if (knowledge.events() == null || knowledge.events().isEmpty()) {
            return list;
        }

        List<String> rawEvents = knowledge.events().stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .toList();

        for (int i = 0; i < rawEvents.size(); i++) {
            String label = rawEvents.get(i);
            String id = "event-" + slugify(label);

            EventType eventType;
            String lower = label.toLowerCase(Locale.ROOT);
            if (i == 0 && (lower.contains("start") || lower.contains("trigger") || lower.contains("request") || lower.contains("initiat") || rawEvents.size() > 1)) {
                eventType = EventType.start;
            } else if (i == rawEvents.size() - 1 && (lower.contains("end") || lower.contains("complete") || lower.contains("finish") || lower.contains("done") || rawEvents.size() > 1)) {
                eventType = EventType.end;
            } else {
                eventType = EventType.intermediate;
            }

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Event)
                        .label(label)
                        .metadata(NodeMetadata.builder().eventType(eventType).build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private List<GraphNode> createDataArtifactNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        List<String> rawData = new ArrayList<>();
        if (knowledge.inputs() != null) {
            rawData.addAll(knowledge.inputs());
        }
        if (knowledge.outputs() != null) {
            rawData.addAll(knowledge.outputs());
        }

        for (String raw : rawData) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String label = raw.trim();
            String id = "data-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.DataArtifact)
                        .label(label)
                        .metadata(NodeMetadata.builder().build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private void buildRoleAssociations(List<GraphNode> activityNodes, List<GraphNode> roleNodes,
                                       ProcessKnowledgeDTO knowledge, Map<String, GraphEdge> edgeRegistry) {
        if (activityNodes.isEmpty() || roleNodes.isEmpty()) {
            return;
        }

        Set<Integer> matchedActivityIndices = new HashSet<>();
        Set<String> matchedRoleIds = new HashSet<>();

        // Phase 1: Explicit / semantic matching between role names and activity labels
        for (GraphNode roleNode : roleNodes) {
            String roleLower = roleNode.getLabel().toLowerCase(Locale.ROOT);
            for (int i = 0; i < activityNodes.size(); i++) {
                if (matchedActivityIndices.contains(i)) {
                    continue;
                }
                GraphNode actNode = activityNodes.get(i);
                String actLower = actNode.getLabel().toLowerCase(Locale.ROOT);
                if (actLower.contains(roleLower) || isSemanticMatch(roleLower, actLower)) {
                    addAssociation(roleNode, actNode, edgeRegistry);
                    matchedActivityIndices.add(i);
                    matchedRoleIds.add(roleNode.getId());
                    break;
                }
            }
        }

        // Phase 2: Index-based 1-to-1 association if no semantic matches or remaining unmatched activities
        if (matchedActivityIndices.isEmpty() && roleNodes.size() == activityNodes.size()) {
            for (int i = 0; i < activityNodes.size(); i++) {
                GraphNode actNode = activityNodes.get(i);
                GraphNode roleNode = roleNodes.get(i);
                addAssociation(roleNode, actNode, edgeRegistry);
            }
        } else if (matchedActivityIndices.size() < activityNodes.size()) {
            for (int i = 0; i < activityNodes.size(); i++) {
                if (!matchedActivityIndices.contains(i) && i < roleNodes.size()) {
                    GraphNode roleNode = roleNodes.get(i);
                    if (!matchedRoleIds.contains(roleNode.getId())) {
                        addAssociation(roleNode, activityNodes.get(i), edgeRegistry);
                        matchedRoleIds.add(roleNode.getId());
                    }
                }
            }
        }
    }

    private void buildSystemAssociations(List<GraphNode> activityNodes, List<GraphNode> systemNodes,
                                         ProcessKnowledgeDTO knowledge, Map<String, GraphEdge> edgeRegistry) {
        if (activityNodes.isEmpty() || systemNodes.isEmpty()) {
            return;
        }

        for (GraphNode systemNode : systemNodes) {
            String systemLower = systemNode.getLabel().toLowerCase(Locale.ROOT);
            for (GraphNode actNode : activityNodes) {
                String actLower = actNode.getLabel().toLowerCase(Locale.ROOT);
                if (actLower.contains(systemLower)) {
                    addAssociation(systemNode, actNode, edgeRegistry);
                    if (actNode.getMetadata() != null) {
                        actNode.getMetadata().setSystemRef(systemNode.getId());
                    }
                }
            }
        }
    }

    private void buildDataArtifactAssociations(List<GraphNode> activityNodes, List<GraphNode> dataNodes,
                                               ProcessKnowledgeDTO knowledge, Map<String, GraphEdge> edgeRegistry) {
        if (activityNodes.isEmpty() || dataNodes.isEmpty()) {
            return;
        }

        for (GraphNode dataNode : dataNodes) {
            String dataLower = dataNode.getLabel().toLowerCase(Locale.ROOT);
            for (GraphNode actNode : activityNodes) {
                String actLower = actNode.getLabel().toLowerCase(Locale.ROOT);
                if (actLower.contains(dataLower) || dataLower.contains(actLower)) {
                    addAssociation(dataNode, actNode, edgeRegistry);
                }
            }
        }
    }

    private void addAssociation(GraphNode fromNode, GraphNode toNode, Map<String, GraphEdge> edgeRegistry) {
        String edgeId = "edge-" + fromNode.getId() + "-" + toNode.getId() + "-association";
        GraphEdge edge = GraphEdge.builder()
                .id(edgeId)
                .from(fromNode.getId())
                .to(toNode.getId())
                .edgeType(EdgeType.association)
                .build();
        edgeRegistry.put(edgeId, edge);

        if (fromNode.getType() == NodeType.Role && toNode.getMetadata() != null) {
            toNode.getMetadata().setRoleRef(fromNode.getId());
        }
    }

    private void buildProcessFlowEdges(List<GraphNode> activityNodes,
                                       List<GraphNode> gatewayNodes,
                                       List<GraphNode> eventNodes,
                                       ProcessKnowledgeDTO knowledge,
                                       Map<String, GraphEdge> edgeRegistry) {
        if (activityNodes.isEmpty()) {
            return;
        }

        // Map gateways to their predecessor activity index
        Map<Integer, List<GatewayPlacement>> gatewayPlacements = determineGatewayPlacements(activityNodes, gatewayNodes, knowledge);

        Set<Integer> bypassedSequentialEdges = new HashSet<>();

        // Connect Start Event to First Activity if present
        if (!eventNodes.isEmpty()) {
            GraphNode startEvent = eventNodes.stream()
                    .filter(e -> e.getMetadata() != null && e.getMetadata().getEventType() == EventType.start)
                    .findFirst()
                    .orElse(null);
            if (startEvent != null) {
                addSequenceEdge(startEvent.getId(), activityNodes.get(0).getId(), edgeRegistry);
            }
        }

        // Wire gateways and sequence flows
        for (int i = 0; i < activityNodes.size(); i++) {
            GraphNode currentActivity = activityNodes.get(i);

            // Check if there are gateways positioned after current activity
            List<GatewayPlacement> placements = gatewayPlacements.get(i);
            if (placements != null && !placements.isEmpty()) {
                for (GatewayPlacement gp : placements) {
                    // 1. Sequence edge from predecessor activity -> gateway
                    addSequenceEdge(currentActivity.getId(), gp.gatewayNode.getId(), edgeRegistry);

                    // 2. Conditional edge from gateway -> target activity
                    if (gp.targetActivityIndex < activityNodes.size()) {
                        GraphNode targetActivity = activityNodes.get(gp.targetActivityIndex);
                        addConditionalEdge(gp.gatewayNode.getId(), targetActivity.getId(), gp.conditionLabel, edgeRegistry);
                        bypassedSequentialEdges.add(i);
                    }
                }
            }

            // If not bypassed by a gateway branch, add standard sequence edge to next activity
            if (i < activityNodes.size() - 1 && !bypassedSequentialEdges.contains(i)) {
                GraphNode nextActivity = activityNodes.get(i + 1);
                addSequenceEdge(currentActivity.getId(), nextActivity.getId(), edgeRegistry);
            }
        }

        // Connect Last Activity to End Event if present
        if (!eventNodes.isEmpty()) {
            GraphNode endEvent = eventNodes.stream()
                    .filter(e -> e.getMetadata() != null && e.getMetadata().getEventType() == EventType.end)
                    .findFirst()
                    .orElse(null);
            if (endEvent != null) {
                addSequenceEdge(activityNodes.get(activityNodes.size() - 1).getId(), endEvent.getId(), edgeRegistry);
            }
        }
    }

    private Map<Integer, List<GatewayPlacement>> determineGatewayPlacements(
            List<GraphNode> activityNodes,
            List<GraphNode> gatewayNodes,
            ProcessKnowledgeDTO knowledge) {

        Map<Integer, List<GatewayPlacement>> placements = new LinkedHashMap<>();
        if (gatewayNodes.isEmpty() || activityNodes.size() < 2) {
            return placements;
        }

        for (int g = 0; g < gatewayNodes.size(); g++) {
            GraphNode gwNode = gatewayNodes.get(g);
            String gwLower = gwNode.getLabel().toLowerCase(Locale.ROOT);

            int predecessorIndex = -1;

            // Strategy 1: Match by role / activity keywords
            for (int i = 0; i < activityNodes.size(); i++) {
                GraphNode act = activityNodes.get(i);
                String actLower = act.getLabel().toLowerCase(Locale.ROOT);
                String roleRef = act.getMetadata() != null ? act.getMetadata().getRoleRef() : null;

                if (gwLower.contains("approval") && (actLower.contains("review") || actLower.contains("approv"))) {
                    predecessorIndex = i;
                    break;
                }
                if (roleRef != null && gwLower.contains(roleRef.replace("role-", "").replace("-", " "))) {
                    predecessorIndex = i;
                    break;
                }
                if (gwLower.contains(actLower) || actLower.contains(gwLower)) {
                    predecessorIndex = i;
                    break;
                }
            }

            // Strategy 2: Fallback to chronological position between activities
            if (predecessorIndex == -1 || predecessorIndex >= activityNodes.size() - 1) {
                predecessorIndex = Math.min(g + 1, activityNodes.size() - 2);
                if (predecessorIndex < 0) {
                    predecessorIndex = 0;
                }
            }

            int targetIndex = predecessorIndex + 1;
            String conditionLabel = extractConditionLabel(gwNode.getLabel(), knowledge);

            placements.computeIfAbsent(predecessorIndex, k -> new ArrayList<>())
                    .add(new GatewayPlacement(gwNode, targetIndex, conditionLabel));
        }

        return placements;
    }

    private String extractConditionLabel(String gatewayLabel, ProcessKnowledgeDTO knowledge) {
        if (knowledge.businessRules() != null) {
            for (String rule : knowledge.businessRules()) {
                if (rule != null) {
                    Matcher matcher = CONDITION_PATTERN.matcher(rule);
                    if (matcher.find()) {
                        String matched = matcher.group(1).trim().toLowerCase(Locale.ROOT);
                        if (matched.equals("approved") || matched.equals("yes") || matched.equals("valid") || matched.equals("success")) {
                            return "approved";
                        }
                        return matched;
                    }
                }
            }
        }

        String gwLower = gatewayLabel.toLowerCase(Locale.ROOT);
        if (gwLower.contains("approv")) {
            return "approved";
        }
        if (gwLower.contains("valid")) {
            return "valid";
        }
        if (gwLower.contains("check")) {
            return "passed";
        }
        return "approved";
    }

    private void addSequenceEdge(String fromId, String toId, Map<String, GraphEdge> edgeRegistry) {
        if (fromId.equals(toId)) return;
        String edgeId = "edge-" + fromId + "-" + toId + "-sequence";
        GraphEdge edge = GraphEdge.builder()
                .id(edgeId)
                .from(fromId)
                .to(toId)
                .edgeType(EdgeType.sequence)
                .build();
        edgeRegistry.put(edgeId, edge);
    }

    private void addConditionalEdge(String fromId, String toId, String label, Map<String, GraphEdge> edgeRegistry) {
        if (fromId.equals(toId)) return;
        String edgeId = "edge-" + fromId + "-" + toId + "-conditional";
        if (label != null && !label.isBlank()) {
            edgeId += "-" + slugify(label);
        }
        GraphEdge edge = GraphEdge.builder()
                .id(edgeId)
                .from(fromId)
                .to(toId)
                .edgeType(EdgeType.conditional)
                .label(label)
                .build();
        edgeRegistry.put(edgeId, edge);
    }

    private String generateDeterministicGraphId(ProcessKnowledgeDTO knowledge) {
        if (knowledge == null) {
            return "graph-process-canonical";
        }
        if (knowledge.activities() != null && !knowledge.activities().isEmpty()) {
            for (String act : knowledge.activities()) {
                if (act != null && !act.isBlank()) {
                    return "graph-" + slugify(act);
                }
            }
        }
        return "graph-process-canonical";
    }

    private boolean isSemanticMatch(String role, String activity) {
        if (role.contains("finance") && activity.contains("budget")) return true;
        if (role.contains("travel desk") && (activity.contains("book") || activity.contains("travel"))) return true;
        if (role.contains("manager") && (activity.contains("review") || activity.contains("approv"))) return true;
        if (role.contains("employee") && (activity.contains("submit") || activity.contains("request"))) return true;
        return false;
    }

    private String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "item";
        }
        String cleaned = text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "item" : cleaned;
    }

    private record GatewayPlacement(GraphNode gatewayNode, int targetActivityIndex, String conditionLabel) {}
}
