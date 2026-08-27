package com.pie.shared.dto;

import java.util.List;

public record ProcessQualityReportDTO(
    boolean valid,
    int qualityScore,
    List<ValidationIssueDTO> issues,
    List<String> recommendations
) {}
