package com.pie.backend.service;

import com.pie.shared.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProcessGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(ProcessGraphBuilder.class);

    private static final Pattern TIMEOUT_PATTERN = Pattern.compile(
            "(?i)(?:within|after|in)\\s+(\\d+)\\s*(day|days|hour|hours|week|weeks|month|months|minute|minutes|d|h|m)"
    );

    private final ProcessGraphValidator validator;

    public ProcessGraphBuilder(ProcessGraphValidator validator) {
        this.validator = validator;
    }

    public ProcessGraphDTO build(ProcessKnowledgeDTO knowledge) {
        validator.validateInput(knowledge);
        String graphId = generateDeterministicGraphId(knowledge);
        return build(graphId, knowledge);
    }

    public ProcessGraphDTO build(String graphId, ProcessKnowledgeDTO knowledge) {
        validator.validateInput(knowledge);

        if (graphId == null || graphId.isBlank()) {
            graphId = generateDeterministicGraphId(knowledge);
        }

        Map<String, GraphNode> nodeRegistry = new LinkedHashMap<>();
        Map<String, GraphEdge> edgeRegistry = new LinkedHashMap<>();

        // 1. Create Nodes
        List<GraphNode> activityNodes = createActivityNodes(knowledge, nodeRegistry);
        List<GraphNode> roleNodes = createRoleNodes(knowledge, nodeRegistry);
        List<GraphNode> gatewayNodes = createGatewayNodes(knowledge, nodeRegistry);
        List<GraphNode> systemNodes = createSystemNodes(knowledge, nodeRegistry);
        List<GraphNode> eventNodes = createEventNodes(knowledge, nodeRegistry);
        List<GraphNode> dataNodes = createDataArtifactNodes(knowledge, nodeRegistry);

        // 2. Build Non-Flow Associations (Role, System, DataArtifact)
        buildRoleAssociations(activityNodes, roleNodes, knowledge, edgeRegistry);
        buildSystemAssociations(activityNodes, systemNodes, knowledge, edgeRegistry);
        buildDataArtifactAssociations(activityNodes, dataNodes, knowledge, edgeRegistry);

        // 3. Build Process Flow (Gateways, Sequences, Loops, Retries, Timeouts, Convergence, End Events)
        buildSemanticProcessFlow(activityNodes, gatewayNodes, eventNodes, knowledge, nodeRegistry, edgeRegistry);

        // 4. Prune Unreachable Nodes (prevent dangling/unused phantom end events)
        pruneUnreachableEvents(nodeRegistry, edgeRegistry);

        // 5. Assemble Graph and Validate
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId(graphId)
                .addNodes(new ArrayList<>(nodeRegistry.values()))
                .addEdges(new ArrayList<>(edgeRegistry.values()))
                .build();

        validator.validateGraph(graph);

        log.info("Successfully built ProcessGraphDTO [{}]: {} nodes, {} edges",
                graph.getGraphId(), graph.getNodes().size(), graph.getEdges().size());

        return graph;
    }

    // ==========================================
    // 1. NODE CREATION METHODS
    // ==========================================

    private List<GraphNode> createActivityNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        if (knowledge.activities() == null) return list;

        List<String> activities = knowledge.activities().stream()
                .filter(raw -> raw != null && !raw.isBlank())
                .map(String::trim)
                .sorted(Comparator.comparingInt(this::activityOrderScore))
                .toList();

        for (String raw : activities) {
            if (raw == null || raw.isBlank()) continue;
            String label = raw.trim();
            String taskType = "USER_TASK"; // Default fallback

            // EXTRACT AI SEMANTIC TAG (e.g., "Review Request [SERVICE_TASK]")
            if (label.matches(".*\\[[A-Z_]+\\]$")) {
                int bracketIdx = label.lastIndexOf('[');
                taskType = label.substring(bracketIdx + 1, label.length() - 1);
                label = label.substring(0, bracketIdx).trim();
            }

            String id = "activity-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Activity)
                        .label(label)
                        // Inject the AI-detected taskType into metadata
                        .metadata(NodeMetadata.builder().taskType(taskType).build()) 
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private int activityOrderScore(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "submit", "receive", "initiat", "trigger")) return 10;
        if (containsAny(lower, "complete", "finish", "archive", "close")) return 90;
        return 50;
    }

    private boolean containsAny(String value, String... candidates) {
        return Arrays.stream(candidates).anyMatch(value::contains);
    }

    private List<GraphNode> createRoleNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        List<String> combinedRoles = new ArrayList<>();

        if (knowledge.actors() != null) combinedRoles.addAll(knowledge.actors());
        if (knowledge.roles() != null) combinedRoles.addAll(knowledge.roles());

        for (String raw : combinedRoles) {
            if (raw == null || raw.isBlank()) continue;
            String label = raw.trim();

            if (isLikelySystem(label, knowledge.systems())) {
                continue;
            }

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
        if (knowledge.gateways() == null) return list;

        for (String raw : knowledge.gateways()) {
            if (raw == null || raw.isBlank()) continue;
            String label = raw.trim();
            GatewayType gatewayType = GatewayType.exclusive; // Default fallback

            // EXTRACT AI SEMANTIC TAG (e.g., "Is Approved? [PARALLEL]")
            if (label.matches(".*\\[[A-Z_]+\\]$")) {
                int bracketIdx = label.lastIndexOf('[');
                String tag = label.substring(bracketIdx + 1, label.length() - 1).toUpperCase();
                label = label.substring(0, bracketIdx).trim();
                
                if (tag.equals("PARALLEL")) gatewayType = GatewayType.parallel;
                else if (tag.equals("INCLUSIVE")) gatewayType = GatewayType.inclusive;
            }

            String id = "gateway-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Gateway)
                        .label(label)
                        .metadata(NodeMetadata.builder().gatewayType(gatewayType).build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private List<GraphNode> createSystemNodes(ProcessKnowledgeDTO knowledge, Map<String, GraphNode> nodeRegistry) {
        List<GraphNode> list = new ArrayList<>();
        if (knowledge.systems() == null) return list;

        for (String raw : knowledge.systems()) {
            if (raw == null || raw.isBlank()) continue;
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

        List<String> rawEvents = new ArrayList<>();
        for (String e : knowledge.events()) {
            if (e != null && !e.isBlank()) rawEvents.add(e.trim());
        }

        for (int i = 0; i < rawEvents.size(); i++) {
            String label = rawEvents.get(i);
            EventType eventType = EventType.intermediate; // Default fallback

            // EXTRACT AI SEMANTIC TAG (e.g., "Wait 1 business day [TIMER]")
            if (label.matches(".*\\[[A-Z_]+\\]$")) {
                int bracketIdx = label.lastIndexOf('[');
                String tag = label.substring(bracketIdx + 1, label.length() - 1).toUpperCase();
                label = label.substring(0, bracketIdx).trim();
                
                if (tag.equals("START")) eventType = EventType.start;
                else if (tag.equals("END")) eventType = EventType.end;
                else if (tag.equals("TIMER")) eventType = EventType.timer;
                else if (tag.equals("MESSAGE")) eventType = EventType.intermediate; // No message enum yet
            }

            String id = "event-" + slugify(label);
            String lower = label.toLowerCase(Locale.ROOT);

            String duration = null;

            if (eventType == EventType.timer || lower.contains("timer") || lower.contains("timeout") || lower.contains("day") || lower.contains("hour")) {
                if (eventType == EventType.intermediate) eventType = EventType.timer;
                duration = extractDurationIso(label);
            } else if (eventType == EventType.intermediate && i == 0 && (lower.contains("start") || lower.contains("trigger") || lower.contains("initiat") || rawEvents.size() > 1)) {
                eventType = EventType.start;
            } else if (eventType == EventType.intermediate && (lower.contains("end") || lower.contains("complet") || lower.contains("finish") || lower.contains("archived") || i == rawEvents.size() - 1)) {
                eventType = EventType.end;
            }

            if (!nodeRegistry.containsKey(id)) {
                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Event)
                        .label(label)
                        .metadata(NodeMetadata.builder().eventType(eventType).duration(duration).build())
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
        if (knowledge.inputs() != null) rawData.addAll(knowledge.inputs());
        if (knowledge.outputs() != null) rawData.addAll(knowledge.outputs());

        for (String raw : rawData) {
            if (raw == null || raw.isBlank()) continue;
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

    // ==========================================
    // 2. ASSOCIATION BUILDERS
    // ==========================================

    private void buildRoleAssociations(List<GraphNode> activityNodes, List<GraphNode> roleNodes,
                                       ProcessKnowledgeDTO knowledge, Map<String, GraphEdge> edgeRegistry) {
        if (activityNodes.isEmpty() || roleNodes.isEmpty()) return;

        Set<Integer> matchedActivityIndices = new HashSet<>();

        for (int i = 0; i < activityNodes.size(); i++) {
            GraphNode actNode = activityNodes.get(i);
            String actLabel = actNode.getLabel().toLowerCase(Locale.ROOT);

            GraphNode bestRole = null;
            int bestScore = 0;

            for (GraphNode roleNode : roleNodes) {
                String roleLabel = roleNode.getLabel().toLowerCase(Locale.ROOT);
                if (actLabel.startsWith(roleLabel) || actLabel.contains(roleLabel)) {
                    int score = roleLabel.length();
                    if (score > bestScore) {
                        bestScore = score;
                        bestRole = roleNode;
                    }
                }
            }

            if (bestRole != null) {
                addAssociation(bestRole, actNode, edgeRegistry);
                matchedActivityIndices.add(i);
            }
        }

        if (matchedActivityIndices.isEmpty() && roleNodes.size() == activityNodes.size()) {
            for (int i = 0; i < activityNodes.size(); i++) {
                addAssociation(roleNodes.get(i), activityNodes.get(i), edgeRegistry);
            }
        }
    }

    private void buildSystemAssociations(List<GraphNode> activityNodes, List<GraphNode> systemNodes,
                                         ProcessKnowledgeDTO knowledge, Map<String, GraphEdge> edgeRegistry) {
        if (activityNodes.isEmpty() || systemNodes.isEmpty()) return;

        for (GraphNode systemNode : systemNodes) {
            String systemLower = systemNode.getLabel().toLowerCase(Locale.ROOT);
            for (GraphNode actNode : activityNodes) {
                String actLower = actNode.getLabel().toLowerCase(Locale.ROOT);
                if (actLower.contains(systemLower) || (systemLower.contains("payment") && actLower.contains("payment")) ||
                    (systemLower.contains("erp") && actLower.contains("erp"))) {
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
        if (activityNodes.isEmpty() || dataNodes.isEmpty()) return;

        for (GraphNode dataNode : dataNodes) {
            String dataLower = dataNode.getLabel().toLowerCase(Locale.ROOT);
            for (GraphNode actNode : activityNodes) {
                String actLower = actNode.getLabel().toLowerCase(Locale.ROOT);
                if (actLower.contains(dataLower) || isDataArtifactRelevant(dataLower, actLower)) {
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
                .confidence(0.95)
                .build();
        edgeRegistry.put(edgeId, edge);

        if (fromNode.getType() == NodeType.Role && toNode.getMetadata() != null) {
            toNode.getMetadata().setRoleRef(fromNode.getId());
        }
    }

    // ==========================================
    // 3. PROCESS FLOW & GATEWAY TOPOLOGY ENGINE
    // ==========================================

    private void buildSemanticProcessFlow(List<GraphNode> activityNodes,
                                          List<GraphNode> gatewayNodes,
                                          List<GraphNode> eventNodes,
                                          ProcessKnowledgeDTO knowledge,
                                          Map<String, GraphNode> nodeRegistry,
                                          Map<String, GraphEdge> edgeRegistry) {
        if (activityNodes.isEmpty()) return;

        // 1. Connect Start Event -> Initial Activity
        GraphNode startEvent = eventNodes.stream()
                .filter(e -> e.getMetadata() != null && e.getMetadata().getEventType() == EventType.start)
                .findFirst()
                .orElse(null);
        if (startEvent != null) {
            addSequenceEdge(startEvent.getId(), activityNodes.get(0).getId(), edgeRegistry);
        }

        // Find primary End Event dynamically
        GraphNode endEvent = eventNodes.stream()
                .filter(e -> e.getMetadata() != null && e.getMetadata().getEventType() == EventType.end)
                .findFirst()
                .orElse(null);

        // 2. Parse explicit branching rules
        List<ParsedBranchRule> parsedRules = parseBusinessRules(knowledge, activityNodes, gatewayNodes, endEvent);

        Set<String> nodesWithOutgoingFlow = new HashSet<>();
        Set<String> exceptionTargetNodes = new HashSet<>();
        Set<String> gatewayPredecessors = new HashSet<>();

        if (startEvent != null) nodesWithOutgoingFlow.add(startEvent.getId());

        // Process explicit Gateway Rules
        for (ParsedBranchRule rule : parsedRules) {
            if (rule.evaluatingActivityId != null && rule.gatewayId != null) {
                addSequenceEdge(rule.evaluatingActivityId, rule.gatewayId, edgeRegistry);
                nodesWithOutgoingFlow.add(rule.evaluatingActivityId);
                gatewayPredecessors.add(rule.evaluatingActivityId);
            }

            for (BranchTarget branch : rule.branches) {
                if (branch.targetNodeId != null && rule.gatewayId != null) {
                    exceptionTargetNodes.add(branch.targetNodeId);

                    if (branch.isTimer) {
                        String timerEventId = "event-timer-" + slugify(branch.conditionLabel);
                        if (!nodeRegistry.containsKey(timerEventId)) {
                            GraphNode timerNode = GraphNode.builder()
                                    .id(timerEventId)
                                    .type(NodeType.Event)
                                    .label(branch.conditionLabel)
                                    .metadata(NodeMetadata.builder()
                                            .eventType(EventType.timer)
                                            .duration(extractDurationIso(branch.conditionLabel))
                                            .build())
                                    .build();
                            nodeRegistry.put(timerEventId, timerNode);
                        }
                        addConditionalEdge(rule.gatewayId, timerEventId, branch.conditionLabel, edgeRegistry);
                        addSequenceEdge(timerEventId, branch.targetNodeId, edgeRegistry);
                        nodesWithOutgoingFlow.add(timerEventId);
                    } else {
                        addConditionalEdge(rule.gatewayId, branch.targetNodeId, branch.conditionLabel, edgeRegistry);
                    }
                    nodesWithOutgoingFlow.add(rule.gatewayId);
                }
            }

            // Loop back connections
            if (rule.loopBackSourceId != null && rule.loopBackTargetId != null) {
                addSequenceEdge(rule.loopBackSourceId, rule.loopBackTargetId, edgeRegistry);
                nodesWithOutgoingFlow.add(rule.loopBackSourceId);
            }
        }

        // 3. Connect sequential sub-flows for ANY generic process
        for (int i = 0; i < activityNodes.size() - 1; i++) {
            GraphNode current = activityNodes.get(i);
            GraphNode next = activityNodes.get(i + 1);

            // Connect only if current has no outgoing flow and next is not a branch target
            if (!gatewayPredecessors.contains(current.getId()) &&
                !nodesWithOutgoingFlow.contains(current.getId()) &&
                !exceptionTargetNodes.contains(next.getId()) &&
                !isTerminalActivity(current.getLabel())) {
                
                addSequenceEdge(current.getId(), next.getId(), edgeRegistry);
                nodesWithOutgoingFlow.add(current.getId());
            }
        }

        // 4. Attach terminal nodes to the End Event
        if (endEvent != null) {
            for (GraphNode current : activityNodes) {
                // If a node was left hanging with no outgoing connections, plug it into the End Event
                if (!nodesWithOutgoingFlow.contains(current.getId())) {
                    addSequenceEdge(current.getId(), endEvent.getId(), edgeRegistry);
                    nodesWithOutgoingFlow.add(current.getId());
                }
            }
        }
    }

    // ==========================================
    // 4. PARSER FOR RULES, BRANCHES & LOOPS
    // ==========================================

    private String extractActionPart(String rule) {
        if (rule == null || rule.isBlank()) return "";
        
        // Parse standard "IF condition THEN action" format
        int thenIdx = rule.toLowerCase(Locale.ROOT).indexOf("then ");
        if (thenIdx >= 0) {
            return rule.substring(thenIdx + 5).trim();
        }
        
        // Parse comma-separated "If condition, action" format
        int commaIdx = rule.indexOf(',');
        if (commaIdx >= 0 && commaIdx < rule.length() - 1) {
            String afterComma = rule.substring(commaIdx + 1).trim();
            int elseIdx = afterComma.toLowerCase(Locale.ROOT).indexOf("else ");
            if (elseIdx > 0) {
                afterComma = afterComma.substring(0, elseIdx).trim();
            }
            return afterComma;
        }
        return rule;
    }

    private String extractConditionLabel(String rule) {
        if (rule == null || rule.isBlank()) return "yes";
        String lower = rule.toLowerCase(Locale.ROOT);
        
        int ifIdx = lower.indexOf("if ");
        int thenIdx = lower.indexOf(" then");
        int commaIdx = lower.indexOf(',');
        
        int endIdx = thenIdx > 0 ? thenIdx : (commaIdx > ifIdx ? commaIdx : -1);
        
        if (ifIdx >= 0 && endIdx > ifIdx + 3) {
            String cond = rule.substring(ifIdx + 3, endIdx).trim();
            if (cond.length() <= 30) return cond; // Ensure labels don't get too long
        }
        
        // Generic fallbacks based on sentence sentiment
        if (lower.contains("reject") || lower.contains("fail") || lower.contains("invalid")) return "no";
        return "yes";
    }

    // ==========================================
    // 3. GENERIC BUSINESS RULE PARSER (AI TAG-AWARE)
    // ==========================================

    private List<ParsedBranchRule> parseBusinessRules(
            ProcessKnowledgeDTO knowledge,
            List<GraphNode> activityNodes,
            List<GraphNode> gatewayNodes,
            GraphNode endEvent) {

        List<ParsedBranchRule> rules = new ArrayList<>();
        List<String> businessRules = knowledge.businessRules() != null ? knowledge.businessRules() : List.of();

        // Ensure every gateway gets processed
        for (int i = 0; i < gatewayNodes.size(); i++) {
            GraphNode gw = gatewayNodes.get(i);
            String gwLabel = gw.getLabel().toLowerCase(Locale.ROOT).replaceAll("[?:]", "").trim();
            
            // 1. Identify which activity precedes this gateway (Evaluating Activity)
            GraphNode evalAct = null;
            if (!activityNodes.isEmpty()) {
                // If it's the first gateway, attach it to the first or second activity
                evalAct = activityNodes.get(Math.min(i, activityNodes.size() - 1)); 
            }
            String evalActId = evalAct != null ? evalAct.getId() : null;

            List<BranchTarget> branches = new ArrayList<>();

            // 2. Parse AI-generated Business Rules to find branches belonging to this gateway
            boolean ruleMatched = false;
            for (String ruleStr : businessRules) {
                String ruleLower = ruleStr.toLowerCase(Locale.ROOT);
                
                // If the rule mentions the gateway's topic
                if (ruleLower.contains(gwLabel) || hasHighTokenOverlap(ruleLower, gwLabel)) {
                    ruleMatched = true;
                    
                    // Parse "IF [Condition] THEN [Action]" or "IF [Condition], [Action]" format
                    String condition = extractConditionLabel(ruleStr);
                    String action = extractActionPart(ruleStr);
                    
                    GraphNode targetAct = findBestMatchingActivity(activityNodes, action);
                    
                    if (targetAct != null) {
                        boolean isTimer = condition.contains("timeout") || condition.contains("day") || condition.contains("hour");
                        branches.add(new BranchTarget(targetAct.getId(), condition, isTimer));
                    }
                }
            }

            // 3. Fallback: If no explicit AI rule matched, generate safe default branches
            if (!ruleMatched || branches.isEmpty()) {
                // Determine next sequential activity
                int evalIdx = evalAct != null ? activityNodes.indexOf(evalAct) : -1;
                GraphNode nextSeqAct = (evalIdx >= 0 && evalIdx + 1 < activityNodes.size()) 
                                        ? activityNodes.get(evalIdx + 1) : null;
                
                if (nextSeqAct != null) {
                    branches.add(new BranchTarget(nextSeqAct.getId(), "yes", false));
                }
                
                // Route the negative branch to the End Event or loop back
                if (endEvent != null && branches.size() < 2) {
                    branches.add(new BranchTarget(endEvent.getId(), "no", false));
                }
            }

            // Ensure a gateway always has at least 2 branches (otherwise it's not a decision)
            if (branches.size() == 1 && endEvent != null) {
                branches.add(new BranchTarget(endEvent.getId(), "no", false));
            }

            rules.add(new ParsedBranchRule(evalActId, gw.getId(), branches, null, null));
        }

        return rules;
    }

    // Helper: Find common tokens to match gateways to rules
    private boolean hasHighTokenOverlap(String text1, String text2) {
        Set<String> tokens1 = new HashSet<>(Arrays.asList(text1.split("\\W+")));
        Set<String> tokens2 = new HashSet<>(Arrays.asList(text2.split("\\W+")));
        tokens1.removeIf(t -> t.length() < 4); // ignore short words
        tokens2.removeIf(t -> t.length() < 4);
        
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        
        return !intersection.isEmpty();
    }

    private GraphNode findBestMatchingActivity(List<GraphNode> activityNodes, String... searchPhrases) {
        GraphNode bestNode = null;
        int bestScore = 0;

        for (GraphNode act : activityNodes) {
            String actLower = act.getLabel().toLowerCase(Locale.ROOT);

            for (String phrase : searchPhrases) {
                String phraseLower = phrase.toLowerCase(Locale.ROOT);

                int score = 0;
                if (actLower.equals(phraseLower)) {
                    score = 1000;
                } else if (actLower.contains(phraseLower)) {
                    score = 500 + phraseLower.length();
                } else if (phraseLower.contains(actLower)) {
                    score = 300 + actLower.length();
                } else {
                    for (String word : phraseLower.split("\\s+")) {
                        if (word.length() > 3 && actLower.contains(word)) {
                            score += word.length() * 10;
                        }
                    }
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestNode = act;
                }
            }
        }

        return bestScore > 0 ? bestNode : null;
    }

    private void pruneUnreachableEvents(Map<String, GraphNode> nodeRegistry, Map<String, GraphEdge> edgeRegistry) {
        Set<String> referencedNodeIds = new HashSet<>();
        for (GraphEdge edge : edgeRegistry.values()) {
            referencedNodeIds.add(edge.getFrom());
            referencedNodeIds.add(edge.getTo());
        }

        // Remove event nodes that have zero incoming/outgoing sequence edges (BUG 8 fix)
        List<String> toRemove = new ArrayList<>();
        for (GraphNode node : nodeRegistry.values()) {
            if (node.getType() == NodeType.Event) {
                if (!referencedNodeIds.contains(node.getId())) {
                    toRemove.add(node.getId());
                }
            }
        }

        for (String id : toRemove) {
            nodeRegistry.remove(id);
        }
    }

    // ==========================================
    // 5. HELPER UTILITIES
    // ==========================================

    private void addSequenceEdge(String fromId, String toId, Map<String, GraphEdge> edgeRegistry) {
        if (fromId.equals(toId)) return;
        String edgeId = "edge-" + fromId + "-" + toId + "-sequence";
        GraphEdge edge = GraphEdge.builder()
                .id(edgeId)
                .from(fromId)
                .to(toId)
                .edgeType(EdgeType.sequence)
                .confidence(0.98)
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
                .confidence(0.95)
                .build();
        edgeRegistry.put(edgeId, edge);
    }

    private boolean isLikelySystem(String name, List<String> systems) {
        if (systems != null && systems.contains(name)) return true;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith("system") || lower.endsWith("tool") || lower.endsWith("platform") ||
               lower.endsWith("service") || lower.endsWith("database") || lower.endsWith("engine") ||
               lower.equals("erp") || lower.equals("crm");
    }

    private boolean isDataArtifactRelevant(String data, String activity) {
        if (data.contains("receipt") && activity.contains("receipt")) return true;
        if (data.contains("budget") && activity.contains("budget")) return true;
        if (data.contains("expense") && (activity.contains("expense") || activity.contains("submit"))) return true;
        if (data.contains("payment") && activity.contains("payment")) return true;
        return false;
    }

    private boolean isTerminalActivity(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.contains("archive") || lower.contains("complete") || lower.contains("finish");
    }

    private String extractDurationIso(String text) {
        Matcher m = TIMEOUT_PATTERN.matcher(text);
        if (m.find()) {
            int num = Integer.parseInt(m.group(1));
            String unit = m.group(2).toLowerCase(Locale.ROOT);
            if (unit.startsWith("day") || unit.equals("d")) return "P" + num + "D";
            if (unit.startsWith("week") || unit.equals("w")) return "P" + (num * 7) + "D";
            if (unit.startsWith("hour") || unit.equals("h")) return "PT" + num + "H";
            if (unit.startsWith("minute") || unit.equals("m")) return "PT" + num + "M";
        }
        return "P7D";
    }

    private String generateDeterministicGraphId(ProcessKnowledgeDTO knowledge) {
        if (knowledge == null) return "graph-process-canonical";
        if (knowledge.activities() != null && !knowledge.activities().isEmpty()) {
            for (String act : knowledge.activities()) {
                if (act != null && !act.isBlank()) {
                    return "graph-" + slugify(act);
                }
            }
        }
        return "graph-process-canonical";
    }

    private String slugify(String text) {
        if (text == null || text.isBlank()) return "item";
        String cleaned = text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "item" : cleaned;
    }

    private record BranchTarget(String targetNodeId, String conditionLabel, boolean isTimer) {}

    private record ParsedBranchRule(
            String evaluatingActivityId,
            String gatewayId,
            List<BranchTarget> branches,
            String loopBackSourceId,
            String loopBackTargetId
    ) {}
}
