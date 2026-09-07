package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BpmnGenerateRequestPayload(
    @JsonProperty("graph")
    ProcessGraphDTO graph,
    @JsonProperty("knowledge")
    ProcessKnowledgeDTO knowledge
) {
}

