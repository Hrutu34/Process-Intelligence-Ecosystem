package com.pie.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationIssueDTO(
    String ruleId,
    String severity,
    String elementId,
    String issue,
    String suggestion
) {}
