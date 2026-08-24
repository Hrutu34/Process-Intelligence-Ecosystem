package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NodeType {
    Activity("Activity"),
    Event("Event"),
    Gateway("Gateway"),
    Role("Role"),
    System("System"),
    DataArtifact("DataArtifact");

    private final String value;

    NodeType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NodeType fromString(String text) {
        if (text == null) {
            return null;
        }
        for (NodeType nodeType : NodeType.values()) {
            if (nodeType.value.equalsIgnoreCase(text.trim())) {
                return nodeType;
            }
        }
        throw new IllegalArgumentException("Unknown NodeType: " + text);
    }
}
