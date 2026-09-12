package com.pie.backend.service;

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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

@Service
public class BpmnEditService {

    private static final Logger log = LoggerFactory.getLogger(BpmnEditService.class);
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    public record EditResult(String xml, List<String> applied, List<String> failed) {}

    public EditResult applyOperations(String xml, List<Map<String, Object>> operations) throws Exception {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML is empty");
        }
        if (operations == null || operations.isEmpty()) {
            return new EditResult(xml, List.of(), List.of());
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        Element processElem = findFirstProcess(doc);
        if (processElem == null) {
            throw new IllegalStateException("No <process> element found in BPMN XML");
        }

        List<String> applied = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Map<String, Object> op : operations) {
            String opName = String.valueOf(op.get("op"));
            try {
                switch (opName) {
                    case "rename_element" -> renameElement(doc, op);
                    case "add_end_event" -> addEndEvent(doc, processElem, op);
                    case "add_start_event" -> addStartEvent(doc, processElem, op);
                    case "add_task" -> addTask(doc, processElem, op);
                    case "delete_element" -> deleteElement(doc, processElem, op);
                    case "connect" -> connect(doc, processElem, op);
                    case "add_gateway" -> addGateway(doc, processElem, op);
                    case "add_condition" -> addCondition(doc, op);
                    default -> throw new IllegalArgumentException("Unknown op: " + opName);
                }
                applied.add(opName);
            } catch (Exception e) {
                log.warn("Op {} failed: {}", opName, e.getMessage());
                failed.add(opName + ": " + e.getMessage());
            }
        }

