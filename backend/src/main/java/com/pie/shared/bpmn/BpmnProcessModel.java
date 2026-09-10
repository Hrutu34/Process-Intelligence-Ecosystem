package com.pie.shared.bpmn;

import java.util.List;

public record BpmnProcessModel(
        String id,
        String name,
        List<Lane> lanes,
        List<Participant> participants,
        List<Task> tasks,
        List<Gateway> gateways,
        List<SequenceFlow> sequenceFlows,
        List<StartEvent> startEvents,
        List<EndEvent> endEvents,
        List<IntermediateEvent> intermediateEvents,
        List<MessageFlow> messageFlows) {

    public BpmnProcessModel {
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
        participants = participants == null ? List.of() : List.copyOf(participants);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        gateways = gateways == null ? List.of() : List.copyOf(gateways);
        sequenceFlows = sequenceFlows == null ? List.of() : List.copyOf(sequenceFlows);
        startEvents = startEvents == null ? List.of() : List.copyOf(startEvents);
        endEvents = endEvents == null ? List.of() : List.copyOf(endEvents);
        intermediateEvents = intermediateEvents == null ? List.of() : List.copyOf(intermediateEvents);
        messageFlows = messageFlows == null ? List.of() : List.copyOf(messageFlows);
    }

    public record Lane(String id, String name, List<String> flowNodeIds) {
        public Lane {
            flowNodeIds = flowNodeIds == null ? List.of() : List.copyOf(flowNodeIds);
        }
    }

    public record Participant(String id, String name) {}

    public record Task(String id, String name, TaskType type, String ownerId) {}

    public record Gateway(String id, String name, GatewayType type) {}

    public record SequenceFlow(String id, String sourceRef, String targetRef, String condition) {}

    public record StartEvent(String id, String name) {}

    public record EndEvent(String id, String name) {}

    public record IntermediateEvent(String id, String name, EventType type, String duration) {}

    public record MessageFlow(String id, String sourceRef, String targetRef, String name) {}

    public enum TaskType {
        USER, SERVICE, MANUAL, SEND, RECEIVE, SCRIPT
    }

    public enum GatewayType {
        EXCLUSIVE, PARALLEL, INCLUSIVE
    }

    public enum EventType {
        MESSAGE, TIMER, SIGNAL, GENERIC
    }
}
