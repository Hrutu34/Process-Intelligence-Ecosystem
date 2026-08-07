package com.pie.shared.dto;

import java.util.List;

public record ReviewReportDTO(
    String summary,
    List<String> issues,
    List<String> recommendations
) {}