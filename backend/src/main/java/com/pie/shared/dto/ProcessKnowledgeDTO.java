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
        List<String> conflicts,
        List<ProcessDocumentDTO> documents
) {
    /**
     * Backward-compatible constructor for existing tests and services 
     * that do not supply the 'documents' list.
     */
    public ProcessKnowledgeDTO(
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
            List<String> conflicts) {
        
        // Chains to the main constructor, defaulting 'documents' to an empty list
        this(activities, actors, roles, systems, events, gateways, 
             inputs, outputs, businessRules, risks, conflicts, List.of());
    }
}