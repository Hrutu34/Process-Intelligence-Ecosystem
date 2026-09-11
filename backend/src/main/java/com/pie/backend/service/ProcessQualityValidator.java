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

    public ProcessQualityReportDTO validateQuality(CanonicalProcessGraph graph) {
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

        // 4. Validate Task Naming & Actionability (D02, D06)
        validateTaskNaming(nodes, issues, recommendations);

        // 5. Validate Connectivity & Orphan Nodes (D05)
        validateConnectivityAndOrphans(nodes, edges, issues, recommendations);

        // 6. Validate Parallel Gateway Synchronization (D07)
        validateParallelGateways(nodes, edges, issues, recommendations);

        // 7. Validate Gateway Question Labels (D10)
        validateGatewayLabels(nodes, issues, recommendations);

        // 8. Validate Duplicate Consecutive Activities (D11)
        validateConsecutiveDuplicates(nodes, edges, issues, recommendations);

        // 9. Validate Swimlanes & Role Ownership (D12)
        validateSwimlanesAndRoles(nodes, issues, recommendations);

        // 10. Validate Linear Complexity & Over-decomposition (D13)
        validateLinearComplexity(nodes, edges, issues, recommendations);

        // Calculate Quality Score
        int highCount = (int) issues.stream().filter(i -> "HIGH".equalsIgnoreCase(i.severity())).count();
        int medCount = (int) issues.stream().filter(i -> "MEDIUM".equalsIgnoreCase(i.severity())).count();
        int lowCount = (int) issues.stream().filter(i -> "LOW".equalsIgnoreCase(i.severity()) || "WARNING".equalsIgnoreCase(i.severity())).count();

        int penalty = (highCount * 25) + (medCount * 10) + (lowCount * 5);
        int qualityScore = Math.max(0, Math.min(100, 100 - penalty));
        boolean isValid = highCount == 0;

        log.info("Quality validation completed for [{}]: score={}, issues={}, recommendations={}",
                graph.getGraphId(), qualityScore, issues.size(), recommendations.size());

        return new ProcessQualityReportDTO(isValid, qualityScore, issues, recommendations);
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

            List<GraphEdge> incoming = edges.stream()
                    .filter(e -> e.getTo().equals(gw.getId()) && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional))
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
            "execute", "perform", "action", "step", "miscellaneous", "etc", "activity"
    );

    /**
     * Rule 15.1: Validate Task Naming & Actionability (D02, D06)
     */
    public void validateTaskNaming(List<GraphNode> nodes,
                                   List<ValidationIssueDTO> issues,
                                   List<String> recommendations) {
        for (GraphNode node : nodes) {
            if (node.getType() == NodeType.Activity) {
                String label = node.getLabel() != null ? node.getLabel().trim() : "";
                String norm = label.toLowerCase(Locale.ROOT);

                boolean isVague = VAGUE_TASK_LABELS.contains(norm)
                        || norm.equals("do needful")
                        || (norm.length() <= 8 && VAGUE_TASK_LABELS.stream().anyMatch(norm::startsWith));

                if (isVague) {
                    issues.add(new ValidationIssueDTO(
                            "TASK_NAME_QUALITY_RULE",
                            "MEDIUM",
                            node.getId(),
                            "Vague task name: '" + label + "'",
                            "Task named '" + label + "' is non-actionable, ambiguous, and lacks a clear business object."
                    ));
                    recommendations.add("Rename task '" + label + "' to a concrete verb+object action (e.g. 'Approve invoice for payment' or 'Verify details').");
                }
            }
        }
    }

    /**
     * Rule 15.2: Validate Connectivity & Orphan Nodes (D05)
     */
    public void validateConnectivityAndOrphans(List<GraphNode> nodes,
                                               List<GraphEdge> edges,
                                               List<ValidationIssueDTO> issues,
                                               List<String> recommendations) {
        if (nodes.size() <= 1) return;

        for (GraphNode node : nodes) {
            if (node.getType() == NodeType.Activity || node.getType() == NodeType.Gateway) {
                long inDegree = edges.stream().filter(e -> e.getTo().equals(node.getId())
                        && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional)).count();
                long outDegree = edges.stream().filter(e -> e.getFrom().equals(node.getId())
                        && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional)).count();

                if (inDegree == 0 && outDegree == 0) {
                    issues.add(new ValidationIssueDTO(
                            "ORPHAN_NODE_RULE",
                            "HIGH",
                            node.getId(),
                            "Orphan / unconnected node: '" + node.getLabel() + "'",
                            "Node '" + node.getLabel() + "' has no incoming or outgoing sequence flows; it is completely disconnected from the process."
                    ));
                    recommendations.add("Connect '" + node.getLabel() + "' into the process flow (e.g. between predecessor activities and subsequent steps or an End Event).");
                }
            }
        }
    }

    /**
     * Rule 15.3: Validate Parallel Gateway Synchronization (D07)
     */
    public void validateParallelGateways(List<GraphNode> nodes,
                                         List<GraphEdge> edges,
                                         List<ValidationIssueDTO> issues,
                                         List<String> recommendations) {
        List<GraphNode> parallelSplits = nodes.stream()
                .filter(n -> n.getType() == NodeType.Gateway)
                .filter(n -> {
                    boolean isParallelType = n.getMetadata() != null && n.getMetadata().getGatewayType() == GatewayType.parallel;
                    boolean isParallelLabel = n.getLabel() != null && n.getLabel().toLowerCase(Locale.ROOT).contains("parallel");
                    long outCount = edges.stream().filter(e -> e.getFrom().equals(n.getId())
                            && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional)).count();
                    return (isParallelType || isParallelLabel) && outCount > 1;
                })
                .toList();

        List<GraphNode> parallelJoins = nodes.stream()
                .filter(n -> n.getType() == NodeType.Gateway)
                .filter(n -> {
                    boolean isParallelType = n.getMetadata() != null && n.getMetadata().getGatewayType() == GatewayType.parallel;
                    long inCount = edges.stream().filter(e -> e.getTo().equals(n.getId())
                            && (e.getEdgeType() == EdgeType.sequence || e.getEdgeType() == EdgeType.conditional)).count();
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
                        "Parallel split gateway '" + split.getLabel() + "' has no matching parallel join gateway; concurrent branches never synchronize."
                ));
                recommendations.add("Add a matching parallel join gateway that merges both parallel branches before the end event.");
            }
        }
    }

    /**
     * Rule 15.4: Validate Gateway Question Labels (D10)
     */
    public void validateGatewayLabels(List<GraphNode> nodes,
                                      List<ValidationIssueDTO> issues,
                                      List<String> recommendations) {
        for (GraphNode node : nodes) {
            if (node.getType() == NodeType.Gateway) {
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
                            "Decision gateway has no descriptive name or question, hiding the decision criteria being evaluated."
                    ));
                    recommendations.add("Name the decision gateway with the question it answers (e.g. 'Receipts valid?' or 'Approved?').");
                }
            }
        }
    }

    /**
     * Rule 15.5: Validate Duplicate Consecutive Activities (D11)
     */
    public void validateConsecutiveDuplicates(List<GraphNode> nodes,
                                              List<GraphEdge> edges,
                                              List<ValidationIssueDTO> issues,
                                              List<String> recommendations) {
        Map<String, GraphNode> activityMap = nodes.stream()
                .filter(n -> n.getType() == NodeType.Activity)
                .collect(Collectors.toMap(GraphNode::getId, n -> n, (a, b) -> a));

        Set<String> reportedPairs = new HashSet<>();

        for (GraphEdge edge : edges) {
            if (edge.getEdgeType() == EdgeType.sequence) {
                GraphNode from = activityMap.get(edge.getFrom());
                GraphNode to = activityMap.get(edge.getTo());

                if (from != null && to != null) {
                    String label1 = from.getLabel().trim().toLowerCase(Locale.ROOT);
                    String label2 = to.getLabel().trim().toLowerCase(Locale.ROOT);

                    if (label1.equals(label2) && !label1.isBlank()) {
                        String pairKey = from.getId() + "--" + to.getId();
                        if (reportedPairs.add(pairKey)) {
                            issues.add(new ValidationIssueDTO(
                                    "DUPLICATE_ACTIVITY_RULE",
                                    "MEDIUM",
                                    to.getId(),
                                    "Duplicate consecutive activity: '" + to.getLabel() + "'",
                                    "Two identical consecutive tasks '" + to.getLabel() + "' add no value and cause process redundancy."
                            ));
                            recommendations.add("Merge consecutive identical '" + to.getLabel() + "' tasks into a single activity.");
                        }
                    }
                }
            }
        }
    }

    /**
     * Rule 15.6: Validate Swimlanes & Role Ownership (D12)
     */
    public void validateSwimlanesAndRoles(List<GraphNode> nodes,
                                          List<ValidationIssueDTO> issues,
                                          List<String> recommendations) {
        long roleCount = nodes.stream().filter(n -> n.getType() == NodeType.Role).count();

        // Also check if multiple roles are implied in activity labels (e.g. "(agent)", "(manager)", "assign to agent", "service desk")
        Set<String> impliedRoles = new HashSet<>();
        for (GraphNode node : nodes) {
            if (node.getType() == NodeType.Activity) {
                String l = node.getLabel().toLowerCase(Locale.ROOT);
                if (l.contains("service desk") || l.contains("helpdesk")) impliedRoles.add("service desk");
                if (l.contains("agent") || l.contains("support")) impliedRoles.add("agent");
                if (l.contains("manager") || l.contains("supervisor")) impliedRoles.add("manager");
                if (l.contains("finance") || l.contains("accounting")) impliedRoles.add("finance");
                if (l.contains("employee") || l.contains("requester")) impliedRoles.add("employee");
            }
        }

        if (roleCount == 0 && impliedRoles.size() >= 2) {
            issues.add(new ValidationIssueDTO(
                    "SWIMLANE_GOVERNANCE_RULE",
                    "MEDIUM",
                    null,
                    "Missing swimlanes / role partitions",
                    "Multiple operational roles (" + String.join(", ", impliedRoles) + ") are implied in the process, but no pool or swimlanes assign explicit ownership."
            ));
            recommendations.add("Introduce a pool with swimlanes (e.g. " + String.join(", ", impliedRoles) + ") and assign tasks to each lane.");
        }
    }

    /**
     * Rule 15.7: Validate Linear Complexity & Over-decomposition (D13)
     */
    public void validateLinearComplexity(List<GraphNode> nodes,
                                         List<GraphEdge> edges,
                                         List<ValidationIssueDTO> issues,
                                         List<String> recommendations) {
        List<GraphNode> activities = nodes.stream().filter(n -> n.getType() == NodeType.Activity).toList();
        List<GraphNode> gateways = nodes.stream().filter(n -> n.getType() == NodeType.Gateway).toList();

        // If a process has >= 7 activities and 0 gateways, it's a long unbranched linear chain
        if (activities.size() >= 7 && gateways.isEmpty()) {
            issues.add(new ValidationIssueDTO(
                    "LINEAR_COMPLEXITY_RULE",
                    "LOW",
                    null,
                    "Excessive linear complexity: " + activities.size() + " consecutive steps without branching",
                    "Long unbranched chain suggests over-decomposition of activities; several steps can be consolidated to improve readability."
            ));
            recommendations.add("Consolidate fine-grained sequential steps (e.g. 'Apply fix' + 'Test fix') to streamline the workflow.");
        }
    }
}
