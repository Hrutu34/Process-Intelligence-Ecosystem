package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EventType {
    start("start"),
    intermediate("intermediate"),
    timer("timer"),
    end("end");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EventType fromString(String text) {
        if (text == null) {
            return null;
        }
        for (EventType type : EventType.values()) {
            if (type.value.equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EventType: " + text);
    }
}
