package com.pie.backend.service;

import com.pie.shared.bpmn.BpmnProcessModel;
import org.springframework.stereotype.Service;

@Service
public class BpmnXmlGenerationService {

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
            xml.append("    <bpmn:startEvent id=\"").append(escape(event.id())).append("\" name=\"")
                    .append(escape(event.name())).append("\" />\n");
        }
        for (BpmnProcessModel.IntermediateEvent event : model.intermediateEvents()) {
            String tag = event.type() == BpmnProcessModel.EventType.TIMER ? "intermediateCatchEvent" : "intermediateThrowEvent";
            xml.append("    <bpmn:").append(tag).append(" id=\"").append(escape(event.id())).append("\" name=\"")
                    .append(escape(event.name())).append("\">\n");
            if (event.type() == BpmnProcessModel.EventType.TIMER) {
                xml.append("      <bpmn:timerEventDefinition>\n")
                        .append("        <bpmn:timeDuration>").append(escape(event.duration() == null ? "P1D" : event.duration())).append("</bpmn:timeDuration>\n")
                        .append("      </bpmn:timerEventDefinition>\n");
            }
            xml.append("    </bpmn:").append(tag).append(">\n");
        }
        for (BpmnProcessModel.Task task : model.tasks()) {
            xml.append("    <bpmn:").append(taskTag(task.type())).append(" id=\"")
                    .append(escape(task.id())).append("\" name=\"").append(escape(task.name())).append("\" />\n");
        }
        for (BpmnProcessModel.Gateway gateway : model.gateways()) {
            xml.append("    <bpmn:").append(gatewayTag(gateway.type())).append(" id=\"")
                    .append(escape(gateway.id())).append("\" name=\"").append(escape(gateway.name())).append("\" />\n");
        }
        for (BpmnProcessModel.EndEvent event : model.endEvents()) {
            xml.append("    <bpmn:endEvent id=\"").append(escape(event.id())).append("\" name=\"")
                    .append(escape(event.name())).append("\" />\n");
        }
        for (BpmnProcessModel.SequenceFlow flow : model.sequenceFlows()) {
            xml.append("    <bpmn:sequenceFlow id=\"").append(escape(flow.id())).append("\" sourceRef=\"")
                    .append(escape(flow.sourceRef())).append("\" targetRef=\"").append(escape(flow.targetRef())).append("\"");
            if (flow.condition() != null && !flow.condition().isBlank()) {
                xml.append(" name=\"").append(escape(flow.condition())).append("\"");
            }
            xml.append(" />\n");
        }
        xml.append("  </bpmn:process>\n</bpmn:definitions>\n");
        return xml.toString();
    }

    private String taskTag(BpmnProcessModel.TaskType type) {
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
