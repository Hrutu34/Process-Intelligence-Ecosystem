package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The single graph contract shared by graph generation, validation, and API clients. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessGraphDTO {
    private String graphId;
    private List<GraphNode> nodes = new ArrayList<>();
    private List<GraphEdge> edges = new ArrayList<>();

    public ProcessGraphDTO() {
    }

    public ProcessGraphDTO(String graphId, List<GraphNode> nodes, List<GraphEdge> edges) {
        this.graphId = graphId;
        setNodes(nodes);
        setEdges(edges);
    }

    public String getGraphId() {
        return graphId;
    }

    public void setGraphId(String graphId) {
        this.graphId = graphId;
    }

    public List<GraphNode> getNodes() {
        return nodes == null ? Collections.emptyList() : Collections.unmodifiableList(nodes);
    }

    public void setNodes(List<GraphNode> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    }

    public List<GraphEdge> getEdges() {
        return edges == null ? Collections.emptyList() : Collections.unmodifiableList(edges);
    }

    public void setEdges(List<GraphEdge> edges) {
        this.edges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String graphId;
        private final List<GraphNode> nodes = new ArrayList<>();
        private final List<GraphEdge> edges = new ArrayList<>();

        public Builder graphId(String graphId) {
            this.graphId = graphId;
            return this;
        }

        public Builder addNode(GraphNode node) {
            if (node != null) nodes.add(node);
            return this;
        }

        public Builder addNodes(List<GraphNode> nodes) {
            if (nodes != null) this.nodes.addAll(nodes);
            return this;
        }

        public Builder addEdge(GraphEdge edge) {
            if (edge != null) edges.add(edge);
            return this;
        }

        public Builder addEdges(List<GraphEdge> edges) {
            if (edges != null) this.edges.addAll(edges);
            return this;
        }

        public ProcessGraphDTO build() {
            return new ProcessGraphDTO(graphId, nodes, edges);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProcessGraphDTO that)) return false;
        return Objects.equals(graphId, that.graphId)
                && Objects.equals(nodes, that.nodes)
                && Objects.equals(edges, that.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphId, nodes, edges);
    }
}