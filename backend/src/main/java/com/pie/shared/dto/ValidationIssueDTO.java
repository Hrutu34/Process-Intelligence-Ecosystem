package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationIssueDTO(
    String ruleId,
    String severity,
    String elementId,
    String issue,
    String suggestion,
    Double confidence
) {
    public ValidationIssueDTO(String ruleId, String severity, String elementId,
                              String issue, String suggestion) {
        this(ruleId, severity, elementId, issue, suggestion, confidenceFor(severity));
    }

    private static double confidenceFor(String severity) {
        if ("HIGH".equalsIgnoreCase(severity)) return 0.95;
        if ("MEDIUM".equalsIgnoreCase(severity)) return 0.85;
        return 0.70;
    }
}
