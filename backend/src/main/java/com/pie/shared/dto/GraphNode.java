package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphNode {
    private String id;
    private NodeType type;
    private String label;
    private NodeMetadata metadata;

    public GraphNode() {
    }

    public GraphNode(String id, NodeType type, String label) {
        this(id, type, label, null);
    }

    public GraphNode(String id, NodeType type, String label, NodeMetadata metadata) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public NodeMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(NodeMetadata metadata) {
        this.metadata = metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private NodeType type;
        private String label;
        private NodeMetadata metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder type(String type) {
            this.type = type != null ? NodeType.fromString(type) : null;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder metadata(NodeMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public GraphNode build() {
            return new GraphNode(id, type, label, metadata);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphNode graphNode)) return false;
        return Objects.equals(id, graphNode.id) &&
               type == graphNode.type &&
               Objects.equals(label, graphNode.label) &&
               Objects.equals(metadata, graphNode.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, label, metadata);
    }

    @Override
    public String toString() {
        return "GraphNode{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", label='" + label + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
