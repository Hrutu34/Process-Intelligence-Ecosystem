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
        return overlap >= 0.66 || similarity >= 0.82;
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
}