        return new EditResult(serialize(doc), applied, failed);
    }

    private Element findFirstProcess(Document doc) {
        NodeList list = doc.getElementsByTagNameNS("*", "process");
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private Element findById(Document doc, String id) {
        if (id == null || id.isBlank()) return null;
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (id.equals(e.getAttribute("id"))) return e;
        }
        return null;
    }

    private String requireStr(Map<String, Object> op, String key) {
        Object v = op.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return v.toString();
    }

    private String optStr(Map<String, Object> op, String key, String def) {
        Object v = op.get(key);
        return (v == null || v.toString().isBlank()) ? def : v.toString();
    }

    private String uniqueId(Document doc, String prefix) {
        int i = 1;
        while (findById(doc, prefix + "_" + i) != null) i++;
        return prefix + "_" + i;
    }

    private void renameElement(Document doc, Map<String, Object> op) {
        String id = requireStr(op, "elementId");
        String newName = requireStr(op, "newName");
        Element el = findById(doc, id);
        if (el == null) throw new IllegalArgumentException("Element not found: " + id);
        el.setAttribute("name", newName);
    }

    private void addEndEvent(Document doc, Element processElem, Map<String, Object> op) {
        String afterId = requireStr(op, "afterElementId");
        String name = optStr(op, "name", "End");
        Element after = findById(doc, afterId);
        if (after == null) throw new IllegalArgumentException("afterElementId not found: " + afterId);

        String endId = optStr(op, "id", uniqueId(doc, "end"));
        Element endEvent = doc.createElementNS(BPMN_NS, "bpmn:endEvent");
        endEvent.setAttribute("id", endId);
        endEvent.setAttribute("name", name);
        processElem.appendChild(endEvent);

        addSequenceFlow(doc, processElem, afterId, endId, null);
    }

    private void addStartEvent(Document doc, Element processElem, Map<String, Object> op) {
        String beforeId = requireStr(op, "beforeElementId");
        String name = optStr(op, "name", "Start");
        Element before = findById(doc, beforeId);
        if (before == null) throw new IllegalArgumentException("beforeElementId not found: " + beforeId);

        String startId = optStr(op, "id", uniqueId(doc, "start"));
        Element startEvent = doc.createElementNS(BPMN_NS, "bpmn:startEvent");
        startEvent.setAttribute("id", startId);
        startEvent.setAttribute("name", name);
        Node firstChild = processElem.getFirstChild();
        if (firstChild != null) processElem.insertBefore(startEvent, firstChild);
        else processElem.appendChild(startEvent);

        addSequenceFlow(doc, processElem, startId, beforeId, null);
    }

    private void addTask(Document doc, Element processElem, Map<String, Object> op) {
        String afterId = requireStr(op, "afterElementId");
        String name = requireStr(op, "name");
        String taskId = optStr(op, "id", uniqueId(doc, "task"));
        Element after = findById(doc, afterId);
        if (after == null) throw new IllegalArgumentException("afterElementId not found: " + afterId);

        Element task = doc.createElementNS(BPMN_NS, "bpmn:task");
        task.setAttribute("id", taskId);
        task.setAttribute("name", name);
        processElem.appendChild(task);

        // If afterId has an outgoing sequence flow, rewire: afterId -> newTask -> oldTarget
        Element existingFlow = findFirstSequenceFlowFrom(doc, afterId);
        if (existingFlow != null) {
            String oldTarget = existingFlow.getAttribute("targetRef");
            existingFlow.setAttribute("targetRef", taskId);
            addSequenceFlow(doc, processElem, taskId, oldTarget, null);
        } else {
            addSequenceFlow(doc, processElem, afterId, taskId, null);
        }
    }

    private void deleteElement(Document doc, Element processElem, Map<String, Object> op) {
        String id = requireStr(op, "elementId");
        Element el = findById(doc, id);
        if (el == null) throw new IllegalArgumentException("Element not found: " + id);

        // Remove touching sequence flows
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (id.equals(f.getAttribute("sourceRef")) || id.equals(f.getAttribute("targetRef"))) {
                toRemove.add(f);
            }
        }
        for (Element f : toRemove) f.getParentNode().removeChild(f);

        // Remove element itself
        el.getParentNode().removeChild(el);

        // Best-effort: remove DI shape/edge referencing this id
        removeDiRefs(doc, id);
    }

    private void connect(Document doc, Element processElem, Map<String, Object> op) {
        String from = requireStr(op, "fromId");
        String to = requireStr(op, "toId");
        String label = optStr(op, "label", null);
        if (findById(doc, from) == null) throw new IllegalArgumentException("fromId not found: " + from);
        if (findById(doc, to) == null) throw new IllegalArgumentException("toId not found: " + to);
        addSequenceFlow(doc, processElem, from, to, label);
    }

    private void addGateway(Document doc, Element processElem, Map<String, Object> op) {
        String afterId = requireStr(op, "afterElementId");
        String type = optStr(op, "gatewayType", "exclusive").toLowerCase(Locale.ROOT);
        String name = optStr(op, "name", "");
        String gwId = optStr(op, "id", uniqueId(doc, "gw"));

        String tagName = switch (type) {
            case "parallel" -> "bpmn:parallelGateway";
            case "inclusive" -> "bpmn:inclusiveGateway";
            default -> "bpmn:exclusiveGateway";
        };

        Element gateway = doc.createElementNS(BPMN_NS, tagName);
        gateway.setAttribute("id", gwId);
        if (!name.isBlank()) gateway.setAttribute("name", name);
        processElem.appendChild(gateway);

        // Rewire like add_task
        Element existingFlow = findFirstSequenceFlowFrom(doc, afterId);
        if (existingFlow != null) {
            String oldTarget = existingFlow.getAttribute("targetRef");
            existingFlow.setAttribute("targetRef", gwId);
            addSequenceFlow(doc, processElem, gwId, oldTarget, null);
        } else {
            addSequenceFlow(doc, processElem, afterId, gwId, null);
        }
    }

    private void addCondition(Document doc, Map<String, Object> op) {
        String flowId = requireStr(op, "flowId");
        String label = requireStr(op, "label");
        Element flow = findById(doc, flowId);
        if (flow == null) throw new IllegalArgumentException("flowId not found: " + flowId);
        flow.setAttribute("name", label);
    }

    private Element findFirstSequenceFlowFrom(Document doc, String sourceId) {
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (sourceId.equals(f.getAttribute("sourceRef"))) return f;
        }
        return null;
    }

    private void addSequenceFlow(Document doc, Element processElem, String from, String to, String label) {
        String flowId = uniqueId(doc, "flow");
        Element flow = doc.createElementNS(BPMN_NS, "bpmn:sequenceFlow");
        flow.setAttribute("id", flowId);
        flow.setAttribute("sourceRef", from);
        flow.setAttribute("targetRef", to);
        if (label != null && !label.isBlank()) flow.setAttribute("name", label);
        processElem.appendChild(flow);
    }

    private void removeDiRefs(Document doc, String bpmnElementId) {
        // Remove BPMNShape / BPMNEdge whose bpmnElement matches this id
        NodeList all = doc.getElementsByTagName("*");
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (bpmnElementId.equals(e.getAttribute("bpmnElement"))) {
                toRemove.add(e);
            }
        }
        for (Element e : toRemove) e.getParentNode().removeChild(e);
    }

    private String serialize(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
