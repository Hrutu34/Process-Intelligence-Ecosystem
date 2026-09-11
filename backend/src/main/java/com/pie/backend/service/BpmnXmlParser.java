package com.pie.backend.service;

import com.pie.shared.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

@Service
public class BpmnXmlParser {

    private static final Logger log = LoggerFactory.getLogger(BpmnXmlParser.class);

    public record BpmnParseResult(
            CanonicalProcessGraph graph,
            ProcessKnowledgeDTO knowledge,
            String processName,
            String processId
    ) {}

    public BpmnParseResult parse(String xmlContent) throws Exception {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new IllegalArgumentException("BPMN XML content cannot be empty");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        Map<String, String> nodeToLaneMap = new HashMap<>();
        List<String> roleNames = new ArrayList<>();

        // 1. Parse Collaboration and Lanes (Roles & Ownership)
        NodeList laneNodes = doc.getElementsByTagNameNS("*", "lane");
        for (int i = 0; i < laneNodes.getLength(); i++) {
            Element laneElem = (Element) laneNodes.item(i);
            String laneId = laneElem.getAttribute("id");
            String laneName = laneElem.getAttribute("name");
            if (laneName == null || laneName.isBlank()) {
                laneName = "Role " + (i + 1);
            }
            roleNames.add(laneName);

            String roleNodeId = "role-" + slugify(laneName);
            if (!nodeMap.containsKey(roleNodeId)) {
                nodeMap.put(roleNodeId, GraphNode.builder()
                        .id(roleNodeId)
                        .type(NodeType.Role)
                        .label(laneName)
                        .metadata(NodeMetadata.builder().build())
                        .build());
            }

            // Extract flowNodeRef children
            NodeList flowRefs = laneElem.getElementsByTagNameNS("*", "flowNodeRef");
            for (int j = 0; j < flowRefs.getLength(); j++) {
                String refId = flowRefs.item(j).getTextContent().trim();
                if (!refId.isBlank()) {
                    nodeToLaneMap.put(refId, roleNodeId);
                }
            }
        }

        // 2. Identify Primary Process
        NodeList processNodes = doc.getElementsByTagNameNS("*", "process");
        String processId = "imported-bpmn";
        String processName = "Imported BPMN Process";

        if (processNodes.getLength() > 0) {
            Element procElem = (Element) processNodes.item(0);
            if (procElem.hasAttribute("id") && !procElem.getAttribute("id").isBlank()) {
                processId = procElem.getAttribute("id");
            }
            if (procElem.hasAttribute("name") && !procElem.getAttribute("name").isBlank()) {
                processName = procElem.getAttribute("name");
            }
        }

        // 3. Parse Start Events
        NodeList startEvents = doc.getElementsByTagNameNS("*", "startEvent");
        for (int i = 0; i < startEvents.getLength(); i++) {
            Element elem = (Element) startEvents.item(i);
            String id = elem.getAttribute("id");
            String name = elem.getAttribute("name");
            if (name == null || name.isBlank()) name = "Start";

            nodeMap.put(id, GraphNode.builder()
                    .id(id)
                    .type(NodeType.Event)
                    .label(name)
                    .metadata(NodeMetadata.builder().eventType(EventType.start).build())
                    .build());
        }

        // 4. Parse End Events
        NodeList endEvents = doc.getElementsByTagNameNS("*", "endEvent");
        for (int i = 0; i < endEvents.getLength(); i++) {
            Element elem = (Element) endEvents.item(i);
            String id = elem.getAttribute("id");
            String name = elem.getAttribute("name");
            if (name == null || name.isBlank()) name = "End";

            nodeMap.put(id, GraphNode.builder()
                    .id(id)
                    .type(NodeType.Event)
                    .label(name)
                    .metadata(NodeMetadata.builder().eventType(EventType.end).build())
                    .build());
        }

        // 5. Parse Intermediate Events
        parseIntermediateEvents(doc, "intermediateCatchEvent", nodeMap);
        parseIntermediateEvents(doc, "intermediateThrowEvent", nodeMap);

        // 6. Parse Tasks (task, userTask, serviceTask, manualTask, scriptTask, sendTask, receiveTask)
        String[] taskTags = {"task", "userTask", "serviceTask", "manualTask", "scriptTask", "sendTask", "receiveTask"};
        for (String tag : taskTags) {
            NodeList tasks = doc.getElementsByTagNameNS("*", tag);
            for (int i = 0; i < tasks.getLength(); i++) {
                Element elem = (Element) tasks.item(i);
                String id = elem.getAttribute("id");
                String name = elem.getAttribute("name");
                if (name == null || name.isBlank()) name = "Unnamed Activity";

                String roleRef = nodeToLaneMap.get(id);
                NodeMetadata meta = NodeMetadata.builder()
                        .roleRef(roleRef)
                        .build();

                nodeMap.put(id, GraphNode.builder()
                        .id(id)
                        .type(NodeType.Activity)
                        .label(name)
                        .metadata(meta)
                        .build());
            }
        }

        // 7. Parse Gateways (exclusiveGateway, parallelGateway, inclusiveGateway)
        NodeList exGateways = doc.getElementsByTagNameNS("*", "exclusiveGateway");
        for (int i = 0; i < exGateways.getLength(); i++) {
            Element elem = (Element) exGateways.item(i);
            String id = elem.getAttribute("id");
            String name = elem.getAttribute("name");
            nodeMap.put(id, GraphNode.builder()
                    .id(id)
                    .type(NodeType.Gateway)
                    .label(name != null ? name : "")
                    .metadata(NodeMetadata.builder().gatewayType(GatewayType.exclusive).build())
                    .build());
        }

        NodeList paGateways = doc.getElementsByTagNameNS("*", "parallelGateway");
        for (int i = 0; i < paGateways.getLength(); i++) {
            Element elem = (Element) paGateways.item(i);
            String id = elem.getAttribute("id");
            String name = elem.getAttribute("name");
            if (name == null || name.isBlank()) name = "Parallel Gateway";
            nodeMap.put(id, GraphNode.builder()
                    .id(id)
                    .type(NodeType.Gateway)
                    .label(name)
                    .metadata(NodeMetadata.builder().gatewayType(GatewayType.parallel).build())
                    .build());
        }

        NodeList inGateways = doc.getElementsByTagNameNS("*", "inclusiveGateway");
        for (int i = 0; i < inGateways.getLength(); i++) {
            Element elem = (Element) inGateways.item(i);
            String id = elem.getAttribute("id");
            String name = elem.getAttribute("name");
            if (name == null || name.isBlank()) name = "Inclusive Gateway";
            nodeMap.put(id, GraphNode.builder()
                    .id(id)
                    .type(NodeType.Gateway)
                    .label(name)
                    .metadata(NodeMetadata.builder().gatewayType(GatewayType.inclusive).build())
                    .build());
        }

        // 8. Parse Sequence Flows
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element elem = (Element) flows.item(i);
            String id = elem.getAttribute("id");
            String sourceRef = elem.getAttribute("sourceRef");
            String targetRef = elem.getAttribute("targetRef");
            String name = elem.getAttribute("name");

            if (sourceRef != null && targetRef != null && !sourceRef.isBlank() && !targetRef.isBlank()) {
                EdgeType edgeType = (name != null && !name.isBlank()) ? EdgeType.conditional : EdgeType.sequence;
                edges.add(GraphEdge.builder()
                        .id(id != null && !id.isBlank() ? id : "flow-" + (i + 1))
                        .from(sourceRef)
                        .to(targetRef)
                        .edgeType(edgeType)
                        .label(name != null && !name.isBlank() ? name : null)
                        .build());
            }
        }

