package com.pie.shared.dto;

import java.util.List;

public record ReviewReportDTO(
    String processName,
    String summary,
    List<String> participants,
    List<ProcessStep> steps,
    List<ProcessDecision> decisions
) {
    public record ProcessStep(
        int sequence,
        String name,
        String description
    ) {}

    public record ProcessDecision(
        String name,
        String description
    ) {}
}

