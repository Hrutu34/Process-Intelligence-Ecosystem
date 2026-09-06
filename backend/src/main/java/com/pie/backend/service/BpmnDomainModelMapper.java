package com.pie.backend.service;

import com.pie.shared.bpmn.BpmnProcessModel;
import com.pie.shared.dto.EdgeType;
import com.pie.shared.dto.GraphEdge;
import com.pie.shared.dto.GraphNode;
import com.pie.shared.dto.NodeType;
import com.pie.shared.dto.ProcessGraphDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BpmnDomainModelMapper {

    public BpmnProcessModel map(ProcessGraphDTO graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Process graph cannot be null");
        }

        List<BpmnProcessModel.Task> tasks = new ArrayList<>();
        List<BpmnProcessModel.Gateway> gateways = new ArrayList<>();
        List<BpmnProcessModel.StartEvent> starts = new ArrayList<>();
        List<BpmnProcessModel.EndEvent> ends = new ArrayList<>();
        List<BpmnProcessModel.IntermediateEvent> intermediate = new ArrayList<>();
        List<BpmnProcessModel.Participant> participants = new ArrayList<>();
        List<BpmnProcessModel.SequenceFlow> flows = new ArrayList<>();

        for (GraphNode node : graph.getNodes()) {
            if (node.getType() == NodeType.Activity) {
                tasks.add(new BpmnProcessModel.Task(
                        node.getId(), node.getLabel(), inferTaskType(node.getLabel(), node), ownerId(node)));
            } else if (node.getType() == NodeType.Gateway) {
                gateways.add(new BpmnProcessModel.Gateway(
                        node.getId(), node.getLabel(), inferGatewayType(node)));
            } else if (node.getType() == NodeType.Event) {
                mapEvent(node, starts, ends, intermediate);
            } else if (node.getType() == NodeType.Role || node.getType() == NodeType.System) {
                participants.add(new BpmnProcessModel.Participant(node.getId(), node.getLabel()));
            }
        }

        for (GraphEdge edge : graph.getEdges()) {
            if (edge.getEdgeType() == EdgeType.sequence || edge.getEdgeType() == EdgeType.conditional) {
                flows.add(new BpmnProcessModel.SequenceFlow(
                        edge.getId(), edge.getFrom(), edge.getTo(), edge.getLabel()));
            }
        }

        List<BpmnProcessModel.Lane> lanes = participants.stream()
                .map(participant -> new BpmnProcessModel.Lane(participant.id(), participant.name(),
                        tasks.stream().filter(task -> participant.id().equals(task.ownerId())).map(BpmnProcessModel.Task::id).toList()))
                .toList();

        return new BpmnProcessModel(
                "Process_" + sanitize(graph.getGraphId()),
                graph.getGraphId().replaceFirst("^graph-", "").replace('-', ' '),
                lanes,
                participants,
                tasks,
                gateways,
                flows,
                starts,
                ends,
                intermediate,
                List.of());
    }

    private void mapEvent(GraphNode node, List<BpmnProcessModel.StartEvent> starts,
                          List<BpmnProcessModel.EndEvent> ends,
                          List<BpmnProcessModel.IntermediateEvent> intermediate) {
        if (node.getMetadata() != null && node.getMetadata().getEventType() != null) {
            switch (node.getMetadata().getEventType()) {
                case start -> starts.add(new BpmnProcessModel.StartEvent(node.getId(), node.getLabel()));
                case end -> ends.add(new BpmnProcessModel.EndEvent(node.getId(), node.getLabel()));
                default -> intermediate.add(new BpmnProcessModel.IntermediateEvent(
                        node.getId(), node.getLabel(), inferEventType(node), node.getMetadata().getDuration()));
            }
        }
    }

    private BpmnProcessModel.TaskType inferTaskType(String label, GraphNode node) {
        String value = label.toLowerCase(Locale.ROOT);
        Object explicit = node.getMetadata() == null ? null : node.getMetadata().getAttributes().get("taskType");
        if (explicit != null) {
            try {
                return BpmnProcessModel.TaskType.valueOf(explicit.toString().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                // Unknown metadata falls through to the stable lexical mapping.
            }
        }
        if (value.contains("send") || value.contains("notify") || value.contains("forward")) return BpmnProcessModel.TaskType.SEND;
        if (value.contains("validate") || value.contains("calculate") || value.contains("process") || value.contains("system")) return BpmnProcessModel.TaskType.SERVICE;
        if (value.contains("script") || value.contains("run ")) return BpmnProcessModel.TaskType.SCRIPT;
        if (value.contains("install") || value.contains("replace") || value.contains("repair")) return BpmnProcessModel.TaskType.MANUAL;
        if (value.contains("receive") || value.contains("wait for")) return BpmnProcessModel.TaskType.RECEIVE;
        return BpmnProcessModel.TaskType.USER;
    }

    private BpmnProcessModel.GatewayType inferGatewayType(GraphNode node) {
        if (node.getMetadata() != null && node.getMetadata().getGatewayType() != null) {
            return switch (node.getMetadata().getGatewayType()) {
                case parallel -> BpmnProcessModel.GatewayType.PARALLEL;
                case inclusive -> BpmnProcessModel.GatewayType.INCLUSIVE;
                default -> BpmnProcessModel.GatewayType.EXCLUSIVE;
            };
        }
        return BpmnProcessModel.GatewayType.EXCLUSIVE;
    }

    private BpmnProcessModel.EventType inferEventType(GraphNode node) {
        String label = node.getLabel().toLowerCase(Locale.ROOT);
        if (label.contains("message") || label.contains("receive") || label.contains("notification")) return BpmnProcessModel.EventType.MESSAGE;
        if (label.contains("timer") || label.contains("timeout") || label.contains("within") || node.getMetadata().getDuration() != null) return BpmnProcessModel.EventType.TIMER;
        if (label.contains("signal")) return BpmnProcessModel.EventType.SIGNAL;
        return BpmnProcessModel.EventType.GENERIC;
    }

    private String ownerId(GraphNode node) {
        if (node.getMetadata() == null) return null;
        return node.getMetadata().getRoleRef() != null ? node.getMetadata().getRoleRef() : node.getMetadata().getSystemRef();
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "process";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
