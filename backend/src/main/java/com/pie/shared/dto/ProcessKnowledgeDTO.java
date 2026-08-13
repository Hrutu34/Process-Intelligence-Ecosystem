package com.pie.shared.dto;

import java.util.List;

public record ProcessKnowledgeDTO(
    List<String> activities,
    List<String> actors,
    List<String> systems,
    List<String> events,
    List<String> decisions,
    List<String> conflicts // NEW: Array for cross-document contradictions
) {}