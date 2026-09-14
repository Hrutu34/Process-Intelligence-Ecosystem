package com.pie.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BpmnParserTest {

    @Test
    public void testValidBpmn_BasicTravelRequest() {
        String bpmn = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <bpmn:process id=\"TravelRequestProcess\" name=\"Travel Approval Process\">\n" +
                "    <bpmn:startEvent id=\"StartEvent_1\" name=\"Submit Travel Request\" />\n" +
                "    <bpmn:task id=\"Task_1\" name=\"Manager Review\" />\n" +
                "    <bpmn:exclusiveGateway id=\"Gateway_1\" name=\"Manager Approval\" />\n" +
                "    <bpmn:task id=\"Task_2\" name=\"Book Tickets\" />\n" +
                "    <bpmn:endEvent id=\"EndEvent_1\" name=\"Process Ended\" />\n" +
                "    <bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"Task_1\" />\n" +
                "  </bpmn:process>\n" +
                "</bpmn:definitions>";
                
        String result = BpmnParser.parseToStructuredContext(bpmn);
        
        assertTrue(result.contains("Process Name: Travel Approval Process"));
        assertTrue(result.contains("Submit Travel Request"));
        assertTrue(result.contains("Manager Review"));
        assertTrue(result.contains("Manager Approval (Exclusive)"));
        assertTrue(result.contains("Book Tickets"));
        assertTrue(result.contains("StartEvent_1 -> Task_1"));
    }

    @Test
    public void testValidBpmn_WithoutProcessName() {
        String bpmn = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <bpmn:process id=\"UnnamedProcess\">\n" +
                "    <bpmn:startEvent id=\"StartEvent_1\" />\n" +
                "    <bpmn:userTask id=\"Task_1\" name=\"Approve Request\" />\n" +
                "  </bpmn:process>\n" +
                "</bpmn:definitions>";
                
        String result = BpmnParser.parseToStructuredContext(bpmn);
        
        assertTrue(result.contains("Process Name: UnnamedProcess"));
        assertTrue(result.contains("Approve Request"));
    }

    @Test
    public void testValidBpmn_ComplexWithLanesAndParallel() {
        String bpmn = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <bpmn:process id=\"ComplexProcess\" name=\"Complex Process\">\n" +
                "    <bpmn:laneSet>\n" +
                "      <bpmn:lane id=\"Lane_1\" name=\"HR Department\" />\n" +
                "      <bpmn:lane id=\"Lane_2\" name=\"Finance\" />\n" +
                "    </bpmn:laneSet>\n" +
                "    <bpmn:startEvent id=\"StartEvent_1\" name=\"Employee Onboarding\" />\n" +
                "    <bpmn:parallelGateway id=\"Gateway_1\" name=\"Split Tasks\" />\n" +
                "    <bpmn:serviceTask id=\"Task_1\" name=\"Create IT Account\" />\n" +
                "  </bpmn:process>\n" +
                "</bpmn:definitions>";
                
        String result = BpmnParser.parseToStructuredContext(bpmn);
        
        assertTrue(result.contains("HR Department"));
        assertTrue(result.contains("Finance"));
        assertTrue(result.contains("Split Tasks (Parallel)"));
        assertTrue(result.contains("Create IT Account"));
    }

    @Test
    public void testInvalidBpmn_EmptyString() {
        assertThrows(IllegalArgumentException.class, () -> {
            BpmnParser.parseToStructuredContext("");
        });
    }

    @Test
    public void testInvalidBpmn_MalformedXml() {
        assertThrows(IllegalArgumentException.class, () -> {
            BpmnParser.parseToStructuredContext("<bpmn:definitions><unclosedTag>");
        });
    }
}

