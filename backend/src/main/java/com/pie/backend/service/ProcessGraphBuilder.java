package com.pie.backend.service;

import com.pie.shared.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProcessGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(ProcessGraphBuilder.class);

    private static final Pattern TIMEOUT_PATTERN = Pattern.compile(
            "(?i)(?:within|after|in)\\s+(\\d+)\\s*(day|days|hour|hours|week|weeks|month|months|minute|minutes|d|h|m)"
    );

    // Generic process nouns/verbs that must be IGNORED when computing token overlap
    // between rules and gateways — otherwise every rule matches every gateway.
    private static final Set<String> TOKEN_STOPWORDS = Set.of(
            "request", "requests", "requested", "process", "processes", "task", "tasks",
            "activity", "activities", "step", "steps", "employee", "employees", "manager",
            "managers", "user", "users", "system", "systems", "record", "records",
            "information", "data", "form", "forms", "case", "cases", "then", "with",
            "into", "from", "this", "that", "them", "their", "will", "shall", "must",
            "have", "been", "when", "where", "which", "while"
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
            String explicitRole = null;

            // Check if label ends with (RoleName), e.g. "Review Request (Manager)" or "Review Request [USER_TASK] (Manager)"
            Matcher mParen = Pattern.compile("\\s*\\(([a-zA-Z0-9_\\s-]+)\\)\\s*$").matcher(label);
            if (mParen.find()) {
                explicitRole = mParen.group(1).trim();
                label = label.substring(0, mParen.start()).trim();
            }

            // Check if label ends with @RoleName, e.g. "Review Request @Manager"
            Matcher mAt = Pattern.compile("\\s*@([a-zA-Z0-9_\\s-]+)\\s*$").matcher(label);
            if (mAt.find() && explicitRole == null) {
                explicitRole = mAt.group(1).trim();
                label = label.substring(0, mAt.start()).trim();
            }

            // EXTRACT AI SEMANTIC TAG (e.g., "Review Request [SERVICE_TASK]")
            if (label.matches(".*\\[[A-Z_]+\\]$")) {
                int bracketIdx = label.lastIndexOf('[');
                taskType = label.substring(bracketIdx + 1, label.length() - 1);
                label = label.substring(0, bracketIdx).trim();
            }

            // Check again in case (RoleName) was before [TASK_TYPE], e.g. "Review Request (Manager) [USER_TASK]"
            if (explicitRole == null) {
                Matcher m2 = Pattern.compile("\\s*\\(([a-zA-Z0-9_\\s-]+)\\)\\s*$").matcher(label);
                if (m2.find()) {
                    explicitRole = m2.group(1).trim();
                    label = label.substring(0, m2.start()).trim();
                }
            }

            // Check for prefix "Actor: Task", e.g. "Manager: Review Request"
            int colonIdx = label.indexOf(':');
            if (colonIdx > 0 && colonIdx < 30 && explicitRole == null) {
                String candidateRole = label.substring(0, colonIdx).trim();
                if (containsAnyIgnoreCase(candidateRole, knowledge.actors(), knowledge.roles())) {
                    explicitRole = candidateRole;
                    label = label.substring(colonIdx + 1).trim();
                }
            }

            String id = "activity-" + slugify(label);

            if (!nodeRegistry.containsKey(id)) {
                NodeMetadata.Builder metaBuilder = NodeMetadata.builder().taskType(taskType);
                if (explicitRole != null && !explicitRole.isBlank()) {
                    metaBuilder.roleRef("role-" + slugify(explicitRole));
                }

                GraphNode node = GraphNode.builder()
                        .id(id)
                        .type(NodeType.Activity)
                        .label(label)
                        .metadata(metaBuilder.build())
                        .build();
                nodeRegistry.put(id, node);
                list.add(node);
            }
        }
        return list;
    }

    private boolean containsAnyIgnoreCase(String value, List<String> list1, List<String> list2) {
        String val = value.trim().toLowerCase(Locale.ROOT);
        if (list1 != null) {
            for (String s : list1) {
                if (s != null && s.trim().equalsIgnoreCase(val)) return true;
            }
        }
        if (list2 != null) {
            for (String s : list2) {
                if (s != null && s.trim().equalsIgnoreCase(val)) return true;
            }
        }
        return false;
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

            // Filter out placeholder roles like "unassigned" if other valid roles exist
            if (label.equalsIgnoreCase("unassigned") || label.equalsIgnoreCase("unknown") ||
                label.equalsIgnoreCase("none") || label.equalsIgnoreCase("n/a")) {
                boolean hasOtherRoles = combinedRoles.stream().anyMatch(r -> r != null && !r.isBlank() &&
                        !r.equalsIgnoreCase("unassigned") && !r.equalsIgnoreCase("unknown") &&
                        !r.equalsIgnoreCase("none") && !r.equalsIgnoreCase("n/a"));
                if (hasOtherRoles) {
                    continue;
                }
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

        // Also ensure any explicit roles tagged on activities are registered as Role nodes
        for (GraphNode act : nodeRegistry.values()) {
            if (act.getType() == NodeType.Activity && act.getMetadata() != null && act.getMetadata().getRoleRef() != null) {
                String roleId = act.getMetadata().getRoleRef();
                if (!nodeRegistry.containsKey(roleId)) {
                    String roleLabel = roleId.replaceFirst("^role-", "").replace('-', ' ');
                    roleLabel = Character.toUpperCase(roleLabel.charAt(0)) + roleLabel.substring(1);
                    GraphNode roleNode = GraphNode.builder()
                            .id(roleId)
                            .type(NodeType.Role)
                            .label(roleLabel)
                            .metadata(NodeMetadata.builder().build())
                            .build();
                    nodeRegistry.put(roleId, roleNode);
                    list.add(roleNode);
                }
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

        // Pass 1: Explicit roleRef already parsed from activity label or set in metadata
        for (int i = 0; i < activityNodes.size(); i++) {
            GraphNode actNode = activityNodes.get(i);
            if (actNode.getMetadata() != null && actNode.getMetadata().getRoleRef() != null) {
                String targetRef = actNode.getMetadata().getRoleRef();
                GraphNode roleNode = roleNodes.stream()
                        .filter(r -> r.getId().equalsIgnoreCase(targetRef) ||
                                     slugify(r.getLabel()).equalsIgnoreCase(targetRef.replaceFirst("^role-", "")))
                        .findFirst()
                        .orElse(null);
                if (roleNode != null) {
                    addAssociation(roleNode, actNode, edgeRegistry);
                    matchedActivityIndices.add(i);
                }
            }
        }

        // Pass 2: Direct string match (activity label contains role label)
        for (int i = 0; i < activityNodes.size(); i++) {
            if (matchedActivityIndices.contains(i)) continue;
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

        // Pass 3: Semantic role heuristics
        for (int i = 0; i < activityNodes.size(); i++) {
            if (matchedActivityIndices.contains(i)) continue;
            GraphNode actNode = activityNodes.get(i);
            String actLabel = actNode.getLabel().toLowerCase(Locale.ROOT);

            GraphNode bestRole = findSemanticRoleMatch(actLabel, roleNodes);
            if (bestRole != null) {
                addAssociation(bestRole, actNode, edgeRegistry);
                matchedActivityIndices.add(i);
            }
        }

        // Pass 4: Fallback distribution for remaining unassigned activities
        // Ensure no activity is left orphan and every participant has assigned tasks
        if (matchedActivityIndices.size() < activityNodes.size()) {
            if (roleNodes.size() == 1) {
                // If only 1 role exists, assign all unassigned to that role
                GraphNode singleRole = roleNodes.get(0);
                for (int i = 0; i < activityNodes.size(); i++) {
                    if (!matchedActivityIndices.contains(i)) {
                        addAssociation(singleRole, activityNodes.get(i), edgeRegistry);
                        matchedActivityIndices.add(i);
                    }
                }
            } else if (matchedActivityIndices.isEmpty() && roleNodes.size() == activityNodes.size()) {
                // Exact 1-to-1 match fallback
                for (int i = 0; i < activityNodes.size(); i++) {
                    addAssociation(roleNodes.get(i), activityNodes.get(i), edgeRegistry);
                }
            } else {
                // Prioritize giving tasks to roles that currently have 0 assigned activities
                Set<String> assignedRoleIds = edgeRegistry.values().stream()
                        .filter(e -> e.getEdgeType() == EdgeType.association)
                        .map(GraphEdge::getFrom)
                        .collect(Collectors.toSet());

                List<GraphNode> hungryRoles = roleNodes.stream()
                        .filter(r -> !assignedRoleIds.contains(r.getId()))
                        .toList();

                int hungryIdx = 0;
                for (int i = 0; i < activityNodes.size(); i++) {
                    if (!matchedActivityIndices.contains(i)) {
                        GraphNode assignedRole;
                        if (hungryIdx < hungryRoles.size()) {
                            assignedRole = hungryRoles.get(hungryIdx++);
                        } else {
                            // Assign to first role or previous activity's role
                            assignedRole = roleNodes.get(0);
                        }
                        addAssociation(assignedRole, activityNodes.get(i), edgeRegistry);
                        matchedActivityIndices.add(i);
                    }
                }
            }
        }
    }

    private GraphNode findSemanticRoleMatch(String actLabel, List<GraphNode> roleNodes) {
        // Priority 0: Explicit requester initiation action (Submit, Apply, Initiate, Draft) -> Employee / Requester
        if (containsAny(actLabel, "submit", "apply", "initiat", "draft", "fill out", "enter")) {
            for (GraphNode r : roleNodes) {
                String rl = r.getLabel().toLowerCase(Locale.ROOT);
                if (containsAny(rl, "employee", "applicant", "initiat", "staff", "user", "customer", "operator", "requester")) {
                    return r;
                }
            }
        }
        // Priority 1: Manager / Reviewer / Approver
        for (GraphNode r : roleNodes) {
            String rl = r.getLabel().toLowerCase(Locale.ROOT);
            if (containsAny(rl, "manager", "approv", "lead", "supervisor", "head", "director", "reviewer")) {
                if (containsAny(actLabel, "approv", "review", "authoriz", "reject", "sign off", "evaluat", "assess", "escalat", "exception")) {
                    return r;
                }
            }
        }
        // Priority 2: Finance / Accounts
        for (GraphNode r : roleNodes) {
            String rl = r.getLabel().toLowerCase(Locale.ROOT);
            if (containsAny(rl, "finance", "account", "billing", "treasury", "payroll")) {
                if (containsAny(actLabel, "pay", "invoice", "reimburse", "budget", "billing", "disburse", "disbursement", "ledger", "fund", "financial")) {
                    return r;
                }
            }
        }
        // Priority 3: Travel
        for (GraphNode r : roleNodes) {
            String rl = r.getLabel().toLowerCase(Locale.ROOT);
            if (containsAny(rl, "travel", "desk", "flight", "hotel", "logistics")) {
                if (containsAny(actLabel, "book", "flight", "hotel", "ticket", "travel", "reservation")) {
                    return r;
                }
            }
        }
        // Priority 4: HR
        for (GraphNode r : roleNodes) {
            String rl = r.getLabel().toLowerCase(Locale.ROOT);
            if (containsAny(rl, "hr", "human", "recruit", "talent")) {
                if (containsAny(actLabel, "onboard", "hire", "interview", "candidate", "training", "orient")) {
                    return r;
                }
            }
        }
        // Priority 5: Employee / Requester (only if not an approval or review task)
        if (!containsAny(actLabel, "review", "approv", "authoriz", "evaluat", "assess", "sign off")) {
            for (GraphNode r : roleNodes) {
                String rl = r.getLabel().toLowerCase(Locale.ROOT);
                if (containsAny(rl, "employee", "applicant", "initiat", "staff", "user", "customer", "operator", "requester")) {
                    if (containsAny(actLabel, "submit", "request", "create", "draft", "fill", "apply", "enter", "initiat", "upload", "attach", "provide")) {
                        return r;
                    }
                }
            }
        }
        return null;
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

        // Collect start / end events up front.
        List<GraphNode> startEvents = eventNodes.stream()
                .filter(e -> e.getMetadata() != null && e.getMetadata().getEventType() == EventType.start)
                .toList();
        GraphNode startEvent = startEvents.isEmpty() ? null : startEvents.get(0);

        List<GraphNode> endEvents = eventNodes.stream()
                .filter(e -> e.getMetadata() != null && e.getMetadata().getEventType() == EventType.end)
                .toList();
        GraphNode endEvent = endEvents.isEmpty() ? null : endEvents.get(0);

        // Parse rules BEFORE wiring the start — a leading parallel fork with no
        // dedicated evaluator activity needs the start event routed to it directly.
        List<ParsedBranchRule> parsedRules = parseBusinessRules(knowledge, activityNodes, gatewayNodes, endEvent);

        // Identify parallel fork gateways that expect the start event as their feeder
        // (parallel type, more than one branch, no evaluator activity assigned).
        GraphNode leadingParallelFork = null;
        Set<String> forkBranchActivityIds = new HashSet<>();
        for (ParsedBranchRule r : parsedRules) {
            GraphNode gw = nodeRegistry.get(r.gatewayId);
            boolean isFork = gw != null && gw.getMetadata() != null
                    && gw.getMetadata().getGatewayType() == GatewayType.parallel
                    && r.branches.size() > 1
                    && r.evaluatingActivityId == null;
            if (isFork) {
                leadingParallelFork = gw;
                for (BranchTarget bt : r.branches) forkBranchActivityIds.add(bt.targetNodeId);
                break;
            }
        }

        // 1. Wire start event(s).
        if (startEvents.size() == 1 && startEvent != null) {
            if (leadingParallelFork != null) {
                addSequenceEdge(startEvent.getId(), leadingParallelFork.getId(), edgeRegistry);
            } else {
                addSequenceEdge(startEvent.getId(), activityNodes.get(0).getId(), edgeRegistry);
            }
        } else if (startEvents.size() > 1) {
            Set<String> claimedActivityIds = new HashSet<>();
            for (GraphNode s : startEvents) {
                GraphNode target = pickBestUnclaimedActivity(activityNodes, s.getLabel(), claimedActivityIds);
                if (target == null) target = activityNodes.get(0);
                addSequenceEdge(s.getId(), target.getId(), edgeRegistry);
                claimedActivityIds.add(target.getId());
            }
        }

        Set<String> nodesWithOutgoingFlow = new HashSet<>();
        Set<String> exceptionTargetNodes = new HashSet<>();
        Set<String> gatewayPredecessors = new HashSet<>();

        if (startEvent != null) nodesWithOutgoingFlow.add(startEvent.getId());

        // Track fork-branch terminal activities so a following parallel JOIN
        // can vacuum them up as incoming flows.
        Set<String> parallelForkBranchTargets = new LinkedHashSet<>();

        // Process explicit Gateway Rules
        for (ParsedBranchRule rule : parsedRules) {
            GraphNode gwNode = nodeRegistry.get(rule.gatewayId);
            boolean isParallel = gwNode != null && gwNode.getMetadata() != null
                    && gwNode.getMetadata().getGatewayType() == GatewayType.parallel;
            boolean isParallelJoin = isParallel && rule.branches.size() == 1;
            boolean isParallelFork = isParallel && rule.branches.size() > 1;

            if (rule.evaluatingActivityId != null && rule.gatewayId != null && !isParallelJoin) {
                addSequenceEdge(rule.evaluatingActivityId, rule.gatewayId, edgeRegistry);
                nodesWithOutgoingFlow.add(rule.evaluatingActivityId);
                gatewayPredecessors.add(rule.evaluatingActivityId);
            }

            // Parallel join: pull in every open fork-branch terminal instead of
            // relying on evalAct alone.
            if (isParallelJoin) {
                for (String forkTarget : parallelForkBranchTargets) {
                    if (!nodesWithOutgoingFlow.contains(forkTarget)) {
                        addSequenceEdge(forkTarget, rule.gatewayId, edgeRegistry);
                        nodesWithOutgoingFlow.add(forkTarget);
                    }
                }
                parallelForkBranchTargets.clear();
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
                    } else if (isParallel) {
                        // Parallel branches carry no condition label
                        addSequenceEdge(rule.gatewayId, branch.targetNodeId, edgeRegistry);
                    } else {
                        addConditionalEdge(rule.gatewayId, branch.targetNodeId, branch.conditionLabel, edgeRegistry);
                    }
                    nodesWithOutgoingFlow.add(rule.gatewayId);

                    if (isParallelFork) {
                        parallelForkBranchTargets.add(branch.targetNodeId);
                    }
                }
            }

            // Loop back connections
            if (rule.loopBackSourceId != null && rule.loopBackTargetId != null) {
                addSequenceEdge(rule.loopBackSourceId, rule.loopBackTargetId, edgeRegistry);
                nodesWithOutgoingFlow.add(rule.loopBackSourceId);
            }
        }

        // 3. Connect sequential sub-flows for ANY generic process.
        // Do NOT auto-link into an activity that is a gateway evaluator (its incoming
        // edge must come from its true predecessor, chosen by pickEvaluatingActivity)
        // and do NOT link out of a rejection/notification terminal into an unrelated
        // downstream branch.
        for (int i = 0; i < activityNodes.size() - 1; i++) {
            GraphNode current = activityNodes.get(i);
            GraphNode next = activityNodes.get(i + 1);

            if (gatewayPredecessors.contains(current.getId())) continue;
            if (nodesWithOutgoingFlow.contains(current.getId())) continue;
            if (exceptionTargetNodes.contains(next.getId())) continue;
            if (gatewayPredecessors.contains(next.getId())) continue; // avoid stealing the evaluator's incoming edge
            if (isTerminalActivity(current.getLabel())) continue;
            if (isBranchTerminalActivity(current.getLabel())) continue; // notify/inform/communicate end their branch

            addSequenceEdge(current.getId(), next.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(current.getId());
        }

        // 4. Attach terminal nodes to the End Event. With multiple end events,
        // route each hanging activity to the end whose label overlaps most —
        // preserves distinct end states (Approved end vs Rejected end vs Timeout end).
        if (endEvent != null) {
            for (GraphNode current : activityNodes) {
                if (nodesWithOutgoingFlow.contains(current.getId())) continue;
                GraphNode target = endEvents.size() > 1
                        ? pickBestEndEvent(endEvents, current.getLabel())
                        : endEvent;
                if (target == null) target = endEvent;
                addSequenceEdge(current.getId(), target.getId(), edgeRegistry);
                nodesWithOutgoingFlow.add(current.getId());
            }
        }
    }

    private GraphNode pickBestUnclaimedActivity(List<GraphNode> activities, String label, Set<String> claimed) {
        GraphNode best = null;
        int bestScore = 0;
        for (GraphNode a : activities) {
            if (claimed.contains(a.getId())) continue;
            int score = tokenOverlapScore(a.getLabel(), label);
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    private GraphNode pickBestEndEvent(List<GraphNode> ends, String label) {
        GraphNode best = null;
        int bestScore = 0;
        for (GraphNode e : ends) {
            int score = tokenOverlapScore(e.getLabel(), label);
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best != null ? best : ends.get(ends.size() - 1);
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
            if (cond.equalsIgnoreCase("within policy")) return "yes";
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

        // ==================================================================
        // Step 1: assign each business rule to AT MOST ONE gateway — the one
        // it overlaps with most strongly. Prevents duplication of branches.
        // ==================================================================
        Map<String, List<BranchTarget>> branchesByGateway = new LinkedHashMap<>();
        for (GraphNode gw : gatewayNodes) branchesByGateway.put(gw.getId(), new ArrayList<>());

        // Pending queue for rules that failed direct token match — assigned later by
        // complementary-condition heuristic so we don't drop them.
        List<BranchTarget> unmatched = new ArrayList<>();

        for (String ruleStr : businessRules) {
            if (ruleStr == null || ruleStr.isBlank()) continue;
            String ruleLower = ruleStr.toLowerCase(Locale.ROOT);
            String condition = extractConditionLabel(ruleStr);
            String action = extractActionPart(ruleStr);

            GraphNode bestGw = null;
            int bestScore = 0;
            for (GraphNode gw : gatewayNodes) {
                String gwLabel = gw.getLabel().toLowerCase(Locale.ROOT).replaceAll("[?:]", "").trim();
                int score = tokenOverlapScore(condition.toLowerCase(Locale.ROOT), gwLabel);
                // If condition tokens didn't discriminate, fall back to whole-rule overlap
                if (score == 0) score = tokenOverlapScore(ruleLower, gwLabel);
                if (score > bestScore) {
                    bestScore = score;
                    bestGw = gw;
                }
            }

            GraphNode targetAct = findBestMatchingActivity(activityNodes, action);
            if (targetAct == null) continue;

            boolean isTimer = condition.contains("timeout") || condition.contains("day") || condition.contains("hour");
            BranchTarget bt = new BranchTarget(targetAct.getId(), condition, isTimer);

            if (bestGw == null || bestScore == 0) {
                unmatched.add(bt);
            } else {
                branchesByGateway.get(bestGw.getId()).add(bt);
            }
        }

        // Complementary-condition pass: assign leftover rules (e.g. "IF Rejected...")
        // to a gateway that already holds the positive counterpart. If none qualifies,
        // route to the gateway with the fewest branches so far — a real gateway needs
        // at least two outgoing edges.
        for (BranchTarget bt : unmatched) {
            String cond = bt.conditionLabel() == null ? "" : bt.conditionLabel().toLowerCase(Locale.ROOT);
            boolean negative = containsAny(cond, "reject", "deni", "declin", "fail", "invalid", "insuffic", "not ");

            GraphNode target = null;
            if (negative) {
                for (Map.Entry<String, List<BranchTarget>> e : branchesByGateway.entrySet()) {
                    boolean hasPositive = e.getValue().stream().anyMatch(b -> {
                        String c = b.conditionLabel() == null ? "" : b.conditionLabel().toLowerCase(Locale.ROOT);
                        return containsAny(c, "approv", "success", "pass", "valid", "suffic", "eligibl", "accept");
                    });
                    if (hasPositive) {
                        target = gatewayNodes.stream().filter(g -> g.getId().equals(e.getKey())).findFirst().orElse(null);
                        break;
                    }
                }
            }
            if (target == null) {
                target = gatewayNodes.stream()
                        .min(Comparator.comparingInt(g -> branchesByGateway.get(g.getId()).size()))
                        .orElse(null);
            }
            if (target != null) branchesByGateway.get(target.getId()).add(bt);
        }

        // ==================================================================
        // Step 2: per-gateway — pick evalAct (activity that FEEDS the gateway)
        // and apply fallback branches ONLY when the AI provided none.
        // ==================================================================
        for (int i = 0; i < gatewayNodes.size(); i++) {
            GraphNode gw = gatewayNodes.get(i);
            String gwLabel = gw.getLabel().toLowerCase(Locale.ROOT).replaceAll("[?:]", "").trim();
            List<BranchTarget> branches = branchesByGateway.get(gw.getId());

            boolean isParallel = gw.getMetadata() != null
                    && gw.getMetadata().getGatewayType() == GatewayType.parallel;

            // Evaluating activity: prefer an activity whose label overlaps the gateway
            // topic AND is NOT one of this gateway's branch targets. Verbs like review,
            // decide, check, evaluate rank above the narrative-first activity fallback.
            Set<String> branchTargetIds = branches.stream()
                    .map(BranchTarget::targetNodeId)
                    .collect(Collectors.toSet());

            GraphNode evalAct;
            if (isParallel) {
                // Parallel gateways typically have no local evaluator activity —
                // fork is fed by the preceding sequential node / start event;
                // join is fed by all fork branch terminals (handled at wiring time).
                evalAct = pickParallelEvaluator(activityNodes, branchTargetIds, gwLabel, branches);
            } else {
                evalAct = pickEvaluatingActivity(activityNodes, gwLabel, branchTargetIds, i);
            }
            String evalActId = evalAct != null ? evalAct.getId() : null;

            // Fallback branches: exclusive/inclusive gateways only. Parallel
            // gateways never get synthetic yes/no or otherwise branches.
            if (!isParallel) {
                if (branches.isEmpty()) {
                    int evalIdx = evalAct != null ? activityNodes.indexOf(evalAct) : -1;
                    GraphNode nextSeqAct = (evalIdx >= 0 && evalIdx + 1 < activityNodes.size())
                            ? activityNodes.get(evalIdx + 1) : null;
                    if (nextSeqAct != null) {
                        branches.add(new BranchTarget(nextSeqAct.getId(), "yes", false));
                    }
                    if (endEvent != null && branches.size() < 2) {
                        branches.add(new BranchTarget(endEvent.getId(), "no", false));
                    }
                } else if (branches.size() == 1 && endEvent != null) {
                    // A real exclusive gateway needs at least two outgoing branches
                    branches.add(new BranchTarget(endEvent.getId(), "otherwise", false));
                }
            }

            rules.add(new ParsedBranchRule(evalActId, gw.getId(), branches, null, null));
        }

        return rules;
    }

    private GraphNode pickParallelEvaluator(List<GraphNode> activityNodes,
                                            Set<String> branchTargetIds,
                                            String gwLabel,
                                            List<BranchTarget> branches) {
        // Join gateways (one branch, "join"/"merge"/"after" keywords) shouldn't
        // pull a preceding activity — the fork branches feed them at wiring time.
        boolean joinLike = containsAny(gwLabel, "join", "merge", "after both", "once all", "when all", "converge");
        if (joinLike || branches.size() <= 1) return null;

        // Fork: use the last activity that appears BEFORE all branch targets in the
        // narrative order — this is the "preceding step" that fans out. If none
        // qualifies, leave null so the start event feeds the fork directly.
        int earliestBranchIdx = Integer.MAX_VALUE;
        for (int i = 0; i < activityNodes.size(); i++) {
            if (branchTargetIds.contains(activityNodes.get(i).getId())) {
                earliestBranchIdx = Math.min(earliestBranchIdx, i);
            }
        }
        if (earliestBranchIdx == 0 || earliestBranchIdx == Integer.MAX_VALUE) return null;
        return activityNodes.get(earliestBranchIdx - 1);
    }

    private GraphNode pickEvaluatingActivity(List<GraphNode> activityNodes, String gwLabel,
                                             Set<String> branchTargetIds, int gatewayIndex) {
        GraphNode best = null;
        int bestScore = 0;
        for (GraphNode act : activityNodes) {
            if (branchTargetIds.contains(act.getId())) continue; // can't be its own predecessor
            String actLower = act.getLabel().toLowerCase(Locale.ROOT);
            int score = tokenOverlapScore(actLower, gwLabel);
            if (containsAny(gwLabel, "approv", "review", "check", "evaluat", "validat", "policy", "sufficient", "eligibl") &&
                containsAny(actLower, "review", "evaluat", "check", "assess", "approv", "inspect", "audit", "verif", "decide", "validat")) {
                score += 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = act;
            }
        }
        if (best != null) return best;
        // Fallback — first non-branch-target activity, then indexed
        for (GraphNode act : activityNodes) {
            if (!branchTargetIds.contains(act.getId())) return act;
        }
        return activityNodes.isEmpty() ? null : activityNodes.get(Math.min(gatewayIndex, activityNodes.size() - 1));
    }

    // Helper: Find common tokens to match gateways to rules.
    // Discards generic process nouns (see TOKEN_STOPWORDS) that would otherwise cause
    // every rule to spuriously match every gateway.
    private boolean hasHighTokenOverlap(String text1, String text2) {
        return tokenOverlapScore(text1, text2) > 0;
    }

    private int tokenOverlapScore(String text1, String text2) {
        if (text1 == null || text2 == null) return 0;
        List<String> tokens1 = distinctiveTokens(text1);
        List<String> tokens2 = distinctiveTokens(text2);
        int score = 0;
        for (String t1 : tokens1) {
            for (String t2 : tokens2) {
                if (t1.equals(t2)) {
                    score += Math.max(4, t1.length());
                    continue;
                }
                int prefixLen = Math.min(Math.min(t1.length(), t2.length()), 6);
                if (prefixLen >= 5 && t1.substring(0, prefixLen).equals(t2.substring(0, prefixLen))) {
                    score += prefixLen;
                }
            }
        }
        return score;
    }

    private List<String> distinctiveTokens(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(t -> t.length() >= 4)
                .filter(t -> !TOKEN_STOPWORDS.contains(t))
                .toList();
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

    // Activities that logically end a branch (rejection notice, approval notice, etc.).
    // Prevents accidental linkage into the next unrelated activity in the flat list.
    private boolean isBranchTerminalActivity(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.startsWith("notify") || lower.startsWith("inform") || lower.startsWith("communicate")
                || lower.startsWith("send") || lower.contains("notification");
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
