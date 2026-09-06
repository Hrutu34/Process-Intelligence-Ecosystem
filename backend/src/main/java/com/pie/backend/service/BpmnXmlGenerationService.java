package com.pie.backend.service;

import com.pie.shared.bpmn.BpmnProcessModel;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BpmnXmlGenerationService {

    private record Bounds(int x, int y, int width, int height) {}

    public String generate(BpmnProcessModel model) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ")
                .append("xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" ")
                .append("xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" ")
                .append("xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" ")
                .append("id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n")
                .append("  <bpmn:process id=\"").append(escape(model.id())).append("\" name=\"")
                .append(escape(model.name())).append("\" isExecutable=\"true\">\n");

        for (BpmnProcessModel.StartEvent event : model.startEvents()) {
            xml.append("    <bpmn:startEvent id=\"").append(escape(event.id())).append("\" name=\"").append(escape(event.name())).append("\" />\n");
        }
        for (BpmnProcessModel.IntermediateEvent event : model.intermediateEvents()) {
            String tag = event.type() == BpmnProcessModel.EventType.TIMER ? "intermediateCatchEvent" : "intermediateThrowEvent";
            xml.append("    <bpmn:").append(tag).append(" id=\"").append(escape(event.id())).append("\" name=\"").append(escape(event.name())).append("\">\n");
            if (event.type() == BpmnProcessModel.EventType.TIMER) {
                xml.append("      <bpmn:timerEventDefinition><bpmn:timeDuration>")
                   .append(escape(event.duration() == null ? "P1D" : event.duration()))
                   .append("</bpmn:timeDuration></bpmn:timerEventDefinition>\n");
            }
            xml.append("    </bpmn:").append(tag).append(">\n");
        }
        for (BpmnProcessModel.Task task : model.tasks()) {
            xml.append("    <bpmn:").append(taskTag(task.type())).append(" id=\"").append(escape(task.id())).append("\" name=\"").append(escape(task.name())).append("\" />\n");
        }
        for (BpmnProcessModel.Gateway gateway : model.gateways()) {
            xml.append("    <bpmn:").append(gatewayTag(gateway.type())).append(" id=\"").append(escape(gateway.id())).append("\" name=\"").append(escape(gateway.name())).append("\" />\n");
        }
        for (BpmnProcessModel.EndEvent event : model.endEvents()) {
            xml.append("    <bpmn:endEvent id=\"").append(escape(event.id())).append("\" name=\"").append(escape(event.name())).append("\" />\n");
        }
        for (BpmnProcessModel.SequenceFlow flow : model.sequenceFlows()) {
            xml.append("    <bpmn:sequenceFlow id=\"").append(escape(flow.id())).append("\" sourceRef=\"")
               .append(escape(flow.sourceRef())).append("\" targetRef=\"").append(escape(flow.targetRef())).append("\"");
            if (flow.condition() != null && !flow.condition().isBlank()) {
                xml.append(" name=\"").append(escape(flow.condition())).append("\"");
            }
            xml.append(" />\n");
        }
        xml.append("  </bpmn:process>\n");

        // Generate BPMNDI Diagram using Branch-Aware Topological Engine
        xml.append("  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n")
           .append("    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"").append(escape(model.id())).append("\">\n");

        Map<String, Bounds> boundsMap = calculateTopologicalLayout(model);

        for (Map.Entry<String, Bounds> entry : boundsMap.entrySet()) {
            Bounds b = entry.getValue();
            xml.append("      <bpmndi:BPMNShape id=\"").append(entry.getKey()).append("_di\" bpmnElement=\"").append(entry.getKey()).append("\">\n")
               .append("        <dc:Bounds x=\"").append(b.x).append("\" y=\"").append(b.y).append("\" width=\"").append(b.width).append("\" height=\"").append(b.height).append("\" />\n")
               .append("      </bpmndi:BPMNShape>\n");
        }

        Map<String, Integer> inEdgeCounter = new HashMap<>();
        Map<String, Integer> outEdgeCounter = new HashMap<>();

        for (BpmnProcessModel.SequenceFlow flow : model.sequenceFlows()) {
            Bounds source = boundsMap.get(flow.sourceRef());
            Bounds target = boundsMap.get(flow.targetRef());
            if (source == null || target == null) continue;

            int outIdx = outEdgeCounter.getOrDefault(flow.sourceRef(), 0);
            outEdgeCounter.put(flow.sourceRef(), outIdx + 1);
            int inIdx = inEdgeCounter.getOrDefault(flow.targetRef(), 0);
            inEdgeCounter.put(flow.targetRef(), inIdx + 1);

            int startX = source.x + source.width;
            int startY = source.y + (source.height / 2);
            int endX = target.x;
            int endY = target.y + (target.height / 2);

            xml.append("      <bpmndi:BPMNEdge id=\"").append(escape(flow.id())).append("_di\" bpmnElement=\"").append(escape(flow.id())).append("\">\n");

            if (startX < endX) { // Forward routing
                int midX = startX + (endX - startX) / 2 + (outIdx * 15);
                xml.append("        <di:waypoint x=\"").append(startX).append("\" y=\"").append(startY).append("\" />\n");
                if (Math.abs(startY - endY) > 5) {
                    xml.append("        <di:waypoint x=\"").append(midX).append("\" y=\"").append(startY).append("\" />\n");
                    xml.append("        <di:waypoint x=\"").append(midX).append("\" y=\"").append(endY).append("\" />\n");
                }
                xml.append("        <di:waypoint x=\"").append(endX).append("\" y=\"").append(endY).append("\" />\n");
            } else { // Loopback routing
                int loopY = Math.max(source.y + source.height, target.y + target.height) + 40 + (outIdx * 20);
                xml.append("        <di:waypoint x=\"").append(startX).append("\" y=\"").append(startY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(startX + 20).append("\" y=\"").append(startY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(startX + 20).append("\" y=\"").append(loopY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(endX - 20).append("\" y=\"").append(loopY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(endX - 20).append("\" y=\"").append(endY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(endX).append("\" y=\"").append(endY).append("\" />\n");
            }
            xml.append("      </bpmndi:BPMNEdge>\n");
        }

        xml.append("    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n</bpmn:definitions>\n");
        return xml.toString();
    }

    private Map<String, Bounds> calculateTopologicalLayout(BpmnProcessModel model) {
        Map<String, Bounds> boundsMap = new HashMap<>();
        Map<String, List<String>> outgoingMap = new HashMap<>();
        Map<String, List<String>> incomingMap = new HashMap<>();
        List<String> allIds = new ArrayList<>();

        Runnable initMaps = () -> {
            model.startEvents().forEach(e -> allIds.add(e.id()));
            model.tasks().forEach(t -> allIds.add(t.id()));
            model.gateways().forEach(g -> allIds.add(g.id()));
            model.intermediateEvents().forEach(e -> allIds.add(e.id()));
            model.endEvents().forEach(e -> allIds.add(e.id()));
        };
        initMaps.run();

        allIds.forEach(id -> {
            outgoingMap.put(id, new ArrayList<>());
            incomingMap.put(id, new ArrayList<>());
        });

        for (BpmnProcessModel.SequenceFlow flow : model.sequenceFlows()) {
            if (outgoingMap.containsKey(flow.sourceRef())) outgoingMap.get(flow.sourceRef()).add(flow.targetRef());
            if (incomingMap.containsKey(flow.targetRef())) incomingMap.get(flow.targetRef()).add(flow.sourceRef());
        }

        // 1. Calculate X-Depth
        Map<String, Integer> depthMap = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        for (BpmnProcessModel.StartEvent start : model.startEvents()) {
            depthMap.put(start.id(), 0);
            queue.add(start.id());
        }
        if (queue.isEmpty() && !allIds.isEmpty()) {
            depthMap.put(allIds.get(0), 0);
            queue.add(allIds.get(0));
        }

        int iter = 0;
        while (!queue.isEmpty() && iter < allIds.size() * 3) {
            iter++;
            String current = queue.poll();
            int currentDepth = depthMap.getOrDefault(current, 0);
            for (String next : outgoingMap.getOrDefault(current, List.of())) {
                if (currentDepth + 1 > depthMap.getOrDefault(next, -1)) {
                    depthMap.put(next, currentDepth + 1);
                    queue.add(next);
                }
            }
        }

        // 2. Calculate Y-Track (Branch Distribution)
        Map<String, Integer> trackMap = new HashMap<>();
        Set<String> visitedTrack = new HashSet<>();
        Queue<String> trackQueue = new LinkedList<>();

        for (BpmnProcessModel.StartEvent start : model.startEvents()) {
            trackMap.put(start.id(), 0);
            trackQueue.add(start.id());
        }
        if (trackQueue.isEmpty() && !allIds.isEmpty()) {
            trackMap.put(allIds.get(0), 0);
            trackQueue.add(allIds.get(0));
        }

        while (!trackQueue.isEmpty()) {
            String current = trackQueue.poll();
            if (!visitedTrack.add(current)) continue;
            int currentTrack = trackMap.getOrDefault(current, 0);
            List<String> children = outgoingMap.getOrDefault(current, List.of());

            if (children.size() == 1) {
                trackMap.putIfAbsent(children.get(0), currentTrack);
                trackQueue.add(children.get(0));
            } else if (children.size() > 1) {
                for (int i = 0; i < children.size(); i++) {
                    int offset = (i == 0) ? -1 : (i == 1 ? 1 : i);
                    trackMap.putIfAbsent(children.get(i), currentTrack + offset);
                    trackQueue.add(children.get(i));
                }
            }
        }

        // Force all End Events to re-center
        model.endEvents().forEach(e -> trackMap.put(e.id(), 0));

        // 3. Resolve Collisions and Assign Physical Bounds
        Set<String> occupiedSlots = new HashSet<>();
        int startX = 180;
        int centerY = 260;
        int colSpacing = 240;
        int rowSpacing = 160;

        for (String id : allIds) {
            int d = depthMap.getOrDefault(id, 0);
            int t = trackMap.getOrDefault(id, 0);

            while (occupiedSlots.contains(d + "_" + t)) {
                t++;
            }
            occupiedSlots.add(d + "_" + t);

            int width = 140, height = 80;
            int yOffset = 0;

            if (model.startEvents().stream().anyMatch(e -> e.id().equals(id)) ||
                model.endEvents().stream().anyMatch(e -> e.id().equals(id)) ||
                model.intermediateEvents().stream().anyMatch(e -> e.id().equals(id))) {
                width = 36; height = 36; yOffset = 22;
            } else if (model.gateways().stream().anyMatch(e -> e.id().equals(id))) {
                width = 50; height = 50; yOffset = 15;
            }

            boundsMap.put(id, new Bounds(startX + (d * colSpacing), (centerY + (t * rowSpacing)) + yOffset, width, height));
        }

        return boundsMap;
    }

    private String taskTag(BpmnProcessModel.TaskType type) {
        if (type == null) return "userTask";
        return switch (type) {
            case SERVICE -> "serviceTask";
            case MANUAL -> "manualTask";
            case SEND -> "sendTask";
            case RECEIVE -> "receiveTask";
            case SCRIPT -> "scriptTask";
            default -> "userTask";
        };
    }

    private String gatewayTag(BpmnProcessModel.GatewayType type) {
        if (type == null) return "exclusiveGateway";
        return switch (type) {
            case PARALLEL -> "parallelGateway";
            case INCLUSIVE -> "inclusiveGateway";
            default -> "exclusiveGateway";
        };
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}