        // 9. Link Roles to Activities via Association Edges
        for (Map.Entry<String, String> entry : nodeToLaneMap.entrySet()) {
            String activityId = entry.getKey();
            String roleId = entry.getValue();
            if (nodeMap.containsKey(activityId) && nodeMap.containsKey(roleId)) {
                edges.add(GraphEdge.builder()
                        .id("assoc-" + roleId + "-" + activityId)
                        .from(roleId)
                        .to(activityId)
                        .edgeType(EdgeType.association)
                        .label("Performs")
                        .build());
            }
        }

        CanonicalProcessGraph graph = CanonicalProcessGraph.builder()
                .graphId(processId)
                .addNodes(new ArrayList<>(nodeMap.values()))
                .addEdges(edges)
                .build();

        // 10. Synthesize ProcessKnowledgeDTO
        List<String> activities = nodeMap.values().stream()
                .filter(n -> n.getType() == NodeType.Activity)
                .map(GraphNode::getLabel)
                .toList();

        List<String> gateways = nodeMap.values().stream()
                .filter(n -> n.getType() == NodeType.Gateway)
                .map(GraphNode::getLabel)
                .filter(l -> l != null && !l.isBlank())
                .toList();

        List<String> events = nodeMap.values().stream()
                .filter(n -> n.getType() == NodeType.Event)
                .map(GraphNode::getLabel)
                .toList();

        ProcessKnowledgeDTO knowledge = new ProcessKnowledgeDTO(
                activities,
                roleNames,
                roleNames,
                List.of(),
                events,
                gateways,
                List.of("Source BPMN 2.0 Document"),
                List.of("Executed Process Instance"),
                List.of("Process conforms to BPMN 2.0 execution semantics"),
                List.of(),
                List.of()
        );

        log.info("Successfully parsed BPMN XML [{}]: {} nodes, {} edges", processId, nodeMap.size(), edges.size());

        return new BpmnParseResult(graph, knowledge, processName, processId);
    }

    private void parseIntermediateEvents(Document doc, String tagName, Map<String, GraphNode> nodeMap) {
        NodeList list = doc.getElementsByTagNameNS("*", tagName);
        for (int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            String id = elem.getAttribute("id");
            String name = elem.getAttribute("name");
            if (name == null || name.isBlank()) name = "Intermediate Event";

            nodeMap.put(id, GraphNode.builder()
                    .id(id)
                    .type(NodeType.Event)
                    .label(name)
                    .metadata(NodeMetadata.builder().eventType(EventType.intermediate).build())
                    .build());
        }
    }

    private String slugify(String input) {
        if (input == null) return "element";
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
