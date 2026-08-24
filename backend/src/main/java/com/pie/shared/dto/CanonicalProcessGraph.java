package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanonicalProcessGraph {
    private String graphId;
    private List<GraphNode> nodes = new ArrayList<>();
    private List<GraphEdge> edges = new ArrayList<>();

    public CanonicalProcessGraph() {
    }

    public CanonicalProcessGraph(String graphId, List<GraphNode> nodes, List<GraphEdge> edges) {
        this.graphId = graphId;
        if (nodes != null) {
            this.nodes = new ArrayList<>(nodes);
        }
        if (edges != null) {
            this.edges = new ArrayList<>(edges);
        }
    }

    public String getGraphId() {
        return graphId;
    }

    public void setGraphId(String graphId) {
        this.graphId = graphId;
    }

    public List<GraphNode> getNodes() {
        return nodes != null ? Collections.unmodifiableList(nodes) : Collections.emptyList();
    }

    public void setNodes(List<GraphNode> nodes) {
        this.nodes = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
    }

    public List<GraphEdge> getEdges() {
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }

    public void setEdges(List<GraphEdge> edges) {
        this.edges = edges != null ? new ArrayList<>(edges) : new ArrayList<>();
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
            if (node != null) {
                this.nodes.add(node);
            }
            return this;
        }

        public Builder addNodes(List<GraphNode> nodes) {
            if (nodes != null) {
                this.nodes.addAll(nodes);
            }
            return this;
        }

        public Builder addEdge(GraphEdge edge) {
            if (edge != null) {
                this.edges.add(edge);
            }
            return this;
        }

        public Builder addEdges(List<GraphEdge> edges) {
            if (edges != null) {
                this.edges.addAll(edges);
            }
            return this;
        }

        public CanonicalProcessGraph build() {
            return new CanonicalProcessGraph(graphId, nodes, edges);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CanonicalProcessGraph that)) return false;
        return Objects.equals(graphId, that.graphId) &&
               Objects.equals(nodes, that.nodes) &&
               Objects.equals(edges, that.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphId, nodes, edges);
    }

    @Override
    public String toString() {
        return "CanonicalProcessGraph{" +
                "graphId='" + graphId + '\'' +
                ", nodes=" + nodes +
                ", edges=" + edges +
                '}';
    }
}
