package com.pie.backend.service;

import com.pie.shared.bpmn.BpmnProcessModel;
import com.pie.shared.dto.EdgeType;
import com.pie.shared.dto.EventType;
import com.pie.shared.dto.GraphEdge;
import com.pie.shared.dto.GraphNode;
import com.pie.shared.dto.NodeMetadata;
import com.pie.shared.dto.NodeType;
import com.pie.shared.dto.ProcessGraphDTO;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnDomainModelMapperTest {

    @Test
    void mapsGraphEntitiesToBpmnDomainAndValidXml() throws Exception {
        ProcessGraphDTO graph = ProcessGraphDTO.builder()
                .graphId("graph-travel-approval")
                .addNodes(List.of(
                        GraphNode.builder().id("event-start").type(NodeType.Event).label("Request Received")
                                .metadata(NodeMetadata.builder().eventType(EventType.start).build()).build(),
                        GraphNode.builder().id("activity-submit").type(NodeType.Activity).label("Submit Request").build(),
                        GraphNode.builder().id("gateway-approved").type(NodeType.Gateway).label("Approved?").build(),
                        GraphNode.builder().id("event-end").type(NodeType.Event).label("Completed")
                                .metadata(NodeMetadata.builder().eventType(EventType.end).build()).build()
                ))
                .addEdges(List.of(
                        GraphEdge.builder().id("flow-1").from("event-start").to("activity-submit").edgeType(EdgeType.sequence).build(),
                        GraphEdge.builder().id("flow-2").from("activity-submit").to("gateway-approved").edgeType(EdgeType.sequence).build(),
                        GraphEdge.builder().id("flow-3").from("gateway-approved").to("event-end").edgeType(EdgeType.conditional).label("approved").build()
                ))
                .build();

        BpmnDomainModelMapper mapper = new BpmnDomainModelMapper();
        BpmnProcessModel model = mapper.map(graph);
        String xml = new BpmnXmlGenerationService().generate(model);
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, model.tasks().size());
        assertEquals(1, model.gateways().size());
        assertEquals(3, model.sequenceFlows().size());
        assertNotNull(document.getElementsByTagName("bpmn:process").item(0));
        assertTrue(xml.contains("bpmn:userTask"));
        assertTrue(xml.contains("bpmn:exclusiveGateway"));
        assertTrue(xml.contains("sourceRef=\"event-start\""));
    }
}
