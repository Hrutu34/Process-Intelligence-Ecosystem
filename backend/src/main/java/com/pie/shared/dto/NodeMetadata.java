package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeMetadata {
    private String roleRef;
    private String systemRef;
    private GatewayType gatewayType;
    private EventType eventType;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public NodeMetadata() {
    }

    public NodeMetadata(String roleRef, String systemRef, GatewayType gatewayType, EventType eventType, Map<String, Object> attributes) {
        this.roleRef = roleRef;
        this.systemRef = systemRef;
        this.gatewayType = gatewayType;
        this.eventType = eventType;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
    }

    public String getRoleRef() {
        return roleRef;
    }

    public void setRoleRef(String roleRef) {
        this.roleRef = roleRef;
    }

    public String getSystemRef() {
        return systemRef;
    }

    public void setSystemRef(String systemRef) {
        this.systemRef = systemRef;
    }

    public GatewayType getGatewayType() {
        return gatewayType;
    }

    public void setGatewayType(GatewayType gatewayType) {
        this.gatewayType = gatewayType;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
    }

    @JsonAnySetter
    public void setAttribute(String key, Object value) {
        if (this.attributes == null) {
            this.attributes = new LinkedHashMap<>();
        }
        this.attributes.put(key, value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String roleRef;
        private String systemRef;
        private GatewayType gatewayType;
        private EventType eventType;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        public Builder roleRef(String roleRef) {
            this.roleRef = roleRef;
            return this;
        }

        public Builder systemRef(String systemRef) {
            this.systemRef = systemRef;
            return this;
        }

        public Builder gatewayType(GatewayType gatewayType) {
            this.gatewayType = gatewayType;
            return this;
        }

        public Builder gatewayType(String gatewayType) {
            this.gatewayType = gatewayType != null ? GatewayType.fromString(gatewayType) : null;
            return this;
        }

        public Builder eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType != null ? EventType.fromString(eventType) : null;
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public NodeMetadata build() {
            return new NodeMetadata(roleRef, systemRef, gatewayType, eventType, attributes);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeMetadata that)) return false;
        return Objects.equals(roleRef, that.roleRef) &&
               Objects.equals(systemRef, that.systemRef) &&
               gatewayType == that.gatewayType &&
               eventType == that.eventType &&
               Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleRef, systemRef, gatewayType, eventType, attributes);
    }

    @Override
    public String toString() {
        return "NodeMetadata{" +
                "roleRef='" + roleRef + '\'' +
                ", systemRef='" + systemRef + '\'' +
                ", gatewayType=" + gatewayType +
                ", eventType=" + eventType +
                ", attributes=" + attributes +
                '}';
    }
}
