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
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
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
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
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
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
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
                "MISSING_END_EVENT".equals(i.ruleId()) && "HIGH".equals(i.severity()) && i.issue().contains("Missing End Event")));
    }

    @Test
    @DisplayName("TASK-013: Detects dead-end activity that does not connect to any End Event")
    void testAbruptTerminationActivity() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
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
                "DEAD_END_ACTIVITY".equals(i.ruleId()) && i.issue().contains("Dead-end activity")));
    }

    @Test
    @DisplayName("TASK-014: Detects single branch decision gateway")
    void testSingleBranchGateway() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
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
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
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
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
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

    @Test
    @DisplayName("D02 & D06: Detects vague task names like 'Process' and 'Do needful'")
    void testVagueTaskNaming() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId("graph-vague-tasks")
                .addNodes(List.of(
                        GraphNode.builder().id("t1").type(NodeType.Activity).label("Process").build(),
                        GraphNode.builder().id("t2").type(NodeType.Activity).label("Do needful").build(),
                        GraphNode.builder().id("t3").type(NodeType.Activity).label("Verify applicant credit score").build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateTaskNaming(graph.getNodes(), issues, recommendations);

        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(i -> i.issue().contains("Process")));
        assertTrue(issues.stream().anyMatch(i -> i.issue().contains("Do needful")));
    }

    @Test
    @DisplayName("D05: Detects orphan node with 0 incoming and 0 outgoing sequence flows")
    void testOrphanNodeDetection() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId("graph-orphan")
                .addNodes(List.of(
                        GraphNode.builder().id("t1").type(NodeType.Activity).label("Submit request").build(),
                        GraphNode.builder().id("t2").type(NodeType.Activity).label("Approve request").build(),
                        GraphNode.builder().id("t9").type(NodeType.Activity).label("Update HR calendar").build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("t1").to("t2").edgeType(EdgeType.sequence).build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateConnectivityAndOrphans(graph.getNodes(), graph.getEdges(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i -> "ORPHAN_NODE_RULE".equals(i.ruleId()) && i.issue().contains("Update HR calendar")));
    }

    @Test
    @DisplayName("D07: Detects parallel split without matching join gateway")
    void testParallelSplitWithoutJoin() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId("graph-parallel-mismatch")
                .addNodes(List.of(
                        GraphNode.builder().id("gs").type(NodeType.Gateway).label("Fulfil in parallel")
                                .metadata(NodeMetadata.builder().gatewayType(GatewayType.parallel).build()).build(),
                        GraphNode.builder().id("t1").type(NodeType.Activity).label("Pick and pack").build(),
                        GraphNode.builder().id("t2").type(NodeType.Activity).label("Generate invoice").build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("gs").to("t1").edgeType(EdgeType.sequence).build(),
                        GraphEdge.builder().id("e2").from("gs").to("t2").edgeType(EdgeType.sequence).build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateParallelGateways(graph.getNodes(), graph.getEdges(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i -> "PARALLEL_SPLIT_JOIN_RULE".equals(i.ruleId())));
    }

    @Test
    @DisplayName("D10: Detects unlabeled decision gateway")
    void testUnlabeledDecisionGateway() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId("graph-unlabeled-gw")
                .addNodes(List.of(
                        GraphNode.builder().id("g1").type(NodeType.Gateway).label("").build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateGatewayLabels(graph.getNodes(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i -> "UNLABELED_GATEWAY_RULE".equals(i.ruleId())));
    }

    @Test
    @DisplayName("D11: Detects duplicate consecutive activities")
    void testDuplicateConsecutiveActivities() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId("graph-duplicate-tasks")
                .addNodes(List.of(
                        GraphNode.builder().id("t4").type(NodeType.Activity).label("Investigate issue").build(),
                        GraphNode.builder().id("t5").type(NodeType.Activity).label("Investigate issue").build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("e1").from("t4").to("t5").edgeType(EdgeType.sequence).build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateConsecutiveDuplicates(graph.getNodes(), graph.getEdges(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i -> "DUPLICATE_ACTIVITY_RULE".equals(i.ruleId())));
    }

    @Test
    @DisplayName("D12: Detects missing swimlanes when multiple operational roles are present")
    void testMissingSwimlanesDetection() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId("graph-missing-lanes")
                .addNodes(List.of(
                        GraphNode.builder().id("t1").type(NodeType.Activity).label("Service desk logs ticket").build(),
                        GraphNode.builder().id("t2").type(NodeType.Activity).label("Support agent investigates issue").build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateSwimlanesAndRoles(graph.getNodes(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i -> "SWIMLANE_GOVERNANCE_RULE".equals(i.ruleId())));
    }

    @Test
    @DisplayName("D13: Detects excessive linear complexity with over-decomposed steps")
    void testExcessiveLinearComplexity() {
        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId("graph-linear-chain")
                .addNodes(List.of(
                        GraphNode.builder().id("t1").type(NodeType.Activity).label("Step 1").build(),
                        GraphNode.builder().id("t2").type(NodeType.Activity).label("Step 2").build(),
                        GraphNode.builder().id("t3").type(NodeType.Activity).label("Step 3").build(),
                        GraphNode.builder().id("t4").type(NodeType.Activity).label("Step 4").build(),
                        GraphNode.builder().id("t5").type(NodeType.Activity).label("Step 5").build(),
                        GraphNode.builder().id("t6").type(NodeType.Activity).label("Step 6").build(),
                        GraphNode.builder().id("t7").type(NodeType.Activity).label("Step 7").build(),
                        GraphNode.builder().id("t8").type(NodeType.Activity).label("Step 8").build()
                ))
                .build();

        List<ValidationIssueDTO> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        validator.validateLinearComplexity(graph.getNodes(), List.of(), issues, recommendations);

        assertTrue(issues.stream().anyMatch(i -> "LINEAR_COMPLEXITY_RULE".equals(i.ruleId())));
    }
}
