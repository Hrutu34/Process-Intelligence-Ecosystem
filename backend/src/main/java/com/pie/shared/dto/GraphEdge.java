package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphEdge {
    private String id;
    private String from;
    private String to;
    private EdgeType edgeType;
    private String label;
    private Double confidence;

    public GraphEdge() {
    }

    public GraphEdge(String id, String from, String to, EdgeType edgeType) {
        this(id, from, to, edgeType, null, null);
    }

    public GraphEdge(String id, String from, String to, EdgeType edgeType, String label) {
        this(id, from, to, edgeType, label, null);
    }

    public GraphEdge(String id, String from, String to, EdgeType edgeType, String label, Double confidence) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.edgeType = edgeType;
        this.label = label;
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public EdgeType getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(EdgeType edgeType) {
        this.edgeType = edgeType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String from;
        private String to;
        private EdgeType edgeType;
        private String label;
        private Double confidence;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder edgeType(EdgeType edgeType) {
            this.edgeType = edgeType;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public GraphEdge build() {
            return new GraphEdge(id, from, to, edgeType, label, confidence);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphEdge graphEdge)) return false;
        return Objects.equals(id, graphEdge.id) &&
               Objects.equals(from, graphEdge.from) &&
               Objects.equals(to, graphEdge.to) &&
               edgeType == graphEdge.edgeType &&
               Objects.equals(label, graphEdge.label) &&
               Objects.equals(confidence, graphEdge.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, from, to, edgeType, label, confidence);
    }

    @Override
    public String toString() {
        return "GraphEdge{" +
                "id='" + id + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", edgeType=" + edgeType +
                ", label='" + label + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
