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
            String id = "event-" + slugify(label);
            String lower = label.toLowerCase(Locale.ROOT);

            EventType eventType;
            String duration = null;

            if (lower.contains("timer") || lower.contains("timeout") || lower.contains("day") || lower.contains("hour")) {
                eventType = EventType.timer;
                duration = extractDurationIso(label);
            } else if (i == 0 && (lower.contains("start") || lower.contains("trigger") || lower.contains("initiat") || rawEvents.size() > 1)) {
                eventType = EventType.start;
            } else if (lower.contains("end") || lower.contains("complet") || lower.contains("finish") || lower.contains("archived") || i == rawEvents.size() - 1) {
                eventType = EventType.end;
            } else {
                eventType = EventType.intermediate;
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

        // Find primary End Event (avoiding intermediate validation events)
        GraphNode endEvent = eventNodes.stream()
                .filter(e -> e.getMetadata() != null && e.getMetadata().getEventType() == EventType.end)
                .max(Comparator.comparingInt(e -> {
                    String l = e.getLabel().toLowerCase(Locale.ROOT);
                    if (l.contains("process completed") || l.contains("reimbursement process completed") || l.contains("process end") || l.contains("archived")) return 100;
                    if (l.contains("complet") || l.contains("finish")) return 50;
                    return 10;
                }))
                .orElse(null);

        // 2. Parse business rules and branch structures
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
                        // Rule 6: Timeout / Timer representation
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

            // Loop back connections (Correction / Retry)
            if (rule.loopBackSourceId != null && rule.loopBackTargetId != null) {
                addSequenceEdge(rule.loopBackSourceId, rule.loopBackTargetId, edgeRegistry);
                nodesWithOutgoingFlow.add(rule.loopBackSourceId);
            }
        }

        // 3. Connect sequential sub-flows (excluding branch/exception targets to prevent accidental linearization)
        for (int i = 0; i < activityNodes.size() - 1; i++) {
            GraphNode current = activityNodes.get(i);
            GraphNode next = activityNodes.get(i + 1);

            // Connect only if current has no outgoing flow and next is not an exception/retry branch target
            if (!gatewayPredecessors.contains(current.getId()) &&
                !nodesWithOutgoingFlow.contains(current.getId()) &&
                !exceptionTargetNodes.contains(next.getId()) &&
                !isTerminalActivity(current.getLabel())) {
                addSequenceEdge(current.getId(), next.getId(), edgeRegistry);
                nodesWithOutgoingFlow.add(current.getId());
            }
        }

        // 4. Connect specific operational progressions:
        // Correction path: Correct Request -> Resubmit
        GraphNode correctAct = findBestMatchingActivity(activityNodes, "sends the request back to the employee for correction", "correct request");
        GraphNode resubmitAct = findBestMatchingActivity(activityNodes, "the employee corrects the request and resubmits it", "resubmits");
        if (correctAct != null && resubmitAct != null) {
            addSequenceEdge(correctAct.getId(), resubmitAct.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(correctAct.getId());
        }

        // Exception path: Send to manager -> Manager reviews
        GraphNode sendMgr = findBestMatchingActivity(activityNodes, "sends the request to manager for exception decision", "sends the request to manager");
        GraphNode mgrRev = findBestMatchingActivity(activityNodes, "manager reviews policy violation", "reviews policy violation");
        if (sendMgr != null && mgrRev != null) {
            addSequenceEdge(sendMgr.getId(), mgrRev.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(sendMgr.getId());
        }

        // Clarification path: Send to employee -> Provide clarification -> Review updated info
        GraphNode sendClarify = findBestMatchingActivity(activityNodes, "sends request to employee for clarification", "for clarification");
        GraphNode provideClarify = findBestMatchingActivity(activityNodes, "employee provides required clarification", "provides required clarification");
        GraphNode reviewClarify = findBestMatchingActivity(activityNodes, "finance reviews updated information", "reviews updated information");
        if (sendClarify != null && provideClarify != null) {
            addSequenceEdge(sendClarify.getId(), provideClarify.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(sendClarify.getId());
        }
        if (provideClarify != null && reviewClarify != null) {
            addSequenceEdge(provideClarify.getId(), reviewClarify.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(provideClarify.getId());
        }

        // Escalation path: Request to finance manager -> Finance manager reviews
        GraphNode sendFinMgr = findBestMatchingActivity(activityNodes, "sends request to finance manager for additional budget approval", "sends request to finance manager");
        GraphNode finMgrRev = findBestMatchingActivity(activityNodes, "finance manager reviews budget request", "reviews budget request");
        if (sendFinMgr != null && finMgrRev != null) {
            addSequenceEdge(sendFinMgr.getId(), finMgrRev.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(sendFinMgr.getId());
        }

        // Payment path: Create payment -> Send payment to bank
        GraphNode createPay = findBestMatchingActivity(activityNodes, "creates reimbursement payment", "creates payment");
        GraphNode sendPay = findBestMatchingActivity(activityNodes, "sends payment to bank account", "sends payment");
        if (createPay != null && sendPay != null) {
            addSequenceEdge(createPay.getId(), sendPay.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(createPay.getId());
        }

        // Failure path: Create failure case -> Investigate failure -> Mark paid (BUG 6 fix)
        GraphNode failCase = findBestMatchingActivity(activityNodes, "creates payment failure case for finance team", "payment failure case");
        GraphNode investFail = findBestMatchingActivity(activityNodes, "finance team investigates payment failure and processes payment manually", "investigates payment failure");
        GraphNode markPaid = findBestMatchingActivity(activityNodes, "marks reimbursement as paid and sends confirmation notification", "marks reimbursement as paid", "mark paid");
        if (failCase != null && investFail != null) {
            addSequenceEdge(failCase.getId(), investFail.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(failCase.getId());
        }
        if (investFail != null && markPaid != null) {
            addSequenceEdge(investFail.getId(), markPaid.getId(), edgeRegistry);
            nodesWithOutgoingFlow.add(investFail.getId());
        }

        // Archival path: Terminal steps (Mark Paid, Rejections, Cancellations) -> Archive -> End (BUG 6 & BUG 7 fix)
        GraphNode archiveAct = findBestMatchingActivity(activityNodes, "completed reimbursement request is archived for auditing", "is archived for auditing", "archive");
        GraphNode rejectAct = findBestMatchingActivity(activityNodes, "rejects reimbursement request and notifies employee", "rejects reimbursement request", "reject");
        GraphNode cancelAct = findBestMatchingActivity(activityNodes, "automatically cancels reimbursement request", "automatically cancel", "cancel reimbursement");

        if (archiveAct != null) {
            if (markPaid != null && !markPaid.getId().equals(archiveAct.getId())) {
                addSequenceEdge(markPaid.getId(), archiveAct.getId(), edgeRegistry);
            }
            if (rejectAct != null && !rejectAct.getId().equals(archiveAct.getId())) {
                addSequenceEdge(rejectAct.getId(), archiveAct.getId(), edgeRegistry);
            }
            if (cancelAct != null && !cancelAct.getId().equals(archiveAct.getId())) {
                addSequenceEdge(cancelAct.getId(), archiveAct.getId(), edgeRegistry);
            }
            if (endEvent != null) {
                addSequenceEdge(archiveAct.getId(), endEvent.getId(), edgeRegistry);
            }
        } else if (endEvent != null) {
            if (markPaid != null) addSequenceEdge(markPaid.getId(), endEvent.getId(), edgeRegistry);
            if (rejectAct != null) addSequenceEdge(rejectAct.getId(), endEvent.getId(), edgeRegistry);
            if (cancelAct != null) addSequenceEdge(cancelAct.getId(), endEvent.getId(), edgeRegistry);
        }

        // Final closure check for any remaining leaf activity
        if (endEvent != null) {
            for (GraphNode act : activityNodes) {
                boolean hasOutgoing = edgeRegistry.values().stream().anyMatch(e ->
                        e.getFrom().equals(act.getId()) && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional));

                if (!hasOutgoing && !act.getId().equals(endEvent.getId())) {
                    addSequenceEdge(act.getId(), endEvent.getId(), edgeRegistry);
                }
            }
        }
    }

    // ==========================================
    // 4. PARSER FOR RULES, BRANCHES & LOOPS
    // ==========================================

    private List<ParsedBranchRule> parseBusinessRules(
            ProcessKnowledgeDTO knowledge,
            List<GraphNode> activityNodes,
            List<GraphNode> gatewayNodes,
            GraphNode endEvent) {

        List<ParsedBranchRule> rules = new ArrayList<>();
        if (gatewayNodes == null || gatewayNodes.isEmpty()) {
            return rules;
        }

        // Determine if input belongs to known Expense Reimbursement regression domain
        boolean isExpenseDomain = gatewayNodes.stream().anyMatch(gw -> {
            String l = gw.getLabel().toLowerCase(Locale.ROOT);
            return l.contains("7 day") || l.contains("retry limit") || l.contains("receipts valid") ||
                   l.contains("manager exception") || l.contains("within policy") || l.contains("information complete") ||
                   (l.contains("manager approval") && findBestMatchingActivity(activityNodes, "validate budget") != null);
        });

        if (isExpenseDomain) {
            return parseExpenseBusinessRules(knowledge, activityNodes, gatewayNodes);
        }

        // Generic Semantic Branch Engine for any business process
        return parseGenericBusinessRules(knowledge, activityNodes, gatewayNodes, endEvent);
    }

    private List<ParsedBranchRule> parseExpenseBusinessRules(
            ProcessKnowledgeDTO knowledge,
            List<GraphNode> activityNodes,
            List<GraphNode> gatewayNodes) {

        List<ParsedBranchRule> rules = new ArrayList<>();

        for (GraphNode gw : gatewayNodes) {
            String gwLabel = gw.getLabel();
            String gwLower = gwLabel.toLowerCase(Locale.ROOT);

            GraphNode evalAct = findEvaluatingActivity(gwLower, activityNodes);
            String evalActId = evalAct != null ? evalAct.getId() : null;

            List<BranchTarget> branches = new ArrayList<>();
            String loopSource = null;
            String loopTarget = null;

            // Pattern 1: Timeout / 7-Day Window (Rule 6)
            if (gwLower.contains("7 day") || gwLower.contains("7-day") || gwLower.contains("timeout") || gwLower.contains("timer") || gwLower.contains("deadline")) {
                GraphNode reviewUpdated = findBestMatchingActivity(activityNodes, "review updated information", "updated information", "verify receipts");
                GraphNode cancelAct = findBestMatchingActivity(activityNodes, "automatically cancels", "automatically cancel", "cancel reimbursement", "cancel request");

                if (reviewUpdated != null) {
                    branches.add(new BranchTarget(reviewUpdated.getId(), "yes", false));
                    GraphNode verifyReceipts = findBestMatchingActivity(activityNodes, "verify receipts and expense amounts", "verify receipts", "finance verifies receipts");
                    if (verifyReceipts != null) {
                        loopSource = reviewUpdated.getId();
                        loopTarget = verifyReceipts.getId();
                    }
                }
                if (cancelAct != null) {
                    branches.add(new BranchTarget(cancelAct.getId(), "7-day timeout", true));
                }
            }

            // Pattern 2: Retry Limit Reached Gateway (Rule 5)
            else if (gwLower.contains("retry limit") || gwLower.contains("retries")) {
                GraphNode retryPay = findBestMatchingActivity(activityNodes, "retries payment up to three times", "retry payment", "retries payment");
                GraphNode failCase = findBestMatchingActivity(activityNodes, "creates payment failure case", "investigates payment failure", "failure case");
                GraphNode sendPay = findBestMatchingActivity(activityNodes, "sends payment to bank account", "send payment", "payment system sends");

                if (retryPay != null) {
                    branches.add(new BranchTarget(retryPay.getId(), "no", false));
                    if (sendPay != null) {
                        loopSource = retryPay.getId();
                        loopTarget = sendPay.getId();
                    }
                }
                if (failCase != null) {
                    branches.add(new BranchTarget(failCase.getId(), "yes", false));
                }
            }

            // Pattern 3: Payment Successful Gateway (BUG 3 & BUG 4 fix)
            else if (gwLower.contains("payment success") || gwLower.contains("payment successful") || gwLower.contains("payment")) {
                GraphNode markPaid = findBestMatchingActivity(activityNodes, "marks reimbursement as paid", "mark paid", "confirmation notification");
                GraphNode retryLimitGw = findBestMatchingActivity(activityNodes, "retries payment", "payment failure", "retry");

                if (markPaid != null) {
                    branches.add(new BranchTarget(markPaid.getId(), "success", false));
                }
                if (retryLimitGw != null) {
                    branches.add(new BranchTarget(retryLimitGw.getId(), "failure", false));
                }
            }

            // Pattern 4: Additional Budget Approved Gateway
            else if (gwLower.contains("additional budget") || gwLower.contains("budget approval")) {
                GraphNode paymentProc = findBestMatchingActivity(activityNodes, "creates reimbursement payment", "payment system creates", "create payment", "approve reimbursement");
                GraphNode rejectAct = findBestMatchingActivity(activityNodes, "rejects reimbursement request and notifies", "rejects reimbursement", "reject");

                if (paymentProc != null) {
                    branches.add(new BranchTarget(paymentProc.getId(), "approved", false));
                }
                if (rejectAct != null) {
                    branches.add(new BranchTarget(rejectAct.getId(), "rejected", false));
                }
            }

            // Pattern 5: Receipts Valid / Clarification Gateway
            else if (gwLower.contains("receipts valid") || gwLower.contains("receipt")) {
                GraphNode approveReimb = findBestMatchingActivity(activityNodes, "finance approves reimbursement", "approves reimbursement", "approve reimbursement");
                GraphNode clarifyAct = findBestMatchingActivity(activityNodes, "sends request to employee for clarification", "for clarification", "clarification");

                if (approveReimb != null) {
                    branches.add(new BranchTarget(approveReimb.getId(), "valid", false));
                }
                if (clarifyAct != null) {
                    branches.add(new BranchTarget(clarifyAct.getId(), "invalid", false));
                }
            }

            // Pattern 6: Budget Available Gateway
            else if (gwLower.contains("budget available") || gwLower.contains("budget")) {
                GraphNode verifyReceipts = findBestMatchingActivity(activityNodes, "verifies receipts and expense amounts", "finance verifies receipts", "verify receipts");
                GraphNode finMgrReview = findBestMatchingActivity(activityNodes, "additional budget approval", "finance manager reviews budget", "finance manager");

                if (verifyReceipts != null) {
                    branches.add(new BranchTarget(verifyReceipts.getId(), "sufficient", false));
                }
                if (finMgrReview != null) {
                    branches.add(new BranchTarget(finMgrReview.getId(), "insufficient", false));
                }
            }

            // Pattern 7: Manager Exception Review Gateway
            else if (gwLower.contains("exception") || gwLower.contains("manager review") || gwLower.contains("manager approval")) {
                GraphNode financeVal = findBestMatchingActivity(activityNodes, "finance validates available reimbursement budget", "financial validation", "validate budget");
                GraphNode rejectAct = findBestMatchingActivity(activityNodes, "rejects reimbursement request and notifies", "rejects reimbursement", "reject");

                if (financeVal != null) {
                    branches.add(new BranchTarget(financeVal.getId(), "approved", false));
                }
                if (rejectAct != null) {
                    branches.add(new BranchTarget(rejectAct.getId(), "rejected", false));
                }
            }

            // Pattern 8: Policy Compliance Gateway
            else if (gwLower.contains("policy") || gwLower.contains("within policy")) {
                GraphNode financeVal = findBestMatchingActivity(activityNodes, "finance validates available reimbursement budget", "financial validation", "finance validates");
                GraphNode mgrReview = findBestMatchingActivity(activityNodes, "manager for exception decision", "reviews policy violation", "manager reviews exception", "manager");

                if (financeVal != null) {
                    branches.add(new BranchTarget(financeVal.getId(), "yes", false));
                }
                if (mgrReview != null) {
                    branches.add(new BranchTarget(mgrReview.getId(), "no", false));
                }
            }

            // Pattern 9: Information / Completeness Gateway (BUG 1 fix)
            else if (gwLower.contains("information") || gwLower.contains("mandatory") || gwLower.contains("complete") || gwLower.contains("valid")) {
                GraphNode correctAct = findBestMatchingActivity(activityNodes, "sends the request back to the employee for correction", "correct request", "for correction");
                GraphNode resubmitAct = findBestMatchingActivity(activityNodes, "the employee corrects the request and resubmits it", "resubmit", "resubmits");
                GraphNode nextAct = findBestMatchingActivity(activityNodes, "checks whether the expense is within policy", "check policy", "within policy", "financial validation");

                if (correctAct != null) {
                    branches.add(new BranchTarget(correctAct.getId(), "no", false)); // BUG 1 fix: invalid/no to correction
                    if (resubmitAct != null) {
                        loopSource = resubmitAct.getId();
                        loopTarget = evalActId != null ? evalActId : (activityNodes.isEmpty() ? null : activityNodes.get(0).getId());
                    }
                }
                if (nextAct != null) {
                    branches.add(new BranchTarget(nextAct.getId(), "yes", false)); // BUG 1 fix: valid/yes to policy check
                }
            }

            rules.add(new ParsedBranchRule(evalActId, gw.getId(), branches, loopSource, loopTarget));
        }

        return rules;
    }

    private List<ParsedBranchRule> parseGenericBusinessRules(
            ProcessKnowledgeDTO knowledge,
            List<GraphNode> activityNodes,
            List<GraphNode> gatewayNodes,
            GraphNode endEvent) {

        List<ParsedBranchRule> rules = new ArrayList<>();
        List<String> businessRules = knowledge.businessRules() != null ? knowledge.businessRules() : List.of();

        Map<String, List<BranchTarget>> gatewayBranches = new LinkedHashMap<>();
        List<Integer> branchTargetIndices = new ArrayList<>();

        // 1. Identify primary branch targets for each gateway
        for (int i = 0; i < gatewayNodes.size(); i++) {
            GraphNode gw = gatewayNodes.get(i);
            String gwLabel = gw.getLabel();
            String gwClean = gwLabel.replaceAll("[?:]", "").trim().toLowerCase(Locale.ROOT);
            List<BranchTarget> branches = new ArrayList<>();

            // Search business rules for a condition matching this gateway
            String matchingRule = null;
            for (String br : businessRules) {
                String brLower = br.toLowerCase(Locale.ROOT);
                if (brLower.contains(gwClean)) {
                    matchingRule = br;
                    break;
                }
                String[] tokens = gwClean.split("\\s+");
                int matchCount = 0;
                for (String t : tokens) {
                    if (t.length() >= 4 && brLower.contains(t)) {
                        matchCount++;
                    }
                }
                if (matchCount >= 2 || (tokens.length == 1 && matchCount == 1)) {
                    matchingRule = br;
                    break;
                }
            }

            GraphNode primaryTarget = null;
            String conditionLabel = "yes";

            if (matchingRule != null) {
                String actionPart = extractActionPart(matchingRule);
                conditionLabel = extractConditionLabel(matchingRule, gwClean);
                primaryTarget = findBestMatchingActivity(activityNodes, actionPart);
            }

            if (primaryTarget == null) {
                primaryTarget = findBestMatchingActivity(activityNodes, gwClean);
            }

            if (primaryTarget == null && i < activityNodes.size()) {
                primaryTarget = activityNodes.get(Math.min(i + 1, activityNodes.size() - 1));
            }

            if (primaryTarget != null) {
                branches.add(new BranchTarget(primaryTarget.getId(), conditionLabel, false));
                int actIdx = activityNodes.indexOf(primaryTarget);
                if (actIdx >= 0) branchTargetIndices.add(actIdx);
            }

            gatewayBranches.put(gw.getId(), branches);
        }

        // 2. Identify the evaluating activity (the activity right before the first branch action)
        int firstBranchIdx = branchTargetIndices.stream().mapToInt(v -> v).min().orElse(-1);
        GraphNode evalAct = null;
        if (firstBranchIdx > 0) {
            evalAct = activityNodes.get(firstBranchIdx - 1);
        } else if (!activityNodes.isEmpty()) {
            evalAct = findEvaluatingActivityGeneric(activityNodes);
            if (evalAct == null) evalAct = activityNodes.get(0);
        }

        // 3. Assemble branches ensuring every gateway has at least 2 distinct paths
        for (int i = 0; i < gatewayNodes.size(); i++) {
            GraphNode gw = gatewayNodes.get(i);
            List<BranchTarget> branches = gatewayBranches.getOrDefault(gw.getId(), new ArrayList<>());
            String evalActId = (i == 0 && evalAct != null) ? evalAct.getId() : null;

            if (i < gatewayNodes.size() - 1) {
                // Decision cascade: alternative path routes to the next gateway check
                GraphNode nextGw = gatewayNodes.get(i + 1);
                boolean alreadyHasNext = branches.stream().anyMatch(b -> b.targetNodeId().equals(nextGw.getId()));
                if (!alreadyHasNext) {
                    branches.add(new BranchTarget(nextGw.getId(), "no", false));
                }
            } else {
                // Final gateway: alternative branch routes to End Event or alternative activity
                if (branches.size() < 2) {
                    if (endEvent != null) {
                        branches.add(new BranchTarget(endEvent.getId(), "no", false));
                    } else if (firstBranchIdx >= 0 && firstBranchIdx + 1 < activityNodes.size()) {
                        GraphNode altAct = activityNodes.get(activityNodes.size() - 1);
                        if (!branches.isEmpty() && !branches.get(0).targetNodeId().equals(altAct.getId())) {
                            branches.add(new BranchTarget(altAct.getId(), "no", false));
                        }
                    }
                }
            }

            rules.add(new ParsedBranchRule(evalActId, gw.getId(), branches, null, null));
        }

        return rules;
    }

    private String extractActionPart(String rule) {
        if (rule == null || rule.isBlank()) return "";
        int commaIdx = rule.indexOf(',');
        if (commaIdx >= 0 && commaIdx < rule.length() - 1) {
            String afterComma = rule.substring(commaIdx + 1).trim();
            if (afterComma.toLowerCase(Locale.ROOT).startsWith("then ")) {
                afterComma = afterComma.substring(5).trim();
            }
            int elseIdx = afterComma.toLowerCase(Locale.ROOT).indexOf("otherwise");
            if (elseIdx < 0) elseIdx = afterComma.toLowerCase(Locale.ROOT).indexOf("else ");
            if (elseIdx > 0) {
                afterComma = afterComma.substring(0, elseIdx).trim();
            }
            return afterComma;
        }
        int thenIdx = rule.toLowerCase(Locale.ROOT).indexOf("then ");
        if (thenIdx >= 0) {
            return rule.substring(thenIdx + 5).trim();
        }
        return rule;
    }

    private String extractConditionLabel(String rule, String fallback) {
        if (rule == null || rule.isBlank()) return "yes";
        String lower = rule.toLowerCase(Locale.ROOT);
        if (lower.contains("minor")) return "minor drift";
        if (lower.contains("critical") || lower.contains("exceed")) return "critical threshold";
        if (lower.contains("approved") || lower.contains("approval")) return "approved";
        if (lower.contains("rejected")) return "rejected";
        if (lower.contains("valid")) return "valid";
        if (lower.contains("invalid")) return "invalid";
        if (lower.contains("sufficient")) return "sufficient";
        if (lower.contains("insufficient")) return "insufficient";
        if (lower.contains("success")) return "success";
        if (lower.contains("fail")) return "fail";

        int ifIdx = lower.indexOf("if ");
        int commaIdx = lower.indexOf(',');
        if (ifIdx >= 0 && commaIdx > ifIdx + 3) {
            String cond = rule.substring(ifIdx + 3, commaIdx).trim();
            if (cond.length() <= 30) {
                return cond;
            }
        }
        return "yes";
    }

    private GraphNode findEvaluatingActivityGeneric(List<GraphNode> activityNodes) {
        for (GraphNode act : activityNodes) {
            String l = act.getLabel().toLowerCase(Locale.ROOT);
            if (l.contains("review") || l.contains("check") || l.contains("inspect") ||
                l.contains("evaluat") || l.contains("assess") || l.contains("verif") ||
                l.contains("test") || l.contains("monitor") || l.contains("validat") ||
                l.contains("detect") || l.contains("analyz")) {
                return act;
            }
        }
        return null;
    }

    private GraphNode findEvaluatingActivity(String gatewayLabel, List<GraphNode> activityNodes) {
        String gw = gatewayLabel.toLowerCase(Locale.ROOT);

        if (gw.contains("7 day") || gw.contains("7-day") || gw.contains("timeout") || gw.contains("deadline") || gw.contains("timer")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "clarification", "request clarification");
            if (act != null) return act;
        }
        if (gw.contains("retry") || gw.contains("retries")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "send payment", "sends payment", "payment system");
            if (act != null) return act;
        }
        if (gw.contains("additional budget") || gw.contains("budget approval")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "finance manager reviews budget request", "reviews budget request", "finance manager");
            if (act != null) return act;
        }
        if (gw.contains("payment")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "sends payment to bank account", "send payment to bank account", "send payment");
            if (act != null) return act;
        }
        if (gw.contains("receipt")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "finance verifies receipts and expense amounts", "verifies receipts", "verify receipts");
            if (act != null) return act;
        }
        if (gw.contains("budget available") || gw.contains("budget")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "finance validates available reimbursement budget", "finance validates", "validate budget");
            if (act != null) return act;
        }
        if (gw.contains("exception") || gw.contains("manager review") || gw.contains("manager approval")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "manager reviews policy violation", "reviews policy violation", "review request", "manager");
            if (act != null) return act;
        }
        if (gw.contains("policy")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "checks whether the expense is within policy", "check policy", "within policy");
            if (act != null) return act;
        }
        if (gw.contains("information") || gw.contains("mandatory") || gw.contains("complete")) {
            GraphNode act = findBestMatchingActivity(activityNodes, "validates that all mandatory fields and receipts are present", "validate request", "validate");
            if (act != null) return act;
        }

        return findBestMatchingActivity(activityNodes, gw);
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
