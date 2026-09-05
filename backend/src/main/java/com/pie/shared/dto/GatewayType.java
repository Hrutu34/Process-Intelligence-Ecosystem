package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GatewayType {
    exclusive("exclusive"),
    parallel("parallel"),
    inclusive("inclusive");

    private final String value;

    GatewayType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static GatewayType fromString(String text) {
        if (text == null) {
            return null;
        }
        for (GatewayType type : GatewayType.values()) {
            if (type.value.equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown GatewayType: " + text);
    }
}
