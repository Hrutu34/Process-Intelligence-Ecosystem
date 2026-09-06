package com.pie.backend.service;

import com.pie.shared.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalProcessGraphBuilderTest {

        private ProcessGraphBuilder builder;
    private ProcessGraphValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProcessGraphValidator();
        builder = new ProcessGraphBuilder(validator);
    }

    @Test
    @DisplayName("Test 1 — Simple linear process: creates Activity nodes and sequential flow")
    void test1_simpleLinearProcess() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Submit Travel Request", "Review Request", "Book Travel"),
                List.of("Employee", "Manager", "Travel Desk"),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        assertNotNull(graph);
        assertEquals(6, graph.getNodes().size()); // 3 activities + 3 roles

        // Verify sequence flow
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-submit-travel-request") &&
                e.getTo().equals("activity-review-request") &&
                e.getEdgeType() == EdgeType.sequence));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-review-request") &&
                e.getTo().equals("activity-book-travel") &&
                e.getEdgeType() == EdgeType.sequence));
    }

    @Test
    @DisplayName("Test 2 — Single XOR Decision: Evaluating activity precedes gateway, conditional branch created")
    void test2_singleXorDecision() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Review Request", "Validate Budget"),
                List.of("Manager", "Finance"),
                List.of(), List.of(), List.of(),
                List.of("Manager Approval"),
                List.of(), List.of(),
                List.of("If approved, Finance validates budget"),
                List.of(), List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        // Sequence: Review Request -> Manager Approval Gateway
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-review-request") &&
                e.getTo().equals("gateway-manager-approval") &&
                e.getEdgeType() == EdgeType.sequence));

        // Conditional: Manager Approval --approved--> Validate Budget
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-manager-approval") &&
                e.getTo().equals("activity-validate-budget") &&
                e.getEdgeType() == EdgeType.conditional &&
                "approved".equals(e.getLabel())));
    }

    @Test
    @DisplayName("Test 3 — IF / ELSE Branching: Gateway produces multiple conditional branches")
    void test3_ifElseBranching() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Check Policy", "Financial Validation", "Manager Review Exception"),
                List.of("System", "Finance", "Manager"),
                List.of(), List.of("Expense System"), List.of(),
                List.of("Within Policy?"),
                List.of(), List.of(),
                List.of("If within policy, proceed to financial validation. Otherwise, manager reviews exception."),
                List.of(), List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        // Rule 1: Check Policy -> Within Policy Gateway
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-check-policy") &&
                e.getTo().equals("gateway-within-policy") &&
                e.getEdgeType() == EdgeType.sequence));

        // Rule 2: Gateway --yes--> Financial Validation
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-within-policy") &&
                e.getTo().equals("activity-financial-validation") &&
                e.getEdgeType() == EdgeType.conditional &&
                "yes".equals(e.getLabel())));

        // Rule 2: Gateway --no--> Manager Review Exception
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-within-policy") &&
                e.getTo().equals("activity-manager-review-exception") &&
                e.getEdgeType() == EdgeType.conditional &&
                "no".equals(e.getLabel())));
    }

    @Test
    @DisplayName("Test 4 — Loop Modeling: Correction and Resubmission loop back to validation")
    void test4_correctionLoop() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Validate Request", "Correct Request", "Resubmit Request", "Check Policy"),
                List.of("System", "Employee"),
                List.of(), List.of(), List.of(),
                List.of("Information Complete?"),
                List.of(), List.of(),
                List.of("If missing information, employee corrects request and resubmits it."),
                List.of(), List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        // Validate Request -> Gateway
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-validate-request") &&
                e.getTo().equals("gateway-information-complete") &&
                e.getEdgeType() == EdgeType.sequence));

        // Gateway --no--> Correct Request
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-information-complete") &&
                e.getTo().equals("activity-correct-request") &&
                e.getEdgeType() == EdgeType.conditional &&
                "no".equals(e.getLabel())));

        // Resubmit Request -> Validate Request (LOOP BACK)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-resubmit-request") &&
                e.getTo().equals("activity-validate-request") &&
                e.getEdgeType() == EdgeType.sequence));
    }

    @Test
    @DisplayName("Test 5 — Retry Loop: Payment Retry loops back to Send Payment")
    void test5_paymentRetryLoop() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Send Payment", "Mark Paid", "Retry Payment", "Investigate Failure"),
                List.of("Payment System", "Finance"),
                List.of(), List.of("Payment System"), List.of(),
                List.of("Payment Successful?", "Retry Limit Reached?"),
                List.of(), List.of(),
                List.of("If payment fails, retry up to 3 times. If limit reached, investigate failure."),
                List.of(), List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        // Send Payment -> Payment Successful Gateway
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-send-payment") &&
                e.getTo().equals("gateway-payment-successful") &&
                e.getEdgeType() == EdgeType.sequence));

        // Gateway --success--> Mark Paid
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-payment-successful") &&
                e.getTo().equals("activity-mark-paid") &&
                e.getEdgeType() == EdgeType.conditional &&
                "success".equals(e.getLabel())));

        // Retry Payment -> Send Payment (LOOP BACK)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-retry-payment") &&
                e.getTo().equals("activity-send-payment") &&
                e.getEdgeType() == EdgeType.sequence));
    }

    @Test
    @DisplayName("Test 6 — Timeout / Timer Event: 7-day timeout produces timer event with ISO duration")
    void test6_timeoutTimerEvent() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Request Clarification", "Review Updated Information", "Automatically Cancel Request"),
                List.of("Finance", "Employee"),
                List.of(), List.of(), List.of(),
                List.of("Information Received within 7 Days?"),
                List.of(), List.of(),
                List.of("If employee does not provide information within 7 days, cancel request."),
                List.of(), List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        // Verify Timer Event exists
        GraphNode timerEvent = graph.getNodes().stream()
                .filter(n -> n.getType() == NodeType.Event && n.getMetadata() != null && n.getMetadata().getEventType() == EventType.timer)
                .findFirst().orElseThrow();

        assertEquals("P7D", timerEvent.getMetadata().getDuration());

        // Gateway -> Timer Event -> Cancel Request
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-information-received-within-7-days") &&
                e.getTo().equals(timerEvent.getId())));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals(timerEvent.getId()) &&
                e.getTo().equals("activity-automatically-cancel-request")));
    }

    @Test
    @DisplayName("Test 7 — Role & System Association: Correct actor roles and systems assigned")
    void test7_rolesAndSystems() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Finance verifies receipts", "Payment system sends payment"),
                List.of("Finance"),
                List.of(),
                List.of("Payment System"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        // Check Role
        GraphNode finAct = graph.getNodes().stream()
                .filter(n -> n.getId().equals("activity-finance-verifies-receipts"))
                .findFirst().orElseThrow();
        assertEquals("role-finance", finAct.getMetadata().getRoleRef());

        // Check System
        GraphNode payAct = graph.getNodes().stream()
                .filter(n -> n.getId().equals("activity-payment-system-sends-payment"))
                .findFirst().orElseThrow();
        assertEquals("system-payment-system", payAct.getMetadata().getSystemRef());
    }

    @Test
    @DisplayName("Test 8 — Complete Regression Test: Employee Expense Reimbursement Process")
    void test8_completeExpenseReimbursementRegression() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of(
                        "Employee submits an expense reimbursement request",
                        "System validates that all mandatory fields and receipts are present",
                        "System sends the request back to the employee for correction",
                        "The employee corrects the request and resubmits it",
                        "System checks whether the expense is within policy",
                        "System sends the request to manager for exception decision",
                        "Manager reviews policy violation",
                        "System rejects reimbursement request and notifies employee",
                        "Finance validates available reimbursement budget",
                        "Finance verifies receipts and expense amounts",
                        "Finance sends request to employee for clarification",
                        "Employee provides required clarification",
                        "Finance reviews updated information",
                        "Finance approves reimbursement",
                        "Finance sends request to finance manager for additional budget approval",
                        "Finance manager reviews budget request",
                        "Payment system creates reimbursement payment",
                        "Payment system sends payment to bank account",
                        "System marks reimbursement as paid and sends confirmation notification",
                        "System retries payment up to three times",
                        "System creates payment failure case for finance team",
                        "Finance team investigates payment failure and processes payment manually",
                        "System automatically cancels reimbursement request",
                        "Completed reimbursement request is archived for auditing"
                ),
                List.of(
                        "Employee",
                        "Manager",
                        "Finance",
                        "Finance Manager",
                        "Finance Team"
                ),
                List.of(),
                List.of(
                        "Expense Reimbursement System",
                        "Payment System",
                        "Banking System"
                ),
                List.of(
                        "Expense Reimbursement Request Initiated",
                        "Reimbursement Process Completed"
                ),
                List.of(
                        "Information Complete?",
                        "Within Policy?",
                        "Manager Exception Approved?",
                        "Budget Available?",
                        "Receipts Valid?",
                        "Information Received within 7 Days?",
                        "Additional Budget Approved?",
                        "Payment Successful?",
                        "Retry Limit Reached?"
                ),
                List.of(
                        "Expense Details",
                        "Receipts"
                ),
                List.of(
                        "Reimbursement Budget",
                        "Payment Failure Case"
                ),
                List.of(
                        "If information missing, send back for correction and resubmit",
                        "If within policy, proceed to financial validation",
                        "If manager rejects exception, reject and notify",
                        "If sufficient budget, verify receipts",
                        "If receipts invalid, request clarification; if within 7 days review updated info, else cancel",
                        "If insufficient budget, finance manager reviews budget",
                        "If payment fails, retry up to 3 times, else investigate failure and manual payment"
                ),
                List.of(),
                List.of()
        );

        ProcessGraphDTO graph = builder.build("graph-expense-reimbursement", input);

        assertNotNull(graph);
        assertEquals("graph-expense-reimbursement", graph.getGraphId());

        // Verify all 9 gateways are present
        assertEquals(9, graph.getNodes().stream().filter(n -> n.getType() == NodeType.Gateway).count());

        // Verify Start Event connects to first activity
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("event-expense-reimbursement-request-initiated") &&
                e.getTo().equals("activity-employee-submits-an-expense-reimbursement-request")));

        // Verify Validation -> Information Gateway -> Correction -> Resubmission Loop
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-system-validates-that-all-mandatory-fields-and-receipts-are-present") &&
                e.getTo().equals("gateway-information-complete")));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-information-complete") &&
                e.getTo().equals("activity-system-sends-the-request-back-to-the-employee-for-correction") &&
                "no".equals(e.getLabel())));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-the-employee-corrects-the-request-and-resubmits-it") &&
                e.getTo().equals("activity-system-validates-that-all-mandatory-fields-and-receipts-are-present")));

        // Verify Policy Gateway -> Merges to Financial Validation
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-within-policy") &&
                e.getTo().equals("activity-finance-validates-available-reimbursement-budget") &&
                "yes".equals(e.getLabel())));

        // Verify Manager Exception Gateway -> Merges to Financial Validation
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-manager-exception-approved") &&
                e.getTo().equals("activity-finance-validates-available-reimbursement-budget") &&
                "approved".equals(e.getLabel())));

        // Verify Budget Available Gateway
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-budget-available") &&
                e.getTo().equals("activity-finance-verifies-receipts-and-expense-amounts") &&
                "sufficient".equals(e.getLabel())));

        // Verify 7-day Timeout Timer Event
        GraphNode timerNode = graph.getNodes().stream()
                .filter(n -> n.getType() == NodeType.Event && n.getMetadata() != null && n.getMetadata().getEventType() == EventType.timer)
                .findFirst().orElseThrow();
        assertEquals("P7D", timerNode.getMetadata().getDuration());

        // Verify Payment Retry Loop
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-system-retries-payment-up-to-three-times") &&
                e.getTo().equals("activity-payment-system-sends-payment-to-bank-account")));

        // Verify Archiving connects to End Event
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-completed-reimbursement-request-is-archived-for-auditing") &&
                e.getTo().equals("event-reimbursement-process-completed")));

        // Print human-readable summary of the graph topology
        System.out.println("=== GENERATED GRAPH TOPOLOGY FOR EXPENSE REIMBURSEMENT ===");
        System.out.println("Total Nodes: " + graph.getNodes().size());
        System.out.println("Total Edges: " + graph.getEdges().size());
        for (GraphEdge edge : graph.getEdges()) {
            System.out.println("  " + edge.getFrom() + " --[" + edge.getEdgeType() + (edge.getLabel() != null ? " : " + edge.getLabel() : "") + "]--> " + edge.getTo());
        }
    }

    @Test
    @DisplayName("Test 9 — Industrial IoT Anomaly Detection: Generic Branching & Gateway Cascade")
    void test9_anomalyDetectionBranchingProcess() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of(
                        "detects anomaly",
                        "send telemetry alert",
                        "review dashboard",
                        "remotely recalibrate sensor",
                        "halt assembly line",
                        "dispatch maintenance technician",
                        "replace faulty sensor",
                        "log hardware swap",
                        "restart line"
                ),
                List.of("shift supervisor", "maintenance technician"),
                List.of(),
                List.of("MQTT", "IoT control panel", "ERP system"),
                List.of("anomaly detected", "process completed"),
                List.of("minor calibration drift?", "vibration exceeds critical safety thresholds?"),
                List.of(),
                List.of(),
                List.of(
                        "If the anomaly is a minor calibration drift, the supervisor remotely recalibrates the sensor using the IoT control panel.",
                        "If the vibration exceeds critical safety thresholds, the supervisor halts the assembly line and dispatches a maintenance technician."
                ),
                List.of(),
                List.of()
        );

        ProcessGraphDTO graph = builder.build(input);

        assertNotNull(graph);

        // 1. Initial sequential intake: Start -> detects anomaly -> send telemetry alert -> review dashboard
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("event-anomaly-detected") &&
                e.getTo().equals("activity-detects-anomaly")));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-detects-anomaly") &&
                e.getTo().equals("activity-send-telemetry-alert")));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-send-telemetry-alert") &&
                e.getTo().equals("activity-review-dashboard")));

        // 2. Evaluating activity connects to first gateway
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-review-dashboard") &&
                e.getTo().equals("gateway-minor-calibration-drift")));

        // 3. Gateway 1 branches to: (a) remotely recalibrate sensor, (b) Gateway 2
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-minor-calibration-drift") &&
                e.getTo().equals("activity-remotely-recalibrate-sensor") &&
                e.getEdgeType() == EdgeType.conditional));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-minor-calibration-drift") &&
                e.getTo().equals("gateway-vibration-exceeds-critical-safety-thresholds") &&
                e.getEdgeType() == EdgeType.conditional));

        // 4. Gateway 2 branches to: (a) halt assembly line, (b) process completed
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-vibration-exceeds-critical-safety-thresholds") &&
                e.getTo().equals("activity-halt-assembly-line") &&
                e.getEdgeType() == EdgeType.conditional));

        // 5. CRUCIAL: remotely recalibrate sensor MUST NOT connect to halt assembly line
        assertFalse(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-remotely-recalibrate-sensor") &&
                e.getTo().equals("activity-halt-assembly-line")),
                "Mutually exclusive branches must not be linearly connected!");

        // 6. Maintenance sub-flow connects sequentially
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-halt-assembly-line") &&
                e.getTo().equals("activity-dispatch-maintenance-technician")));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-dispatch-maintenance-technician") &&
                e.getTo().equals("activity-replace-faulty-sensor")));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-replace-faulty-sensor") &&
                e.getTo().equals("activity-log-hardware-swap")));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-log-hardware-swap") &&
                e.getTo().equals("activity-restart-line")));

        // 7. Both branch terminal activities connect to End Event
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-remotely-recalibrate-sensor") &&
                e.getTo().equals("event-process-completed")));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-restart-line") &&
                e.getTo().equals("event-process-completed")));

        // 8. Validate Quality Score
        ProcessQualityValidator qualityValidator = new ProcessQualityValidator();
        ProcessQualityReportDTO report = qualityValidator.validateQuality(graph);
        assertTrue(report.qualityScore() < 100);
        assertFalse(report.issues().isEmpty(), "Expected quality issues for incomplete ownership/flow evidence");
        assertTrue(report.issues().stream().anyMatch(issue ->
                "ACTIVITY_OWNER_RULE".equals(issue.ruleId()) || "FLOW_COVERAGE_RULE".equals(issue.ruleId())));
    }
}
