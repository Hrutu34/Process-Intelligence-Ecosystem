package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EdgeType {
    sequence("sequence"),
    conditional("conditional"),
    association("association");

    private final String value;

    EdgeType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EdgeType fromString(String text) {
        if (text == null) {
            return null;
        }
        for (EdgeType edgeType : EdgeType.values()) {
            if (edgeType.value.equalsIgnoreCase(text.trim())) {
                return edgeType;
            }
        }
        throw new IllegalArgumentException("Unknown EdgeType: " + text);
    }
}
