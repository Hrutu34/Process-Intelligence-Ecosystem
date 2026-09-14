package com.pie.backend.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class BpmnParser {

    public static String parseToStructuredContext(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid BPMN XML: Empty or null");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Important: namespace awareness is often false by default, enabling it or just ignoring prefixes.
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(bpmnXml)));

            StringBuilder context = new StringBuilder();
            context.append("BPMN Process Structure:\n\n");

            // Extract Process ID/Name
            NodeList processNodes = doc.getElementsByTagNameNS("*", "process");
            if (processNodes.getLength() > 0) {
                Element process = (Element) processNodes.item(0);
                String processId = process.getAttribute("id");
                String processName = process.getAttribute("name");
                context.append("Process Name: ").append(processName.isEmpty() ? processId : processName).append("\n\n");
            }

            // Extract Participants (Lanes/Participants)
            List<String> roles = extractElementsByTagName(doc, "participant", "name");
            List<String> lanes = extractElementsByTagName(doc, "lane", "name");
            context.append("Participants / Roles:\n");
            for (String r : roles) { context.append("- ").append(r).append("\n"); }
            for (String l : lanes) { context.append("- ").append(l).append("\n"); }
            context.append("\n");

            // Extract Events
            List<String> startEvents = extractElementsByTagName(doc, "startEvent", "name", "id");
            List<String> endEvents = extractElementsByTagName(doc, "endEvent", "name", "id");
            context.append("Start Events:\n");
            for (String e : startEvents) { context.append("- ").append(e).append("\n"); }
            context.append("\nEnd Events:\n");
            for (String e : endEvents) { context.append("- ").append(e).append("\n"); }
            context.append("\n");

            // Extract Tasks
            List<String> tasks = extractElementsByTagName(doc, "task", "name");
            List<String> userTasks = extractElementsByTagName(doc, "userTask", "name");
            List<String> serviceTasks = extractElementsByTagName(doc, "serviceTask", "name");
            List<String> allTasks = new ArrayList<>();
            allTasks.addAll(tasks);
            allTasks.addAll(userTasks);
            allTasks.addAll(serviceTasks);
            context.append("Tasks:\n");
            for (String t : allTasks) { context.append("- ").append(t).append("\n"); }
            context.append("\n");

            // Extract Gateways
            List<String> gateways = extractElementsByTagName(doc, "exclusiveGateway", "name", "id");
            List<String> parallelGateways = extractElementsByTagName(doc, "parallelGateway", "name", "id");
            context.append("Gateways (Decisions/Splits):\n");
            for (String g : gateways) { context.append("- ").append(g).append(" (Exclusive)\n"); }
            for (String g : parallelGateways) { context.append("- ").append(g).append(" (Parallel)\n"); }
            context.append("\n");

            // Extract Flows (Sequence Flows)
            NodeList flowNodes = doc.getElementsByTagNameNS("*", "sequenceFlow");
            context.append("Sequence Flows:\n");
            for (int i = 0; i < flowNodes.getLength(); i++) {
                Element flow = (Element) flowNodes.item(i);
                String source = flow.getAttribute("sourceRef");
                String target = flow.getAttribute("targetRef");
                String name = flow.getAttribute("name");
                context.append("- ").append(source).append(" -> ").append(target);
                if (!name.isEmpty()) {
                    context.append(" [Condition: ").append(name).append("]");
                }
                context.append("\n");
            }
            
            return context.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid BPMN XML: Failed to parse. " + e.getMessage(), e);
        }
    }

    private static List<String> extractElementsByTagName(Document doc, String localName, String... attributeNames) {
        List<String> result = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String val = "";
            for (String attr : attributeNames) {
                val = el.getAttribute(attr);
                if (!val.isEmpty()) break;
            }
            if (val.isEmpty()) {
                val = "Unnamed " + localName + " (" + el.getAttribute("id") + ")";
            }
            result.add(val);
        }
        return result;
    }
}

