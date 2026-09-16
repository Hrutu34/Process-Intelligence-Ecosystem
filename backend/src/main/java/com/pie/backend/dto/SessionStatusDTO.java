package com.pie.backend.dto;

import java.util.List;

public record SessionStatusDTO(
    String sessionId,
    String currentStage,
    List<String> completedStages,
    String failedStage,
    String statusMessage,
    int progressPercentage
) {}

