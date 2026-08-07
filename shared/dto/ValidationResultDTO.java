package com.pie.shared.dto;

import java.util.List;

public record ValidationResultDTO(
    List<String> issues,
    List<String> warnings,
    List<String> recommendations
) {}