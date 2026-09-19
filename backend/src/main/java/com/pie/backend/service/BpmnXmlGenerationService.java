package com.pie.backend.service;

import com.pie.shared.bpmn.BpmnProcessModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BpmnXmlGenerationService {

    // Standard BPMN DI dimensions and spacing.
    // Widened to reduce clutter — bpmn.io renders these as-is, so more air = less overlap.
    private static final int BASE_LANE_HEIGHT = 320;
    private static final int X_START = 180;
    private static final int X_SPACING = 280; // Horizontal distance between columns (was 220)
    private static final int Y_SPACING = 170; // Vertical distance between stacked parallel nodes (was 120)
    private static final int LANE_TOP_PADDING = 70;

    private static final int TASK_WIDTH = 120;
    private static final int TASK_HEIGHT = 80;
    private static final int GATEWAY_SIZE = 50;
    private static final int EVENT_SIZE = 36;

    private record Bounds(int x, int y, int width, int height) {}

    private static class LayoutContext {
        Map<String, Bounds> boundsMap = new HashMap<>();
        Map<String, Integer> laneHeights = new HashMap<>();
        Map<String, Integer> laneStartYs = new HashMap<>();
        int totalWidth = 1200;
        int totalHeight = 0;
    }

    public String generate(BpmnProcessModel model) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ")
                .append("xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" ")
                .append("xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" ")
                .append("xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" ")
                .append("id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n");

        boolean hasParticipants = model.participants() != null && !model.participants().isEmpty();
        List<BpmnProcessModel.Lane> activeLanes = hasParticipants && model.lanes() != null 
            ? model.lanes().stream().filter(l -> l.flowNodeIds() != null && !l.flowNodeIds().isEmpty()).collect(Collectors.toList())
            : List.of();

        if (hasParticipants) {
            xml.append("  <bpmn:collaboration id=\"Collaboration_1\">\n");
            xml.append("    <bpmn:participant id=\"Participant_Process\" name=\"Process Execution\" processRef=\"").append(escape(model.id())).append("\" />\n");
            xml.append("  </bpmn:collaboration>\n");
        }

        xml.append("  <bpmn:process id=\"").append(escape(model.id())).append("\" name=\"")
                .append(escape(model.name())).append("\" isExecutable=\"true\">\n");

        if (!activeLanes.isEmpty()) {
            xml.append("    <bpmn:laneSet id=\"LaneSet_1\">\n");
            for (BpmnProcessModel.Lane lane : activeLanes) {
                xml.append("      <bpmn:lane id=\"Lane_").append(escape(lane.id())).append("\" name=\"").append(escape(lane.name())).append("\">\n");
                for (String flowNodeId : lane.flowNodeIds()) {
                    xml.append("        <bpmn:flowNodeRef>").append(escape(flowNodeId)).append("</bpmn:flowNodeRef>\n");
                }
                xml.append("      </bpmn:lane>\n");
            }
            xml.append("    </bpmn:laneSet>\n");
        }

        // Write Flow Elements
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

        // --------------------------------------------------------
        // Generate BPMNDI Diagram using Grid-Collision Engine
        // --------------------------------------------------------
        xml.append("  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n")
           .append("    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"")
           .append(hasParticipants ? "Collaboration_1" : escape(model.id())).append("\">\n");

        LayoutContext ctx = calculateGridCollisionLayout(model, activeLanes);
        
        // Render Lane Shapes with DYNAMIC heights
        if (hasParticipants && !activeLanes.isEmpty()) {
            xml.append("      <bpmndi:BPMNShape id=\"Participant_Process_di\" bpmnElement=\"Participant_Process\" isHorizontal=\"true\">\n")
               .append("        <dc:Bounds x=\"50\" y=\"50\" width=\"").append(ctx.totalWidth).append("\" height=\"").append(ctx.totalHeight).append("\" />\n")
               .append("      </bpmndi:BPMNShape>\n");

            for (BpmnProcessModel.Lane lane : activeLanes) {
                int y = ctx.laneStartYs.get(lane.id());
                int h = ctx.laneHeights.get(lane.id());
                xml.append("      <bpmndi:BPMNShape id=\"Lane_").append(escape(lane.id())).append("_di\" bpmnElement=\"Lane_").append(escape(lane.id())).append("\" isHorizontal=\"true\">\n")
                   .append("        <dc:Bounds x=\"80\" y=\"").append(y).append("\" width=\"").append(ctx.totalWidth - 30).append("\" height=\"").append(h).append("\" />\n")
                   .append("      </bpmndi:BPMNShape>\n");
            }
        }

        // Render Node Shapes
        for (Map.Entry<String, Bounds> entry : ctx.boundsMap.entrySet()) {
            Bounds b = entry.getValue();
            xml.append("      <bpmndi:BPMNShape id=\"").append(entry.getKey()).append("_di\" bpmnElement=\"").append(entry.getKey()).append("\">\n")
               .append("        <dc:Bounds x=\"").append(b.x).append("\" y=\"").append(b.y).append("\" width=\"").append(b.width).append("\" height=\"").append(b.height).append("\" />\n")
               .append("      </bpmndi:BPMNShape>\n");
        }

        // --------------------------------------------------------
        // 🚦 Orthogonal Gutter Routing Engine
        // --------------------------------------------------------
        Map<String, Integer> outEdgeCounter = new HashMap<>();
        Map<String, Integer> inEdgeCounter = new HashMap<>();

        for (BpmnProcessModel.SequenceFlow flow : model.sequenceFlows()) {
            Bounds source = ctx.boundsMap.get(flow.sourceRef());
            Bounds target = ctx.boundsMap.get(flow.targetRef());
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
            xml.append("        <di:waypoint x=\"").append(startX).append("\" y=\"").append(startY).append("\" />\n");

            // Stagger constants (bigger = more air between parallel edges from the same node)
            int outStagger = 18 * outIdx;
            int inStagger = 18 * inIdx;

            if (startX < endX) { // Forward routing
                boolean isAdjacent = (endX - startX) <= X_SPACING;

                if (Math.abs(startY - endY) <= 5 && outIdx == 0 && inIdx == 0) {
                    // Perfect straight line (no other edges sharing endpoints)
                    xml.append("        <di:waypoint x=\"").append(endX).append("\" y=\"").append(endY).append("\" />\n");
                } else if (isAdjacent) {
                    // Adjacent columns: drop into the vertical gutter between them.
                    int gutterX = startX + Math.max(40, ((endX - startX) / 2) - 20 + outStagger);
                    if (gutterX >= endX - 10) gutterX = endX - 20;
                    xml.append("        <di:waypoint x=\"").append(gutterX).append("\" y=\"").append(startY).append("\" />\n");
                    xml.append("        <di:waypoint x=\"").append(gutterX).append("\" y=\"").append(endY).append("\" />\n");
                    xml.append("        <di:waypoint x=\"").append(endX).append("\" y=\"").append(endY).append("\" />\n");
                } else {
                    // Spanning multiple columns: 5-segment orthogonal route with staggered gutters.
                    int midX1 = startX + 40 + outStagger;                 // exit source gutter
                    int midX2 = endX - 40 - inStagger;                    // enter target gutter

                    // Push horizontal-gutter Y clear of task bounds (TASK_HEIGHT = 80, half = 40).
                    int gutterOffset = 80 + (outIdx * 12);
                    int safeY = startY < endY ? startY + gutterOffset : startY - gutterOffset;

                    xml.append("        <di:waypoint x=\"").append(midX1).append("\" y=\"").append(startY).append("\" />\n");
                    xml.append("        <di:waypoint x=\"").append(midX1).append("\" y=\"").append(safeY).append("\" />\n");
                    xml.append("        <di:waypoint x=\"").append(midX2).append("\" y=\"").append(safeY).append("\" />\n");
                    xml.append("        <di:waypoint x=\"").append(midX2).append("\" y=\"").append(endY).append("\" />\n");
                    xml.append("        <di:waypoint x=\"").append(endX).append("\" y=\"").append(endY).append("\" />\n");
                }
            } else { // Loopback routing (target sits to the left of source)
                int loopY = source.y + source.height + 40 + (outIdx * 20);
                int exitX = startX + 20 + outStagger;
                int entryX = endX - 20 - inStagger;
                xml.append("        <di:waypoint x=\"").append(exitX).append("\" y=\"").append(startY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(exitX).append("\" y=\"").append(loopY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(entryX).append("\" y=\"").append(loopY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(entryX).append("\" y=\"").append(endY).append("\" />\n")
                   .append("        <di:waypoint x=\"").append(endX).append("\" y=\"").append(endY).append("\" />\n");
            }

            xml.append("      </bpmndi:BPMNEdge>\n");
        }

        xml.append("    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n</bpmn:definitions>\n");
        return xml.toString();
    }

    private LayoutContext calculateGridCollisionLayout(BpmnProcessModel model, List<BpmnProcessModel.Lane> activeLanes) {
        LayoutContext ctx = new LayoutContext();
        Map<String, List<String>> outgoingMap = new HashMap<>();
        List<String> allIds = new ArrayList<>();

        model.startEvents().forEach(e -> allIds.add(e.id()));
        model.tasks().forEach(t -> allIds.add(t.id()));
        model.gateways().forEach(g -> allIds.add(g.id()));
        model.intermediateEvents().forEach(e -> allIds.add(e.id()));
        model.endEvents().forEach(e -> allIds.add(e.id()));

        allIds.forEach(id -> outgoingMap.put(id, new ArrayList<>()));
        for (BpmnProcessModel.SequenceFlow flow : model.sequenceFlows()) {
            if (outgoingMap.containsKey(flow.sourceRef())) outgoingMap.get(flow.sourceRef()).add(flow.targetRef());
        }

        // 1. Calculate Raw X-Depth using Cycle-Safe DFS
        Map<String, Integer> depthMap = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        
        for (BpmnProcessModel.StartEvent start : model.startEvents()) {
            calculateDepthDFS(start.id(), 0, outgoingMap, depthMap, visiting);
        }
        for (String id : allIds) { // Catch orphans
            if (!depthMap.containsKey(id)) {
                calculateDepthDFS(id, 0, outgoingMap, depthMap, visiting);
            }
        }

        // 2. Compress Depths to eliminate horizontal gaps
        Set<Integer> sortedDepths = new TreeSet<>(depthMap.values());
        Map<Integer, Integer> compressedDepthMap = new HashMap<>();
        int newDepth = 0;
        for (int d : sortedDepths) {
            compressedDepthMap.put(d, newDepth++);
        }
        for (Map.Entry<String, Integer> entry : depthMap.entrySet()) {
            depthMap.put(entry.getKey(), compressedDepthMap.get(entry.getValue()));
        }

        int maxDepth = compressedDepthMap.size() > 0 ? compressedDepthMap.size() - 1 : 0;
        ctx.totalWidth = Math.max(1400, X_START + (maxDepth * X_SPACING) + 400);

        // 3. Map Nodes to Lanes
        Map<String, String> nodeLaneMap = new HashMap<>();
        for (BpmnProcessModel.Lane lane : activeLanes) {
            for (String nodeId : lane.flowNodeIds()) {
                nodeLaneMap.put(nodeId, lane.id());
            }
        }

        // 4. Prevent Collisions: Count nodes at the same (Lane + Depth)
        Map<String, Map<Integer, Integer>> laneDepthCounts = new HashMap<>();
        Map<String, Integer> nodeStackIndexMap = new HashMap<>();

        for (String id : allIds) {
            int d = depthMap.getOrDefault(id, 0);
            String laneId = nodeLaneMap.getOrDefault(id, "default_lane");

            laneDepthCounts.putIfAbsent(laneId, new HashMap<>());
            int currentStackSize = laneDepthCounts.get(laneId).getOrDefault(d, 0);

            nodeStackIndexMap.put(id, currentStackSize);
            laneDepthCounts.get(laneId).put(d, currentStackSize + 1);
        }

        // 5. Calculate Dynamic Lane Heights
        int currentY = 50;
        if (!activeLanes.isEmpty()) {
            for (BpmnProcessModel.Lane lane : activeLanes) {
                int maxStackInLane = laneDepthCounts.containsKey(lane.id())
                    ? laneDepthCounts.get(lane.id()).values().stream().mapToInt(v -> v).max().orElse(1)
                    : 1;

                int requiredHeight = Math.max(BASE_LANE_HEIGHT,
                        (maxStackInLane * Y_SPACING) + LANE_TOP_PADDING + 40);

                ctx.laneHeights.put(lane.id(), requiredHeight);
                ctx.laneStartYs.put(lane.id(), currentY);
                currentY += requiredHeight;
            }
        } else {
            ctx.laneStartYs.put("default_lane", 50);
            currentY = 700;
        }
        ctx.totalHeight = currentY - 50;

        // 6. Assign Final Physical Coordinates. Center each shape vertically in its
        // slot regardless of shape type (task / gateway / event) so nodes in the same
        // stack column share the same visual baseline.
        for (String id : allIds) {
            int d = depthMap.getOrDefault(id, 0);
            int stackIndex = nodeStackIndexMap.getOrDefault(id, 0);
            String laneId = nodeLaneMap.getOrDefault(id, "default_lane");

            int laneY = ctx.laneStartYs.getOrDefault(laneId, 100);

            // Slot center — one row per stackIndex within the lane
            int x = X_START + (d * X_SPACING);
            int slotCenterY = laneY + LANE_TOP_PADDING + (stackIndex * Y_SPACING) + (TASK_HEIGHT / 2);

            int width = TASK_WIDTH, height = TASK_HEIGHT;

            if (model.startEvents().stream().anyMatch(e -> e.id().equals(id)) ||
                model.endEvents().stream().anyMatch(e -> e.id().equals(id)) ||
                model.intermediateEvents().stream().anyMatch(e -> e.id().equals(id))) {
                width = EVENT_SIZE; height = EVENT_SIZE;
            } else if (model.gateways().stream().anyMatch(e -> e.id().equals(id))) {
                width = GATEWAY_SIZE; height = GATEWAY_SIZE;
            }

            // Horizontally center smaller shapes within the task-width column
            int shapeX = x + ((TASK_WIDTH - width) / 2);
            int shapeY = slotCenterY - (height / 2);

            ctx.boundsMap.put(id, new Bounds(shapeX, shapeY, width, height));
        }

        return ctx;
    }

    private void calculateDepthDFS(String currentId, int depth, 
                                   Map<String, List<String>> outgoingMap, 
                                   Map<String, Integer> depthMap, 
                                   Set<String> visiting) {
        // Break infinite loops triggered by BPMN back-edges
        if (visiting.contains(currentId)) return; 
        
        depthMap.put(currentId, Math.max(depthMap.getOrDefault(currentId, 0), depth));
        
        visiting.add(currentId);
        for (String next : outgoingMap.getOrDefault(currentId, List.of())) {
            calculateDepthDFS(next, depthMap.get(currentId) + 1, outgoingMap, depthMap, visiting);
        }
        visiting.remove(currentId);
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