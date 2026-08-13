package com.pie.shared.dto;

import java.util.List;

public record ProcessGraphDTO(
    List<NodeDTO> nodes,
    List<EdgeDTO> edges
) {
    public record NodeDTO(String id, String type, String label) {}
    public record EdgeDTO(String fromId, String toId, String condition) {}
}