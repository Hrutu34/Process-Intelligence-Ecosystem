package com.pie.backend.service;

import com.pie.backend.exception.InvalidProcessGraphException;
import com.pie.shared.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalProcessGraphBuilderTest {

    private CanonicalProcessGraphBuilder builder;
    private ProcessGraphValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProcessGraphValidator();
        builder = new CanonicalProcessGraphBuilder(validator);
    }

    @Test
    @DisplayName("Test 1 — Basic activities: creates Activity nodes, sequence edge, deterministic IDs")
    void test1_basicActivities() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Submit Travel Request", "Review Request"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of()
        );

        CanonicalProcessGraph graph = builder.build(input);

        assertNotNull(graph);
        assertNotNull(graph.getGraphId());
        assertEquals(2, graph.getNodes().size());

        // Verify Activity nodes
        GraphNode node1 = graph.getNodes().get(0);
        assertEquals("activity-submit-travel-request", node1.getId());
        assertEquals(NodeType.Activity, node1.getType());
        assertEquals("Submit Travel Request", node1.getLabel());

        GraphNode node2 = graph.getNodes().get(1);
        assertEquals("activity-review-request", node2.getId());
        assertEquals(NodeType.Activity, node2.getType());
        assertEquals("Review Request", node2.getLabel());

        // Verify Sequence Edge
        assertEquals(1, graph.getEdges().size());
        GraphEdge edge = graph.getEdges().get(0);
        assertEquals("edge-activity-submit-travel-request-activity-review-request-sequence", edge.getId());
        assertEquals("activity-submit-travel-request", edge.getFrom());
        assertEquals("activity-review-request", edge.getTo());
        assertEquals(EdgeType.sequence, edge.getEdgeType());
        assertNull(edge.getLabel());
    }

    @Test
    @DisplayName("Test 2 — Actors: creates Role nodes and association edges")
    void test2_actors() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Submit Travel Request"),
                List.of("Employee"),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of()
        );

        CanonicalProcessGraph graph = builder.build(input);

        assertNotNull(graph);
        assertEquals(2, graph.getNodes().size());

        GraphNode actNode = graph.getNodes().stream()
                .filter(n -> n.getType() == NodeType.Activity)
                .findFirst().orElseThrow();
        assertEquals("activity-submit-travel-request", actNode.getId());
        assertEquals("Submit Travel Request", actNode.getLabel());
        assertEquals("role-employee", actNode.getMetadata().getRoleRef());

        GraphNode roleNode = graph.getNodes().stream()
                .filter(n -> n.getType() == NodeType.Role)
                .findFirst().orElseThrow();
        assertEquals("role-employee", roleNode.getId());
        assertEquals("Employee", roleNode.getLabel());

        // Verify Association Edge
        assertEquals(1, graph.getEdges().size());
        GraphEdge edge = graph.getEdges().get(0);
        assertEquals("edge-role-employee-activity-submit-travel-request-association", edge.getId());
        assertEquals("role-employee", edge.getFrom());
        assertEquals("activity-submit-travel-request", edge.getTo());
        assertEquals(EdgeType.association, edge.getEdgeType());
    }

    @Test
    @DisplayName("Test 3 — Gateway: creates Gateway node with default exclusive type and sequence connection")
    void test3_gateway() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Review Request", "Validate Budget"),
                List.of(), List.of(), List.of(), List.of(),
                List.of("Manager Approval"),
                List.of(), List.of(), List.of(),
                List.of(), List.of()
        );

        CanonicalProcessGraph graph = builder.build(input);

        assertNotNull(graph);
        assertEquals(3, graph.getNodes().size());

        GraphNode gwNode = graph.getNodes().stream()
                .filter(n -> n.getType() == NodeType.Gateway)
                .findFirst().orElseThrow();
        assertEquals("gateway-manager-approval", gwNode.getId());
        assertEquals("Manager Approval", gwNode.getLabel());
        assertNotNull(gwNode.getMetadata());
        assertEquals(GatewayType.exclusive, gwNode.getMetadata().getGatewayType());

        // Check edges: Review Request -> Gateway (sequence), Gateway -> Validate Budget (conditional)
        assertEquals(2, graph.getEdges().size());

        GraphEdge seqEdge = graph.getEdges().stream()
                .filter(e -> e.getEdgeType() == EdgeType.sequence)
                .findFirst().orElseThrow();
        assertEquals("activity-review-request", seqEdge.getFrom());
        assertEquals("gateway-manager-approval", seqEdge.getTo());

        GraphEdge condEdge = graph.getEdges().stream()
                .filter(e -> e.getEdgeType() == EdgeType.conditional)
                .findFirst().orElseThrow();
        assertEquals("gateway-manager-approval", condEdge.getFrom());
        assertEquals("activity-validate-budget", condEdge.getTo());
    }

    @Test
    @DisplayName("Test 4 — Conditional flow: Gateway to Activity produces conditional edge with label")
    void test4_conditionalFlow() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Review Request", "Validate Budget"),
                List.of("Manager", "Finance"),
                List.of(), List.of(), List.of(),
                List.of("Manager Approval"),
                List.of(), List.of(),
                List.of("If approved, Finance validates budget"),
                List.of(), List.of()
        );

        CanonicalProcessGraph graph = builder.build(input);

        GraphEdge condEdge = graph.getEdges().stream()
                .filter(e -> e.getEdgeType() == EdgeType.conditional)
                .findFirst().orElseThrow();

        assertEquals("gateway-manager-approval", condEdge.getFrom());
        assertEquals("activity-validate-budget", condEdge.getTo());
        assertEquals(EdgeType.conditional, condEdge.getEdgeType());
        assertEquals("approved", condEdge.getLabel());
    }

    @Test
    @DisplayName("Test 5 — Full example: Complete Travel Request process graph topology")
    void test5_fullExample() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of(
                        "Submit Travel Request",
                        "Review Request",
                        "Validate Budget",
                        "Book Travel"
                ),
                List.of(
                        "Employee",
                        "Manager",
                        "Finance",
                        "Travel Desk"
                ),
                List.of(), List.of(), List.of(),
                List.of(
                        "Manager Approval"
                ),
                List.of(), List.of(),
                List.of(
                        "If approved, Finance validates budget"
                ),
                List.of(), List.of()
        );

        CanonicalProcessGraph graph = builder.build("graph-travel-request", input);

        assertEquals("graph-travel-request", graph.getGraphId());
        // 4 Activities + 4 Roles + 1 Gateway = 9 nodes
        assertEquals(9, graph.getNodes().size());

        // Verify Node IDs exist
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("activity-submit-travel-request")));
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("activity-review-request")));
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("activity-validate-budget")));
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("activity-book-travel")));

        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("role-employee")));
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("role-manager")));
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("role-finance")));
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("role-travel-desk")));

        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("gateway-manager-approval")));

        // 4 Associations + 3 Sequences + 1 Conditional = 8 edges
        assertEquals(8, graph.getEdges().size());

        // 1. Employee -> Submit Travel Request (association)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("role-employee") &&
                e.getTo().equals("activity-submit-travel-request") &&
                e.getEdgeType() == EdgeType.association));

        // 2. Manager -> Review Request (association)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("role-manager") &&
                e.getTo().equals("activity-review-request") &&
                e.getEdgeType() == EdgeType.association));

        // 3. Finance -> Validate Budget (association)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("role-finance") &&
                e.getTo().equals("activity-validate-budget") &&
                e.getEdgeType() == EdgeType.association));

        // 4. Travel Desk -> Book Travel (association)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("role-travel-desk") &&
                e.getTo().equals("activity-book-travel") &&
                e.getEdgeType() == EdgeType.association));

        // 5. Submit Travel Request -> Review Request (sequence)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-submit-travel-request") &&
                e.getTo().equals("activity-review-request") &&
                e.getEdgeType() == EdgeType.sequence));

        // 6. Review Request -> Manager Approval (sequence)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-review-request") &&
                e.getTo().equals("gateway-manager-approval") &&
                e.getEdgeType() == EdgeType.sequence));

        // 7. Manager Approval --approved--> Validate Budget (conditional)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("gateway-manager-approval") &&
                e.getTo().equals("activity-validate-budget") &&
                e.getEdgeType() == EdgeType.conditional &&
                "approved".equals(e.getLabel())));

        // 8. Validate Budget -> Book Travel (sequence)
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-validate-budget") &&
                e.getTo().equals("activity-book-travel") &&
                e.getEdgeType() == EdgeType.sequence));
    }

    @Test
    @DisplayName("Test 6 — Determinism: identical input yields identical graph IDs, node IDs, and edge IDs")
    void test6_determinism() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Submit Request", "Review Request", "Approve Request"),
                List.of("Employee", "Manager"),
                List.of(), List.of(), List.of(),
                List.of("Manager Approval"),
                List.of(), List.of(), List.of(),
                List.of(), List.of()
        );

        CanonicalProcessGraph graph1 = builder.build(input);
        CanonicalProcessGraph graph2 = builder.build(input);

        assertEquals(graph1.getGraphId(), graph2.getGraphId());
        assertEquals(graph1.getNodes().size(), graph2.getNodes().size());
        assertEquals(graph1.getEdges().size(), graph2.getEdges().size());

        for (int i = 0; i < graph1.getNodes().size(); i++) {
            GraphNode n1 = graph1.getNodes().get(i);
            GraphNode n2 = graph2.getNodes().get(i);
            assertEquals(n1.getId(), n2.getId());
            assertEquals(n1.getType(), n2.getType());
            assertEquals(n1.getLabel(), n2.getLabel());
        }

        for (int i = 0; i < graph1.getEdges().size(); i++) {
            GraphEdge e1 = graph1.getEdges().get(i);
            GraphEdge e2 = graph2.getEdges().get(i);
            assertEquals(e1.getId(), e2.getId());
            assertEquals(e1.getFrom(), e2.getFrom());
            assertEquals(e1.getTo(), e2.getTo());
            assertEquals(e1.getEdgeType(), e2.getEdgeType());
            assertEquals(e1.getLabel(), e2.getLabel());
        }
    }

    @Test
    @DisplayName("Test 7 — Duplicate handling: duplicate extracted entities are deduplicated")
    void test7_duplicateHandling() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Submit Request", "Submit Request", "Review Request"),
                List.of("Manager", "Manager", "Employee"),
                List.of(), List.of(), List.of(),
                List.of("Manager Approval", "Manager Approval"),
                List.of(), List.of(), List.of(),
                List.of(), List.of()
        );

        CanonicalProcessGraph graph = builder.build(input);

        // 2 distinct activities + 2 distinct roles + 1 distinct gateway = 5 nodes
        assertEquals(5, graph.getNodes().size());

        long activityCount = graph.getNodes().stream().filter(n -> n.getType() == NodeType.Activity).count();
        long roleCount = graph.getNodes().stream().filter(n -> n.getType() == NodeType.Role).count();
        long gatewayCount = graph.getNodes().stream().filter(n -> n.getType() == NodeType.Gateway).count();

        assertEquals(2, activityCount);
        assertEquals(2, roleCount);
        assertEquals(1, gatewayCount);
    }

    @Test
    @DisplayName("Test 8 — Extended entities: Systems, Events, and Data Artifacts")
    void test8_extendedEntities() {
        ProcessKnowledgeDTO input = new ProcessKnowledgeDTO(
                List.of("Submit Request via ERP", "Archive Document"),
                List.of("Staff"),
                List.of(),
                List.of("ERP"),
                List.of("Start Trigger", "Process Completed"),
                List.of(),
                List.of("Invoice Form"),
                List.of("Receipt PDF"),
                List.of(), List.of(), List.of()
        );

        CanonicalProcessGraph graph = builder.build(input);

        // Check Event nodes
        GraphNode startEvent = graph.getNodes().stream()
                .filter(n -> n.getId().equals("event-start-trigger"))
                .findFirst().orElseThrow();
        assertEquals(NodeType.Event, startEvent.getType());
        assertEquals(EventType.start, startEvent.getMetadata().getEventType());

        GraphNode endEvent = graph.getNodes().stream()
                .filter(n -> n.getId().equals("event-process-completed"))
                .findFirst().orElseThrow();
        assertEquals(NodeType.Event, endEvent.getType());
        assertEquals(EventType.end, endEvent.getMetadata().getEventType());

        // Check System node & association
        GraphNode systemNode = graph.getNodes().stream()
                .filter(n -> n.getId().equals("system-erp"))
                .findFirst().orElseThrow();
        assertEquals(NodeType.System, systemNode.getType());

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("system-erp") &&
                e.getTo().equals("activity-submit-request-via-erp") &&
                e.getEdgeType() == EdgeType.association));

        // Check Data Artifact nodes
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("data-invoice-form") && n.getType() == NodeType.DataArtifact));
        assertTrue(graph.getNodes().stream().anyMatch(n -> n.getId().equals("data-receipt-pdf") && n.getType() == NodeType.DataArtifact));

        // Check Event Flow: Start Event -> First Activity, Last Activity -> End Event
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("event-start-trigger") &&
                e.getTo().equals("activity-submit-request-via-erp") &&
                e.getEdgeType() == EdgeType.sequence));

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                e.getFrom().equals("activity-archive-document") &&
                e.getTo().equals("event-process-completed") &&
                e.getEdgeType() == EdgeType.sequence));
    }

    @Test
    @DisplayName("Test 9 — Validation & Error handling")
    void test9_validationErrors() {
        // Null knowledge
        assertThrows(InvalidProcessGraphException.class, () -> builder.build(null));

        // Empty knowledge
        ProcessKnowledgeDTO empty = new ProcessKnowledgeDTO(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        assertThrows(InvalidProcessGraphException.class, () -> builder.build(empty));

        // Graph with no ID
        CanonicalProcessGraph invalidGraphNoId = new CanonicalProcessGraph("", List.of(new GraphNode("1", NodeType.Activity, "A")), List.of());
        assertThrows(InvalidProcessGraphException.class, () -> validator.validateGraph(invalidGraphNoId));

        // Graph with dangling edge reference
        CanonicalProcessGraph invalidGraphDangling = new CanonicalProcessGraph(
                "g1",
                List.of(new GraphNode("act-1", NodeType.Activity, "A")),
                List.of(new GraphEdge("e1", "act-1", "non-existent-node", EdgeType.sequence))
        );
        assertThrows(InvalidProcessGraphException.class, () -> validator.validateGraph(invalidGraphDangling));

        // Graph with self-referencing edge
        CanonicalProcessGraph invalidGraphSelfRef = new CanonicalProcessGraph(
                "g2",
                List.of(new GraphNode("act-1", NodeType.Activity, "A")),
                List.of(new GraphEdge("e1", "act-1", "act-1", EdgeType.sequence))
        );
        assertThrows(InvalidProcessGraphException.class, () -> validator.validateGraph(invalidGraphSelfRef));
    }
}
