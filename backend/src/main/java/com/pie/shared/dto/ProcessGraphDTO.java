package com.pie.shared.dto;

import java.util.ArrayList;
import java.util.List;

public record ProcessGraphDTO(
    List<NodeDTO> nodes,
    List<EdgeDTO> edges
) {
    public record NodeDTO(String id, String type, String label) {}
    public record EdgeDTO(String fromId, String toId, String condition) {}

    public static ProcessGraphDTO fromCanonical(CanonicalProcessGraph canonical) {
        if (canonical == null) {
            return new ProcessGraphDTO(List.of(), List.of());
        }

        List<NodeDTO> nodeDTOs = new ArrayList<>();
        if (canonical.getNodes() != null) {
            for (GraphNode node : canonical.getNodes()) {
                nodeDTOs.add(new NodeDTO(
                        node.getId(),
                        node.getType() != null ? node.getType().getValue() : null,
                        node.getLabel()
                ));
            }
        }

        List<EdgeDTO> edgeDTOs = new ArrayList<>();
        if (canonical.getEdges() != null) {
            for (GraphEdge edge : canonical.getEdges()) {
                edgeDTOs.add(new EdgeDTO(
                        edge.getFrom(),
                        edge.getTo(),
                        edge.getLabel()
                ));
            }
        }

        return new ProcessGraphDTO(nodeDTOs, edgeDTOs);
    }

    public CanonicalProcessGraph toCanonical(String graphId) {
        List<GraphNode> canonicalNodes = new ArrayList<>();
        if (nodes != null) {
            for (NodeDTO n : nodes) {
                canonicalNodes.add(new GraphNode(
                        n.id(),
                        n.type() != null ? NodeType.fromString(n.type()) : null,
                        n.label()
                ));
            }
        }

        List<GraphEdge> canonicalEdges = new ArrayList<>();
        if (edges != null) {
            for (EdgeDTO e : edges) {
                EdgeType edgeType = e.condition() != null && !e.condition().isBlank()
                        ? EdgeType.conditional
                        : EdgeType.sequence;
                String edgeId = "edge-" + e.fromId() + "-" + e.toId() + "-" + edgeType.getValue();
                if (e.condition() != null && !e.condition().isBlank()) {
                    edgeId += "-" + e.condition().replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase();
                }
                canonicalEdges.add(new GraphEdge(
                        edgeId,
                        e.fromId(),
                        e.toId(),
                        edgeType,
                        e.condition()
                ));
            }
        }

        return new CanonicalProcessGraph(graphId, canonicalNodes, canonicalEdges);
    }
}