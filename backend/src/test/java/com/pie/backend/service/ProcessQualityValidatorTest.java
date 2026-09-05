package com.pie.backend.service;

import com.pie.shared.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessQualityValidatorTest {

    private ProcessQualityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProcessQualityValidator();
    }

    @Test
    @DisplayName("TASK-012: Detects missing start event and assigns HIGH severity")
    void testMissingStartEvent() {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-missing-start")
                .addNodes(List.of(
                        GraphNode.builder().id("activity-1").type(NodeType.Activity).label("Perform Task").build(),
                        GraphNode.builder().id("event-end").type(NodeType.Event).label("End").metadata(NodeMetadata.builder().eventType(EventType.end).build()).build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("activity-1").to("event-end").edgeType(EdgeType.sequence).build()
                ))
                .build();

        ProcessQualityReportDTO report = validator.validateQuality(graph);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(i ->
                "START_EVENT_RULE".equals(i.ruleId()) && "HIGH".equals(i.severity()) && i.issue().contains("Missing Start Event")));
    }

    @Test
    @DisplayName("TASK-012: Detects invalid initiation sequence when process starts with approval without prior trigger")
    void testInvalidInitiationSequence() {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-approval-first")
                .addNodes(List.of(
                        GraphNode.builder().id("activity-approve").type(NodeType.Activity).label("Manager approves request").build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateStartEvents(graph.getNodes(), List.of(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i ->
                "START_EVENT_RULE".equals(i.ruleId()) && i.issue().contains("starts with approval")));
    }

    @Test
    @DisplayName("TASK-013: Detects missing end event and assigns HIGH severity")
    void testMissingEndEvent() {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-missing-end")
                .addNodes(List.of(
                        GraphNode.builder().id("event-start").type(NodeType.Event).label("Start").metadata(NodeMetadata.builder().eventType(EventType.start).build()).build(),
                        GraphNode.builder().id("activity-1").type(NodeType.Activity).label("Process Order").build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("event-start").to("activity-1").edgeType(EdgeType.sequence).build()
                ))
                .build();

        ProcessQualityReportDTO report = validator.validateQuality(graph);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(i ->
                "END_EVENT_RULE".equals(i.ruleId()) && "HIGH".equals(i.severity()) && i.issue().contains("Missing End Event")));
    }

    @Test
    @DisplayName("TASK-013: Detects abrupt termination when activity has no outgoing flow to End Event")
    void testAbruptTerminationActivity() {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-abrupt-end")
                .addNodes(List.of(
                        GraphNode.builder().id("event-start").type(NodeType.Event).label("Start").metadata(NodeMetadata.builder().eventType(EventType.start).build()).build(),
                        GraphNode.builder().id("activity-1").type(NodeType.Activity).label("Review Document").build(),
                        GraphNode.builder().id("event-end").type(NodeType.Event).label("End").metadata(NodeMetadata.builder().eventType(EventType.end).build()).build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("event-start").to("activity-1").edgeType(EdgeType.sequence).build()
                        // activity-1 is not connected to event-end!
                ))
                .build();

        ProcessQualityReportDTO report = validator.validateQuality(graph);

        assertTrue(report.issues().stream().anyMatch(i ->
                "END_EVENT_RULE".equals(i.ruleId()) && i.issue().contains("Process ends abruptly without closure")));
    }

        @Test
        @DisplayName("TASK-016: Detects semantically overlapping activity labels")
        void testSemanticDuplicateActivities() {
                List<GraphNode> nodes = List.of(
                                GraphNode.builder().id("activity-review").type(NodeType.Activity).label("Review Application").build(),
                                GraphNode.builder().id("activity-review-submitted").type(NodeType.Activity).label("Review Submitted Application").build()
                );
                List<ValidationIssueDTO> issues = new ArrayList<>();
                List<String> recommendations = new ArrayList<>();

                validator.validateDuplicates(nodes, issues, recommendations);

                assertTrue(issues.stream().anyMatch(issue -> "DUPLICATE_ACTIVITY_RULE".equals(issue.ruleId())));
                assertFalse(recommendations.isEmpty());
        }

        @Test
        @DisplayName("TASK-016: Does not flag distinct procurement stages sharing a noun")
        void testDistinctProcurementActivitiesAreNotDuplicates() {
                List<GraphNode> nodes = List.of(
                                GraphNode.builder().id("activity-request").type(NodeType.Activity).label("Create Asset Purchase Request").build(),
                                GraphNode.builder().id("activity-order").type(NodeType.Activity).label("Create Purchase Order").build(),
                                GraphNode.builder().id("activity-close").type(NodeType.Activity).label("Close purchase order").build()
                );
                List<ValidationIssueDTO> issues = new ArrayList<>();
                validator.validateDuplicates(nodes, issues, new ArrayList<>());

                assertTrue(issues.isEmpty());
        }

            @Test
            @DisplayName("Detects disconnected process elements instead of treating the graph as stable")
            void testDisconnectedProcessElement() {
                ProcessGraphDTO graph = ProcessGraphDTO.builder()
                        .graphId("graph-disconnected")
                        .addNodes(List.of(
                                GraphNode.builder().id("event-start").type(NodeType.Event).label("Request Received").metadata(NodeMetadata.builder().eventType(EventType.start).build()).build(),
                                GraphNode.builder().id("activity-submit").type(NodeType.Activity).label("Submit Request").build(),
                                GraphNode.builder().id("activity-unconnected").type(NodeType.Activity).label("Review Request").build(),
                                GraphNode.builder().id("event-end").type(NodeType.Event).label("Completed").metadata(NodeMetadata.builder().eventType(EventType.end).build()).build()
                        ))
                        .addEdges(List.of(
                                GraphEdge.builder().id("e1").from("event-start").to("activity-submit").edgeType(EdgeType.sequence).build(),
                                GraphEdge.builder().id("e2").from("activity-submit").to("event-end").edgeType(EdgeType.sequence).build()
                        ))
                        .build();

                ProcessQualityReportDTO report = validator.validateQuality(graph);

                assertTrue(report.issues().stream().anyMatch(issue -> "FLOW_COVERAGE_RULE".equals(issue.ruleId())));
                assertTrue(report.qualityScore() < 100);
            }

            @Test
            @DisplayName("Detects unowned activities when roles or systems are present")
            void testUnownedActivity() {
                ProcessGraphDTO graph = ProcessGraphDTO.builder()
                        .graphId("graph-unowned-activity")
                        .addNodes(List.of(
                                GraphNode.builder().id("event-start").type(NodeType.Event).label("Start").metadata(NodeMetadata.builder().eventType(EventType.start).build()).build(),
                                GraphNode.builder().id("activity-submit").type(NodeType.Activity).label("Submit Request").metadata(NodeMetadata.builder().build()).build(),
                                GraphNode.builder().id("role-employee").type(NodeType.Role).label("Employee").metadata(NodeMetadata.builder().build()).build(),
                                GraphNode.builder().id("event-end").type(NodeType.Event).label("End").metadata(NodeMetadata.builder().eventType(EventType.end).build()).build()
                        ))
                        .addEdges(List.of(
                                GraphEdge.builder().id("e1").from("event-start").to("activity-submit").edgeType(EdgeType.sequence).build(),
                                GraphEdge.builder().id("e2").from("activity-submit").to("event-end").edgeType(EdgeType.sequence).build()
                        ))
                        .build();

                ProcessQualityReportDTO report = validator.validateQuality(graph);

                assertTrue(report.issues().stream().anyMatch(issue -> "ACTIVITY_OWNER_RULE".equals(issue.ruleId())));
                assertTrue(report.qualityScore() < 100);
            }

    @Test
    @DisplayName("TASK-014: Detects single branch decision gateway")
    void testSingleBranchGateway() {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-single-branch")
                .addNodes(List.of(
                        GraphNode.builder().id("gateway-1").type(NodeType.Gateway).label("Check Budget").build(),
                        GraphNode.builder().id("activity-1").type(NodeType.Activity).label("Proceed").build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("gateway-1").to("activity-1").edgeType(EdgeType.conditional).label("Approved").build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateGateways(graph.getNodes(), graph.getEdges(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i ->
                "GATEWAY_RULE_SINGLE_BRANCH".equals(i.ruleId()) && "HIGH".equals(i.severity())));
        assertTrue(issues.stream().anyMatch(i ->
                "GATEWAY_RULE_MISSING_REJECTION".equals(i.ruleId()) && i.issue().contains("Rejected Path Missing")));
    }

    @Test
    @DisplayName("TASK-014: Detects missing condition on gateway branches")
    void testMissingGatewayCondition() {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-no-conditions")
                .addNodes(List.of(
                        GraphNode.builder().id("gateway-1").type(NodeType.Gateway).label("Evaluate").build(),
                        GraphNode.builder().id("activity-1").type(NodeType.Activity).label("Path A").build(),
                        GraphNode.builder().id("activity-2").type(NodeType.Activity).label("Path B").build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("gateway-1").to("activity-1").edgeType(EdgeType.conditional).label("").build(),
                        GraphEdge.builder().id("e2").from("gateway-1").to("activity-2").edgeType(EdgeType.conditional).label(null).build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateGateways(graph.getNodes(), graph.getEdges(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i ->
                "GATEWAY_RULE_MISSING_CONDITION".equals(i.ruleId())));
    }

    @Test
    @DisplayName("TASK-014: Detects dead-end branch from a gateway")
    void testDeadEndBranchFromGateway() {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-dead-end")
                .addNodes(List.of(
                        GraphNode.builder().id("gateway-1").type(NodeType.Gateway).label("Quality OK?").build(),
                        GraphNode.builder().id("activity-pass").type(NodeType.Activity).label("Ship Product").build(),
                        GraphNode.builder().id("activity-fail").type(NodeType.Activity).label("Log Error").build(),
                        GraphNode.builder().id("event-end").type(NodeType.Event).label("End").metadata(NodeMetadata.builder().eventType(EventType.end).build()).build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("gateway-1").to("activity-pass").edgeType(EdgeType.conditional).label("Pass").build(),
                        GraphEdge.builder().id("e2").from("gateway-1").to("activity-fail").edgeType(EdgeType.conditional).label("Fail").build(),
                        GraphEdge.builder().id("e3").from("activity-pass").to("event-end").edgeType(EdgeType.sequence).build()
                        // activity-fail has no outgoing flow!
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateGateways(graph.getNodes(), graph.getEdges(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i ->
                "GATEWAY_RULE_DEAD_END".equals(i.ruleId()) && i.issue().contains("Dead-End Branch Detected")));
    }
}
