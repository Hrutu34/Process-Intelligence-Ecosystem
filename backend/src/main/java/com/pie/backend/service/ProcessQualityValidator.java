package com.pie.backend.service;

import com.pie.shared.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProcessQualityValidator {

    private static final Logger log = LoggerFactory.getLogger(ProcessQualityValidator.class);

    private static final Set<String> APPROVAL_PREFIXES = Set.of(
            "approve", "approv", "review", "authorize", "authoris", "sign off", "sign-off", "audit", "evaluate"
    );

    private static final Set<String> INITIATION_PREFIXES = Set.of(
            "submit", "create", "initiat", "request", "draft", "start", "enter", "order", "receive", "trigger", "file"
    );

    private static final Set<String> AFFIRMATIVE_CONDITIONS = Set.of(
            "approved", "approve", "yes", "valid", "passed", "pass", "success", "confirmed", "true"
    );

    private static final Set<String> NEGATIVE_CONDITIONS = Set.of(
            "rejected", "reject", "no", "invalid", "failed", "fail", "denied", "deny", "escalate", "false"
    );

    public ProcessQualityReportDTO validateQuality(ProcessGraphDTO graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return new ProcessQualityReportDTO(
                    false,
                    0,
                    List.of(new ValidationIssueDTO("EMPTY_GRAPH", "HIGH", null, "Process graph is empty", "Extract process knowledge or provide activities before validation.")),
                    List.of("Provide process description or documents to generate a valid workflow.")
            );
        }

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        List<GraphNode> nodes = graph.getNodes();
        List<GraphEdge> edges = graph.getEdges() != null ? graph.getEdges() : List.of();

        // 1. Validate Start Events (TASK-012)
        validateStartEvents(nodes, edges, issues, recommendations);

        // 2. Validate End Events (TASK-013)
        validateEndEvents(nodes, edges, issues, recommendations);

        // 3. Validate Decision Gateways (TASK-014)
        validateGateways(nodes, edges, issues, recommendations);

        // 4. Detect semantically redundant activities (TASK-016)
        validateDuplicates(nodes, issues, recommendations);

        // 5. Validate graph evidence beyond synthetic start/end nodes
        validateFlowCoverage(nodes, edges, issues, recommendations);
        validateActivityOwnership(nodes, issues, recommendations);
        validateSharedOwnership(nodes, edges, issues, recommendations);

        // 6. Answer-key defect rules (D02, D06, D07, D10, D11, D12, D13) + extras
        validateTaskNaming(nodes, issues, recommendations);
        validateParallelGateways(nodes, edges, issues, recommendations);
        validateGatewayLabels(nodes, issues, recommendations);
        validateConsecutiveDuplicates(nodes, edges, issues, recommendations);
        validateSwimlanesAndRoles(nodes, issues, recommendations);
        validateLinearComplexity(nodes, edges, issues, recommendations);
        validateDanglingFlows(nodes, edges, issues, recommendations);

        int qualityScore = calculateQualityScore(nodes, edges, issues);
        int highCount = (int) issues.stream().filter(i -> "HIGH".equalsIgnoreCase(i.severity())).count();
        boolean isValid = highCount == 0;

        log.info("Quality validation completed for [{}]: score={}, issues={}, recommendations={}",
                graph.getGraphId(), qualityScore, issues.size(), recommendations.size());

        return new ProcessQualityReportDTO(isValid, qualityScore, issues, recommendations);
    }

        private int calculateQualityScore(List<GraphNode> nodes, List<GraphEdge> edges,
                          List<ValidationIssueDTO> issues) {
        // Hard failures affect the score directly; extraction uncertainty is weighted by coverage.
        int structuralPenalty = (int) issues.stream()
            .filter(issue -> "HIGH".equalsIgnoreCase(issue.severity()))
            .count() * 25;
        int semanticPenalty = (int) issues.stream()
            .filter(issue -> "MEDIUM".equalsIgnoreCase(issue.severity()))
            .count() * 10;

        List<GraphNode> processNodes = nodes.stream()
            .filter(node -> node.getType() == NodeType.Activity
                || node.getType() == NodeType.Gateway
                || node.getType() == NodeType.Event)
            .toList();
        Set<String> flowNodeIds = edges.stream()
            .filter(this::isProcessFlow)
            .flatMap(edge -> java.util.stream.Stream.of(edge.getFrom(), edge.getTo()))
            .collect(Collectors.toSet());
        int flowPenalty = scaledCoveragePenalty(processNodes.size(),
            processNodes.stream().filter(node -> flowNodeIds.contains(node.getId())).count(), 15);

        List<GraphNode> activities = nodes.stream()
            .filter(node -> node.getType() == NodeType.Activity)
            .toList();
        boolean hasOwners = nodes.stream().anyMatch(node -> node.getType() == NodeType.Role || node.getType() == NodeType.System);
        long ownedActivities = activities.stream().filter(this::hasOwner).count();
        int ownershipPenalty = hasOwners ? scaledCoveragePenalty(activities.size(), ownedActivities, 10) : 0;

        return Math.max(0, Math.min(100,
            100 - structuralPenalty - semanticPenalty - flowPenalty - ownershipPenalty));
        }

        private int scaledCoveragePenalty(int total, long covered, int maximumPenalty) {
        if (total == 0 || covered >= total) return 0;
        return (int) Math.ceil((total - covered) * maximumPenalty / (double) total);
        }

        private boolean hasOwner(GraphNode activity) {
        NodeMetadata metadata = activity.getMetadata();
        return metadata != null
            && ((metadata.getRoleRef() != null && !metadata.getRoleRef().isBlank())
            || (metadata.getSystemRef() != null && !metadata.getSystemRef().isBlank()));
        }

    private void validateFlowCoverage(List<GraphNode> nodes, List<GraphEdge> edges,
                                      List<ValidationIssueDTO> issues, List<String> recommendations) {
        Set<String> flowNodeIds = edges.stream()
                .filter(this::isProcessFlow)
                .flatMap(edge -> java.util.stream.Stream.of(edge.getFrom(), edge.getTo()))
                .collect(Collectors.toSet());

        List<GraphNode> disconnected = nodes.stream()
            .filter(node -> node.getType() == NodeType.Activity || node.getType() == NodeType.Gateway || node.getType() == NodeType.Event)
            .filter(node -> !flowNodeIds.contains(node.getId()))
            .toList();

        if (!disconnected.isEmpty()) {
            String labels = disconnected.stream().map(GraphNode::getLabel).limit(5).collect(Collectors.joining("; "));
            String suffix = disconnected.size() > 5 ? " and " + (disconnected.size() - 5) + " more" : "";
            issues.add(new ValidationIssueDTO(
                "FLOW_COVERAGE_RULE",
                "LOW",
                null,
                disconnected.size() + " process element(s) lack explicit flow connections: " + labels + suffix,
                "Review disconnected elements; this is an evidence warning and may reflect incomplete model extraction."
            ));
            recommendations.add("Review " + disconnected.size() + " process element(s) without explicit sequence or conditional flow.");
        }
    }

    private void validateActivityOwnership(List<GraphNode> nodes,
                                           List<ValidationIssueDTO> issues,
                                           List<String> recommendations) {
        boolean hasOwners = nodes.stream().anyMatch(node -> node.getType() == NodeType.Role || node.getType() == NodeType.System);
        if (!hasOwners) return;

        List<GraphNode> unowned = new ArrayList<>();
        for (GraphNode activity : nodes.stream().filter(node -> node.getType() == NodeType.Activity).toList()) {
            if (!hasOwner(activity)) {
            unowned.add(activity);
            }
        }

        if (!unowned.isEmpty()) {
            String labels = unowned.stream().map(GraphNode::getLabel).limit(5).collect(Collectors.joining("; "));
            String suffix = unowned.size() > 5 ? " and " + (unowned.size() - 5) + " more" : "";
            issues.add(new ValidationIssueDTO(
                "ACTIVITY_OWNER_RULE",
                "LOW",
                null,
                unowned.size() + " activity(ies) have no explicit owner or system: " + labels + suffix,
                "Assign roles or systems where known; extracted text may not contain ownership for every activity."
            ));
            recommendations.add("Review ownership for " + unowned.size() + " activity(ies); treat missing ownership as a refinement opportunity.");
        }
    }

    private boolean isProcessFlow(GraphEdge edge) {
        return edge.getEdgeType() == EdgeType.sequence || edge.getEdgeType() == EdgeType.conditional;
    }

        private void validateSharedOwnership(List<GraphNode> nodes, List<GraphEdge> edges,
                         List<ValidationIssueDTO> issues, List<String> recommendations) {
        Set<String> roleIds = nodes.stream()
            .filter(node -> node.getType() == NodeType.Role)
            .map(GraphNode::getId)
            .collect(Collectors.toSet());
        List<String> sharedActivities = nodes.stream()
            .filter(node -> node.getType() == NodeType.Activity)
            .filter(activity -> edges.stream()
                .filter(edge -> edge.getEdgeType() == EdgeType.association
                    && edge.getTo().equals(activity.getId())
                    && roleIds.contains(edge.getFrom()))
                .map(GraphEdge::getFrom)
                .distinct()
                .count() > 1)
            .map(GraphNode::getLabel)
            .toList();

        if (!sharedActivities.isEmpty()) {
            issues.add(new ValidationIssueDTO(
                "SHARED_OWNERSHIP_RULE",
                "LOW",
                null,
                sharedActivities.size() + " activity(ies) have multiple assigned roles: " + String.join("; ", sharedActivities.stream().limit(5).toList()),
                "Confirm the accountable owner and distinguish supporting roles where responsibility is shared."
            ));
            recommendations.add("Clarify accountable ownership for " + sharedActivities.size() + " shared-responsibility activity(ies).");
        }
        }

    /** Flags activity labels that are near matches, not only exact duplicates. */
    public void validateDuplicates(List<GraphNode> nodes,
                                   List<ValidationIssueDTO> issues,
                                   List<String> recommendations) {
        List<GraphNode> activities = nodes.stream()
                .filter(node -> node.getType() == NodeType.Activity)
                .toList();

        for (int i = 0; i < activities.size(); i++) {
            for (int j = i + 1; j < activities.size(); j++) {
                GraphNode first = activities.get(i);
                GraphNode second = activities.get(j);
                if (!isSemanticallyEquivalent(first.getLabel(), second.getLabel())) {
                    continue;
                }

                issues.add(new ValidationIssueDTO(
                        "DUPLICATE_ACTIVITY_RULE",
                        "MEDIUM",
                        second.getId(),
                        "Redundant activities detected: '" + first.getLabel() + "' and '" + second.getLabel() + "'",
                        "Merge the overlapping activities or clarify how their responsibilities differ."
                ));
                recommendations.add("Review similar steps '" + first.getLabel() + "' and '" + second.getLabel() + "' to remove redundant work.");
            }
        }
    }

    private boolean isSemanticallyEquivalent(String firstLabel, String secondLabel) {
        String first = normalizeActivityLabel(firstLabel);
        String second = normalizeActivityLabel(secondLabel);
        if (first.equals(second)) return true;

        Set<String> firstTokens = new HashSet<>(List.of(first.split(" ")));
        Set<String> secondTokens = new HashSet<>(List.of(second.split(" ")));
        Set<String> intersection = new HashSet<>(firstTokens);
        intersection.retainAll(secondTokens);
        int shorterSize = Math.min(firstTokens.size(), secondTokens.size());
        double overlap = shorterSize == 0 ? 0 : (double) intersection.size() / shorterSize;

        int maxLength = Math.max(first.length(), second.length());
        double similarity = maxLength == 0 ? 1 : 1.0 - (double) levenshteinDistance(first, second) / maxLength;
        return overlap == 1.0 || similarity >= 0.90;
    }

    private String normalizeActivityLabel(String label) {
        return Arrays.stream(label.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9 ]", " ")
                        .trim()
                        .split("\\s+"))
                .filter(token -> !Set.of("the", "a", "an", "to", "of", "and").contains(token))
                .collect(Collectors.joining(" "));
    }

    private int levenshteinDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) previous[j] = j;

        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int substitution = previous[j - 1] + (first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    /**
     * TASK-012: Start Event Validation Rule
     */
    public void validateStartEvents(List<GraphNode> nodes, List<GraphEdge> edges,
                                    List<ValidationIssueDTO> issues, List<String> recommendations) {
        List<GraphNode> startEvents = nodes.stream()
                .filter(n -> n.getType() == NodeType.Event && n.getMetadata() != null && n.getMetadata().getEventType() == EventType.start)
                .toList();

        List<GraphNode> activities = nodes.stream()
                .filter(n -> n.getType() == NodeType.Activity)
                .toList();

        // Rule 12.1: Missing Start Event
        if (startEvents.isEmpty()) {
            issues.add(new ValidationIssueDTO(
                    "START_EVENT_RULE",
                    "HIGH",
                    null,
                    "Missing Start Event",
                    "Add an explicit BPMN Start Event or triggering condition to initiate the process workflow."
            ));
            recommendations.add("Define an explicit start event (e.g. 'Order Received' or 'Application Submitted') to clearly designate workflow initiation.");
        } else {
            // Check start event out-degree
            for (GraphNode start : startEvents) {
                boolean hasOutgoing = edges.stream().anyMatch(e -> e.getFrom().equals(start.getId()) && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional));
                if (!hasOutgoing) {
                    issues.add(new ValidationIssueDTO(
                            "START_EVENT_RULE",
                            "HIGH",
                            start.getId(),
                            "Disconnected Start Event",
                            "Start event '" + start.getLabel() + "' is not connected to any subsequent task or gateway."
                    ));
                    recommendations.add("Connect Start Event '" + start.getLabel() + "' to the first activity in the sequence.");
                }
            }
        }

        // Rule 12.2: Process starts with approval without prior initiation (Semantic Order Check)
        if (!activities.isEmpty()) {
            GraphNode firstActivity = activities.get(0);
            String firstLabelLower = firstActivity.getLabel().toLowerCase(Locale.ROOT);

            boolean startsWithApproval = APPROVAL_PREFIXES.stream().anyMatch(firstLabelLower::contains);
            boolean hasOtherInitiation = activities.stream()
                    .filter(a -> !a.getId().equals(firstActivity.getId()))
                    .anyMatch(a -> {
                        String l = a.getLabel().toLowerCase(Locale.ROOT);
                        return INITIATION_PREFIXES.stream().anyMatch(l::contains);
                    });

            if (startsWithApproval && !hasOtherInitiation) {
                issues.add(new ValidationIssueDTO(
                        "START_EVENT_RULE",
                        "HIGH",
                        firstActivity.getId(),
                        "Invalid Initiation Sequence: Process starts with approval",
                        "Process starts with '" + firstActivity.getLabel() + "' without a preceding request submission or initiation trigger."
                ));
                recommendations.add("Add a preceding submission or intake step (e.g. 'Submit Request') before '" + firstActivity.getLabel() + "'.");
            }
        }
    }

    /**
     * TASK-013: End Event Validation Rule
     */
    public void validateEndEvents(List<GraphNode> nodes, List<GraphEdge> edges,
                                  List<ValidationIssueDTO> issues, List<String> recommendations) {
        List<GraphNode> endEvents = nodes.stream()
                .filter(n -> n.getType() == NodeType.Event && n.getMetadata() != null && n.getMetadata().getEventType() == EventType.end)
                .toList();

        List<GraphNode> activities = nodes.stream()
                .filter(n -> n.getType() == NodeType.Activity)
                .toList();

        // Rule 13.1: Missing End Event
        if (endEvents.isEmpty()) {
            issues.add(new ValidationIssueDTO(
                    "END_EVENT_RULE",
                    "HIGH",
                    null,
                    "Missing End Event",
                    "Every process path must terminate in a valid BPMN End Event or completion condition."
            ));
            recommendations.add("Add an explicit End Event (e.g. 'Process Completed' or 'Order Dispatched') to designate successful closure.");
        } else {
            // Check end event in-degree
            for (GraphNode end : endEvents) {
                boolean hasIncoming = edges.stream().anyMatch(e -> e.getTo().equals(end.getId()) && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional));
                if (!hasIncoming) {
                    issues.add(new ValidationIssueDTO(
                            "END_EVENT_RULE",
                            "HIGH",
                            end.getId(),
                            "Unreachable End Event",
                            "End event '" + end.getLabel() + "' has no incoming sequence flows."
                    ));
                    recommendations.add("Ensure process paths route into End Event '" + end.getLabel() + "'.");
                }
            }
        }

        // Rule 13.2: Abrupt Termination / Dead-End Activities
        for (GraphNode act : activities) {
            List<GraphEdge> outgoing = edges.stream()
                    .filter(e -> e.getFrom().equals(act.getId()) && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional))
                    .toList();

            if (outgoing.isEmpty()) {
                // If there are end events and this activity does not connect to any, it's an abrupt termination
                issues.add(new ValidationIssueDTO(
                        "END_EVENT_RULE",
                        "HIGH",
                        act.getId(),
                        "Process ends abruptly without closure: " + act.getLabel(),
                        "Activity '" + act.getLabel() + "' has no outgoing sequence flows and is not connected to an End Event."
                ));
                recommendations.add("Connect terminal activity '" + act.getLabel() + "' to an End Event or notification step.");
            }
        }
    }

    /**
     * TASK-014: Gateway Validation Engine
     */
    public void validateGateways(List<GraphNode> nodes, List<GraphEdge> edges,
                                 List<ValidationIssueDTO> issues, List<String> recommendations) {
        List<GraphNode> gateways = nodes.stream()
                .filter(n -> n.getType() == NodeType.Gateway)
                .toList();

        Map<String, GraphNode> nodeMap = nodes.stream()
                .collect(Collectors.toMap(GraphNode::getId, n -> n, (a, b) -> a));

        for (GraphNode gw : gateways) {
            List<GraphEdge> outgoing = edges.stream()
                    .filter(e -> e.getFrom().equals(gw.getId()) && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional))
                    .toList();

            // Rule 14.1: Single Branch Decision
            if (outgoing.size() == 1) {
                issues.add(new ValidationIssueDTO(
                        "GATEWAY_RULE_SINGLE_BRANCH",
                        "HIGH",
                        gw.getId(),
                        "Single Branch Decision",
                        "Decision gateway '" + gw.getLabel() + "' has only 1 outgoing path. A gateway must branch into at least 2 distinct paths."
                ));
                recommendations.add("Add at least one additional alternative branch to decision point '" + gw.getLabel() + "'.");
            } else if (outgoing.isEmpty()) {
                issues.add(new ValidationIssueDTO(
                        "GATEWAY_RULE_DEAD_END",
                        "HIGH",
                        gw.getId(),
                        "Dead-End Gateway",
                        "Decision gateway '" + gw.getLabel() + "' has no outgoing branches."
                ));
                recommendations.add("Connect decision gateway '" + gw.getLabel() + "' to subsequent conditional target activities.");
            }

            // Rule 14.2: Missing Gateway Conditions & Ambiguous Choices
            Set<String> seenConditions = new HashSet<>();
            boolean hasAffirmative = false;
            boolean hasNegative = false;

            for (GraphEdge edge : outgoing) {
                String label = edge.getLabel();
                if (label == null || label.isBlank()) {
                    issues.add(new ValidationIssueDTO(
                            "GATEWAY_RULE_MISSING_CONDITION",
                            "MEDIUM",
                            edge.getId(),
                            "Missing Gateway Condition",
                            "Gateway branch from '" + gw.getLabel() + "' to '" + edge.getTo() + "' lacks an explicit condition label."
                    ));
                    recommendations.add("Specify a condition label (e.g. 'Approved', 'Rejected', 'Timeout') on branch " + edge.getId() + ".");
                } else {
                    String normLabel = label.trim().toLowerCase(Locale.ROOT);
                    if (!seenConditions.add(normLabel)) {
                        issues.add(new ValidationIssueDTO(
                                "GATEWAY_RULE_AMBIGUOUS_CHOICE",
                                "MEDIUM",
                                gw.getId(),
                                "Ambiguous Decision Branches",
                                "Multiple outgoing branches from gateway '" + gw.getLabel() + "' share identical condition: '" + label + "'."
                        ));
                        recommendations.add("Differentiate duplicate condition labels on branches exiting '" + gw.getLabel() + "'.");
                    }

                    if (AFFIRMATIVE_CONDITIONS.contains(normLabel)) {
                        hasAffirmative = true;
                    }
                    if (NEGATIVE_CONDITIONS.contains(normLabel)) {
                        hasNegative = true;
                    }
                }

                // Rule 14.3: Dead-End Branch from Gateway
                GraphNode targetNode = nodeMap.get(edge.getTo());
                if (targetNode != null && targetNode.getType() == NodeType.Activity) {
                    boolean targetHasOutgoing = edges.stream().anyMatch(e -> e.getFrom().equals(targetNode.getId()) && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional));
                    if (!targetHasOutgoing) {
                        issues.add(new ValidationIssueDTO(
                                "GATEWAY_RULE_DEAD_END",
                                "HIGH",
                                targetNode.getId(),
                                "Dead-End Branch Detected: " + targetNode.getLabel(),
                                "Branch from '" + gw.getLabel() + "' leads to step '" + targetNode.getLabel() + "' which has no subsequent action or End Event."
                        ));
                        recommendations.add("Ensure branch reaching '" + targetNode.getLabel() + "' terminates in an End Event or loops back to a rework step.");
                    }
                }
            }

            // Rule 14.4: Missing Rejected / Alternative Path
            if (hasAffirmative && !hasNegative && outgoing.size() < 2) {
                issues.add(new ValidationIssueDTO(
                        "GATEWAY_RULE_MISSING_REJECTION",
                        "HIGH",
                        gw.getId(),
                        "Rejected Path Missing",
                        "Decision point '" + gw.getLabel() + "' only handles the positive/approved path ('" + outgoing.get(0).getLabel() + "'). The rejected or exception path is missing."
                ));
                recommendations.add("Add a rejection/exception branch (e.g. 'Rejected' -> 'Notify Requester' or 'Terminate') to '" + gw.getLabel() + "'.");
            }
        }
    }

    private static final Set<String> VAGUE_TASK_LABELS = Set.of(
            "process", "do needful", "handle", "task", "work", "do work",
            "execute", "perform", "action", "step", "miscellaneous", "etc", "activity",
            "manage", "deal", "deal with", "do", "run", "operate",
            "verify", "next step", "todo", "to do", "misc", "other", "stuff", "thing",
            "start task", "end task", "finish", "complete", "close", "final",
            "unnamed activity"
    );

    public void validateTaskNaming(List<GraphNode> nodes,
                                   List<ValidationIssueDTO> issues,
                                   List<String> recommendations) {
        for (GraphNode node : nodes) {
            if (node.getType() != NodeType.Activity) continue;
            String label = node.getLabel() != null ? node.getLabel().trim() : "";
            String norm = label.toLowerCase(Locale.ROOT);

            if (label.isBlank()) {
                issues.add(new ValidationIssueDTO(
                        "TASK_NAME_MISSING",
                        "MEDIUM",
                        node.getId(),
                        "Unnamed task",
                        "Activity has no name. Every task should carry a verb+object label."
                ));
                recommendations.add("Give the unnamed activity a clear verb+object name.");
                continue;
            }

            boolean isVague = VAGUE_TASK_LABELS.contains(norm)
                    || (norm.length() <= 10 && VAGUE_TASK_LABELS.stream()
                            .anyMatch(v -> norm.equals(v) || norm.startsWith(v + " ")));

            if (isVague) {
                issues.add(new ValidationIssueDTO(
                        "TASK_NAME_QUALITY_RULE",
                        "MEDIUM",
                        node.getId(),
                        "Vague task name: '" + label + "'",
                        "Task '" + label + "' is non-actionable. Rename to a verb + business object (e.g. 'Approve invoice for payment')."
                ));
                recommendations.add("Rename task '" + label + "' to a concrete verb+object action.");
            }
        }
    }

    public void validateParallelGateways(List<GraphNode> nodes,
                                         List<GraphEdge> edges,
                                         List<ValidationIssueDTO> issues,
                                         List<String> recommendations) {
        List<GraphNode> parallelSplits = nodes.stream()
                .filter(n -> n.getType() == NodeType.Gateway)
                .filter(n -> {
                    boolean isParallelType = n.getMetadata() != null && n.getMetadata().getGatewayType() == GatewayType.parallel;
                    boolean isParallelLabel = n.getLabel() != null && n.getLabel().toLowerCase(Locale.ROOT).contains("parallel");
                    long outCount = edges.stream()
                            .filter(this::isProcessFlow)
                            .filter(e -> e.getFrom().equals(n.getId()))
                            .count();
                    return (isParallelType || isParallelLabel) && outCount > 1;
                })
                .toList();

        List<GraphNode> parallelJoins = nodes.stream()
                .filter(n -> n.getType() == NodeType.Gateway)
                .filter(n -> {
                    boolean isParallelType = n.getMetadata() != null && n.getMetadata().getGatewayType() == GatewayType.parallel;
                    long inCount = edges.stream()
                            .filter(this::isProcessFlow)
                            .filter(e -> e.getTo().equals(n.getId()))
                            .count();
                    return isParallelType && inCount > 1;
                })
                .toList();

        for (GraphNode split : parallelSplits) {
            if (parallelJoins.isEmpty()) {
                issues.add(new ValidationIssueDTO(
                        "PARALLEL_SPLIT_JOIN_RULE",
                        "HIGH",
                        split.getId(),
                        "Missing parallel join for split: '" + split.getLabel() + "'",
                        "Parallel split '" + split.getLabel() + "' has no matching join. Concurrent branches never synchronize."
                ));
                recommendations.add("Add a matching parallel join gateway that merges branches before the End Event.");
            }
        }
    }

    public void validateGatewayLabels(List<GraphNode> nodes,
                                      List<ValidationIssueDTO> issues,
                                      List<String> recommendations) {
        for (GraphNode node : nodes) {
            if (node.getType() != NodeType.Gateway) continue;
            String label = node.getLabel() != null ? node.getLabel().trim() : "";
            String norm = label.toLowerCase(Locale.ROOT);

            boolean isUnlabeled = label.isBlank()
                    || norm.equals("unlabeled")
                    || norm.startsWith("exclusivegateway")
                    || norm.equals("gateway")
                    || norm.equals("decision");

            if (isUnlabeled) {
                issues.add(new ValidationIssueDTO(
                        "UNLABELED_GATEWAY_RULE",
                        "LOW",
                        node.getId(),
                        "Unlabeled decision gateway",
                        "Decision gateway has no descriptive name, hiding the decision criteria."
                ));
                recommendations.add("Name the gateway with the question it answers (e.g. 'Approved?').");
            }
        }
    }

    public void validateConsecutiveDuplicates(List<GraphNode> nodes,
                                              List<GraphEdge> edges,
                                              List<ValidationIssueDTO> issues,
                                              List<String> recommendations) {
        Map<String, GraphNode> activityMap = nodes.stream()
                .filter(n -> n.getType() == NodeType.Activity)
                .collect(Collectors.toMap(GraphNode::getId, n -> n, (a, b) -> a));

        Set<String> reportedPairs = new HashSet<>();

        for (GraphEdge edge : edges) {
            if (edge.getEdgeType() != EdgeType.sequence) continue;
            GraphNode from = activityMap.get(edge.getFrom());
            GraphNode to = activityMap.get(edge.getTo());
            if (from == null || to == null) continue;

            String label1 = from.getLabel().trim().toLowerCase(Locale.ROOT);
            String label2 = to.getLabel().trim().toLowerCase(Locale.ROOT);
            if (label1.equals(label2) && !label1.isBlank()) {
                String pairKey = from.getId() + "--" + to.getId();
                if (reportedPairs.add(pairKey)) {
                    issues.add(new ValidationIssueDTO(
                            "CONSECUTIVE_DUPLICATE_RULE",
                            "MEDIUM",
                            to.getId(),
                            "Duplicate consecutive activity: '" + to.getLabel() + "'",
                            "Two identical consecutive tasks '" + to.getLabel() + "' add no value."
                    ));
                    recommendations.add("Merge consecutive identical '" + to.getLabel() + "' tasks into one activity.");
                }
            }
        }
    }

    public void validateSwimlanesAndRoles(List<GraphNode> nodes,
                                          List<ValidationIssueDTO> issues,
                                          List<String> recommendations) {
        long roleCount = nodes.stream().filter(n -> n.getType() == NodeType.Role).count();

        Set<String> impliedRoles = new HashSet<>();
        for (GraphNode node : nodes) {
            if (node.getType() != NodeType.Activity) continue;
            String l = node.getLabel().toLowerCase(Locale.ROOT);
            if (l.contains("service desk") || l.contains("helpdesk")) impliedRoles.add("service desk");
            if (l.contains("agent") || l.contains("support")) impliedRoles.add("agent");
            if (l.contains("manager") || l.contains("supervisor")) impliedRoles.add("manager");
            if (l.contains("finance") || l.contains("accounting")) impliedRoles.add("finance");
            if (l.contains("employee") || l.contains("requester")) impliedRoles.add("employee");
        }

        if (roleCount == 0 && impliedRoles.size() >= 2) {
            issues.add(new ValidationIssueDTO(
                    "SWIMLANE_GOVERNANCE_RULE",
                    "MEDIUM",
                    null,
                    "Missing swimlanes / role partitions",
                    "Multiple operational roles (" + String.join(", ", impliedRoles) + ") are implied, but no pool/lanes assign ownership."
            ));
            recommendations.add("Introduce a pool with lanes (e.g. " + String.join(", ", impliedRoles) + ") and assign tasks.");
        }
    }

    public void validateLinearComplexity(List<GraphNode> nodes,
                                         List<GraphEdge> edges,
                                         List<ValidationIssueDTO> issues,
                                         List<String> recommendations) {
        List<GraphNode> activities = nodes.stream().filter(n -> n.getType() == NodeType.Activity).toList();
        List<GraphNode> gateways = nodes.stream().filter(n -> n.getType() == NodeType.Gateway).toList();

        if (activities.size() >= 7 && gateways.isEmpty()) {
            issues.add(new ValidationIssueDTO(
                    "LINEAR_COMPLEXITY_RULE",
                    "LOW",
                    null,
                    "Excessive linear complexity: " + activities.size() + " consecutive steps without branching",
                    "Long unbranched chain suggests over-decomposition. Consolidate related steps."
            ));
            recommendations.add("Consolidate fine-grained sequential steps to streamline the workflow.");
        }
    }

    public void validateDanglingFlows(List<GraphNode> nodes,
                                      List<GraphEdge> edges,
                                      List<ValidationIssueDTO> issues,
                                      List<String> recommendations) {
        Set<String> nodeIds = nodes.stream().map(GraphNode::getId).collect(Collectors.toSet());

        for (GraphEdge edge : edges) {
            if (!isProcessFlow(edge)) continue;
            boolean fromMissing = edge.getFrom() == null || !nodeIds.contains(edge.getFrom());
            boolean toMissing = edge.getTo() == null || !nodeIds.contains(edge.getTo());

            if (fromMissing || toMissing) {
                String direction = fromMissing && toMissing
                        ? "both source and target"
                        : fromMissing ? "source '" + edge.getFrom() + "'" : "target '" + edge.getTo() + "'";
                issues.add(new ValidationIssueDTO(
                        "DANGLING_FLOW",
                        "HIGH",
                        edge.getId(),
                        "Dangling sequence flow: " + edge.getId(),
                        "Sequence flow '" + edge.getId() + "' references " + direction + " which does not exist."
                ));
                recommendations.add("Remove or reconnect the dangling flow '" + edge.getId() + "'.");
            }
        }
    }
}
