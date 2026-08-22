package com.pie.shared.dto;

import java.util.List;

public record ProcessKnowledgeDTO(
    List<String> activities,
    List<String> actors,
    List<String> roles,
    List<String> systems,
    List<String> events,
    List<String> gateways,
    List<String> inputs,
    List<String> outputs,
    List<String> businessRules,
    List<String> risks,
    List<String> conflicts
) {}