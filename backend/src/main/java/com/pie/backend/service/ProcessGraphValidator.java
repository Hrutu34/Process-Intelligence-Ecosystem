package com.pie.backend.service;

import com.pie.backend.exception.InvalidProcessGraphException;
import com.pie.shared.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ProcessGraphValidator {

    private static final Logger log = LoggerFactory.getLogger(ProcessGraphValidator.class);

    public void validateInput(ProcessKnowledgeDTO knowledge) {
        if (knowledge == null) {
            throw new InvalidProcessGraphException("Process knowledge cannot be null");
        }

        boolean hasActivities = knowledge.activities() != null && !knowledge.activities().isEmpty()
                && knowledge.activities().stream().anyMatch(a -> a != null && !a.isBlank());
        boolean hasActors = knowledge.actors() != null && !knowledge.actors().isEmpty()
                && knowledge.actors().stream().anyMatch(a -> a != null && !a.isBlank());
        boolean hasGateways = knowledge.gateways() != null && !knowledge.gateways().isEmpty()
                && knowledge.gateways().stream().anyMatch(g -> g != null && !g.isBlank());
        boolean hasEvents = knowledge.events() != null && !knowledge.events().isEmpty()
                && knowledge.events().stream().anyMatch(e -> e != null && !e.isBlank());

        if (!hasActivities && !hasActors && !hasGateways && !hasEvents) {
            throw new InvalidProcessGraphException("Process knowledge cannot be empty: at least one activity or process element is required");
        }

        if (!hasActivities && !hasEvents) {
            throw new InvalidProcessGraphException("Process knowledge must contain at least one activity or event to construct a valid process graph");
        }
    }

    public void validateGraph(ProcessGraphDTO graph) {
        if (graph == null) {
            throw new InvalidProcessGraphException("ProcessGraphDTO cannot be null");
        }

        if (graph.getGraphId() == null || graph.getGraphId().isBlank()) {
            throw new InvalidProcessGraphException("Graph ID cannot be null or blank");
        }

        List<GraphNode> nodes = graph.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new InvalidProcessGraphException("Graph must contain at least one node");
        }

        Set<String> nodeIds = new HashSet<>();
        for (GraphNode node : nodes) {
            if (node == null) {
                throw new InvalidProcessGraphException("Graph contains a null node");
            }
            if (node.getId() == null || node.getId().isBlank()) {
                throw new InvalidProcessGraphException("Node ID cannot be null or blank");
            }
            if (!nodeIds.add(node.getId())) {
                throw new InvalidProcessGraphException("Duplicate node ID detected: " + node.getId());
            }
            if (node.getType() == null) {
                throw new InvalidProcessGraphException("Node type cannot be null for node: " + node.getId());
            }
            if (node.getLabel() == null || node.getLabel().isBlank()) {
                throw new InvalidProcessGraphException("Node label cannot be null or blank for node: " + node.getId());
            }
        }

        List<GraphEdge> edges = graph.getEdges();
        if (edges != null) {
            Set<String> edgeIds = new HashSet<>();
            Set<String> edgeSignatures = new HashSet<>();

            for (GraphEdge edge : edges) {
                if (edge == null) {
                    throw new InvalidProcessGraphException("Graph contains a null edge");
                }
                if (edge.getId() == null || edge.getId().isBlank()) {
                    throw new InvalidProcessGraphException("Edge ID cannot be null or blank");
                }
                if (!edgeIds.add(edge.getId())) {
                    throw new InvalidProcessGraphException("Duplicate edge ID detected: " + edge.getId());
                }
                if (edge.getFrom() == null || edge.getFrom().isBlank()) {
                    throw new InvalidProcessGraphException("Edge 'from' cannot be null or blank in edge: " + edge.getId());
                }
                if (edge.getTo() == null || edge.getTo().isBlank()) {
                    throw new InvalidProcessGraphException("Edge 'to' cannot be null or blank in edge: " + edge.getId());
                }
                if (edge.getEdgeType() == null) {
                    throw new InvalidProcessGraphException("Edge type cannot be null for edge: " + edge.getId());
                }

                // Reference integrity: check that from and to point to existing nodes
                if (!nodeIds.contains(edge.getFrom())) {
                    throw new InvalidProcessGraphException("Edge references non-existent source node ID: " + edge.getFrom());
                }
                if (!nodeIds.contains(edge.getTo())) {
                    throw new InvalidProcessGraphException("Edge references non-existent target node ID: " + edge.getTo());
                }

                // Self-referencing check
                if (edge.getFrom().equals(edge.getTo())) {
                    throw new InvalidProcessGraphException("Self-referencing edge is invalid: " + edge.getFrom() + " -> " + edge.getTo());
                }

                // Check duplicate edge signature
                String signature = edge.getFrom() + "->" + edge.getTo() + ":" + edge.getEdgeType() + ":" + (edge.getLabel() != null ? edge.getLabel() : "");
                if (!edgeSignatures.add(signature)) {
                    throw new InvalidProcessGraphException("Duplicate edge detected: " + signature);
                }
            }
        }

        log.debug("Validated ProcessGraphDTO [{}]: {} nodes, {} edges",
                graph.getGraphId(), nodes.size(), edges != null ? edges.size() : 0);
    }
}
