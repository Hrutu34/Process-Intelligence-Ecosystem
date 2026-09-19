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

    public record ElementMatch(String id, String name, String type) {}

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
            String opName = String.valueOf(op.getOrDefault("op", op.get("operation")));
            try {
                switch (opName) {
                    case "insert_element", "add_task", "create_element" -> insertElement(doc, processElem, op);
                    case "update_element", "rename_element" -> updateElement(doc, op);
                    case "delete_element" -> deleteElement(doc, processElem, op);
                    case "replace_element" -> replaceElement(doc, processElem, op);
                    case "move_element" -> moveElement(doc, processElem, op);
                    case "connect_elements", "connect" -> connectElements(doc, processElem, op);
                    case "disconnect_elements", "disconnect" -> disconnectElements(doc, op);
                    case "add_gateway" -> addGateway(doc, processElem, op);
                    case "add_event", "add_start_event", "add_end_event" -> addEvent(doc, processElem, op, opName);
                    case "add_condition" -> addCondition(doc, op);
                    case "change_all_tasks" -> changeAllTasks(doc, processElem, op);
                    case "add_subprocess", "create_subprocess" -> addSubprocess(doc, processElem, op);
                    default -> throw new IllegalArgumentException("Unknown operation: " + opName);
                }
                applied.add(opName);
            } catch (Exception e) {
                log.warn("Op {} failed: {}", opName, e.getMessage());
                failed.add(opName + ": " + e.getMessage());
            }
        }

        return new EditResult(serialize(doc), applied, failed);
    }

    // =========================================================================
    // ELEMENT RESOLVER
    // =========================================================================

    public Element resolveElement(Document doc, String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String query = identifier.trim();
        if (query.startsWith("#")) {
            query = query.substring(1).trim();
        }

        NodeList all = doc.getElementsByTagName("*");

        // 1. Exact ID match
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (query.equals(e.getAttribute("id"))) {
                return e;
            }
        }

        // 2. Exact Name match (case-sensitive)
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (query.equals(e.getAttribute("name"))) {
                return e;
            }
        }

        // 3. Case-insensitive Name match
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (query.equalsIgnoreCase(e.getAttribute("name"))) {
                return e;
            }
        }

        // 4. Substring / contains Name match
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        Element partialMatch = null;
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            String name = e.getAttribute("name");
            if (name != null && !name.isBlank()) {
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (lowerName.contains(lowerQuery) || lowerQuery.contains(lowerName)) {
                    if (partialMatch == null) {
                        partialMatch = e;
                    }
                }
            }
        }

        return partialMatch;
    }

    public List<ElementMatch> findMatchingElements(Document doc, String query) {
        List<ElementMatch> matches = new ArrayList<>();
        if (query == null || query.isBlank()) return matches;
        String q = query.trim().toLowerCase(Locale.ROOT);

        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            String tagName = e.getLocalName() != null ? e.getLocalName() : e.getTagName();
            if (tagName.contains("Flow") || tagName.contains("Diagram") || tagName.contains("Plane")
                    || tagName.contains("definitions") || tagName.contains("collaboration")) {
                continue;
            }

            String id = e.getAttribute("id");
            String name = e.getAttribute("name");
            boolean matchId = id != null && id.toLowerCase(Locale.ROOT).contains(q);
            boolean matchName = name != null && name.toLowerCase(Locale.ROOT).contains(q);

            if (matchId || matchName) {
                matches.add(new ElementMatch(id, name != null ? name : id, tagName));
            }
        }
        return matches;
    }

    private Element findFirstProcess(Document doc) {
        NodeList list = doc.getElementsByTagNameNS("*", "process");
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private String requireStr(Map<String, Object> op, String... keys) {
        for (String key : keys) {
            Object v = op.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        throw new IllegalArgumentException("Missing required parameter from: " + Arrays.toString(keys));
    }

    private String optStr(Map<String, Object> op, String def, String... keys) {
        for (String key : keys) {
            Object v = op.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return def;
    }

    private boolean optBool(Map<String, Object> op, boolean def, String... keys) {
        for (String key : keys) {
            Object v = op.get(key);
            if (v != null) {
                if (v instanceof Boolean b) return b;
                return Boolean.parseBoolean(v.toString().trim());
            }
        }
        return def;
    }

    private String uniqueId(Document doc, String prefix) {
        int i = 1;
        while (resolveElement(doc, prefix + "_" + i) != null) i++;
        return prefix + "_" + i;
    }

    // =========================================================================
    // OPERATIONS
    // =========================================================================

    private void insertElement(Document doc, Element processElem, Map<String, Object> op) {
        String name = requireStr(op, "name", "label");
        String rawType = optStr(op, "userTask", "type", "taskType", "elementType");
        String tagName = normalizeTaskTag(rawType);

        String id = optStr(op, uniqueId(doc, "task"), "id", "elementId");
        Element newElement = doc.createElementNS(BPMN_NS, "bpmn:" + tagName);
        newElement.setAttribute("id", id);
        newElement.setAttribute("name", name);
        processElem.appendChild(newElement);

        String afterQuery = optStr(op, null, "after", "afterElementId", "sourceId", "from");
        String beforeQuery = optStr(op, null, "before", "beforeElementId", "targetId", "to");

        if (afterQuery != null) {
            Element after = resolveElement(doc, afterQuery);
            if (after == null) throw new IllegalArgumentException("Target element to insert after not found: " + afterQuery);
            String afterId = after.getAttribute("id");

            // Check if 'before' is also specified (between insertion)
            if (beforeQuery != null) {
                Element before = resolveElement(doc, beforeQuery);
                if (before != null) {
                    String beforeId = before.getAttribute("id");
                    // Rewire flow between afterId and beforeId
                    Element existing = findSequenceFlow(doc, afterId, beforeId);
                    if (existing != null) {
                        existing.setAttribute("targetRef", id);
                        addSequenceFlow(doc, processElem, id, beforeId, null);
                        return;
                    }
                }
            }

            // Normal after insertion: rewire outgoing flow if single, or create new flow
            Element outgoingFlow = findFirstSequenceFlowFrom(doc, afterId);
            if (outgoingFlow != null) {
                String oldTarget = outgoingFlow.getAttribute("targetRef");
                outgoingFlow.setAttribute("targetRef", id);
                addSequenceFlow(doc, processElem, id, oldTarget, null);
            } else {
                addSequenceFlow(doc, processElem, afterId, id, null);
            }
        } else if (beforeQuery != null) {
            Element before = resolveElement(doc, beforeQuery);
            if (before == null) throw new IllegalArgumentException("Target element to insert before not found: " + beforeQuery);
            String beforeId = before.getAttribute("id");

            Element incomingFlow = findFirstSequenceFlowTo(doc, beforeId);
            if (incomingFlow != null) {
                incomingFlow.setAttribute("targetRef", id);
                addSequenceFlow(doc, processElem, id, beforeId, null);
            } else {
                addSequenceFlow(doc, processElem, id, beforeId, null);
            }
        }
    }

    private void updateElement(Document doc, Map<String, Object> op) {
        String targetQuery = requireStr(op, "elementId", "id", "name", "target");
        Element el = resolveElement(doc, targetQuery);
        if (el == null) throw new IllegalArgumentException("Element not found to update: " + targetQuery);

        String newName = optStr(op, null, "newName", "name", "label");
        if (newName != null) {
            el.setAttribute("name", newName);
        }

        String newType = optStr(op, null, "newType", "type", "taskType");
        if (newType != null) {
            replaceElementType(doc, el, newType);
        }
    }

    private void deleteElement(Document doc, Element processElem, Map<String, Object> op) {
        String targetQuery = requireStr(op, "elementId", "id", "name");
        Element el = resolveElement(doc, targetQuery);
        if (el == null) throw new IllegalArgumentException("Element not found to delete: " + targetQuery);
        String id = el.getAttribute("id");

        boolean reconnect = optBool(op, true, "reconnect", "autoReconnect");

        // Find incoming and outgoing sequence flows
        List<Element> incoming = findSequenceFlowsTo(doc, id);
        List<Element> outgoing = findSequenceFlowsFrom(doc, id);

        if (reconnect && !incoming.isEmpty() && !outgoing.isEmpty()) {
            // Reconnect first incoming to first outgoing target
            String newSource = incoming.get(0).getAttribute("sourceRef");
            String newTarget = outgoing.get(0).getAttribute("targetRef");
            String flowName = incoming.get(0).getAttribute("name");

            addSequenceFlow(doc, processElem, newSource, newTarget, flowName);
        }

        // Remove all flows touching this element
        for (Element f : incoming) {
            if (f.getParentNode() != null) f.getParentNode().removeChild(f);
        }
        for (Element f : outgoing) {
            if (f.getParentNode() != null) f.getParentNode().removeChild(f);
        }

        // Remove element itself
        if (el.getParentNode() != null) {
            el.getParentNode().removeChild(el);
        }

        removeDiRefs(doc, id);
    }

    private void replaceElement(Document doc, Element processElem, Map<String, Object> op) {
        String targetQuery = requireStr(op, "elementId", "id", "name");
        Element el = resolveElement(doc, targetQuery);
        if (el == null) throw new IllegalArgumentException("Element not found to replace: " + targetQuery);

        String newType = requireStr(op, "newType", "type");
        replaceElementType(doc, el, newType);
    }

    private void replaceElementType(Document doc, Element oldElement, String newType) {
        String newTagName = normalizeTag(newType);
        Element replacement = doc.createElementNS(BPMN_NS, "bpmn:" + newTagName);

        // Copy all attributes
        for (int i = 0; i < oldElement.getAttributes().getLength(); i++) {
            Node attr = oldElement.getAttributes().item(i);
            replacement.setAttribute(attr.getNodeName(), attr.getNodeValue());
        }

        // Replace node in DOM tree (flows reference the same ID so they remain intact)
        oldElement.getParentNode().replaceChild(replacement, oldElement);
    }

    private void moveElement(Document doc, Element processElem, Map<String, Object> op) {
        String targetQuery = requireStr(op, "elementId", "id", "name");
        Element el = resolveElement(doc, targetQuery);
        if (el == null) throw new IllegalArgumentException("Element not found to move: " + targetQuery);
        String id = el.getAttribute("id");

        String afterQuery = optStr(op, null, "after", "afterElementId");
        String beforeQuery = optStr(op, null, "before", "beforeElementId");

        if (afterQuery == null && beforeQuery == null) {
            throw new IllegalArgumentException("move_element requires 'after' or 'before' target");
        }

        // 1. Disconnect current position with reconnect between old neighbors
        List<Element> incoming = findSequenceFlowsTo(doc, id);
        List<Element> outgoing = findSequenceFlowsFrom(doc, id);

        if (!incoming.isEmpty() && !outgoing.isEmpty()) {
            String oldSource = incoming.get(0).getAttribute("sourceRef");
            String oldTarget = outgoing.get(0).getAttribute("targetRef");
            addSequenceFlow(doc, processElem, oldSource, oldTarget, null);
        }

        for (Element f : incoming) {
            if (f.getParentNode() != null) f.getParentNode().removeChild(f);
        }
        for (Element f : outgoing) {
            if (f.getParentNode() != null) f.getParentNode().removeChild(f);
        }

        // 2. Splice into new position
        if (afterQuery != null) {
            Element after = resolveElement(doc, afterQuery);
            if (after == null) throw new IllegalArgumentException("Target element 'after' not found: " + afterQuery);
            String afterId = after.getAttribute("id");

            Element outgoingFlow = findFirstSequenceFlowFrom(doc, afterId);
            if (outgoingFlow != null) {
                String oldTarget = outgoingFlow.getAttribute("targetRef");
                outgoingFlow.setAttribute("targetRef", id);
                addSequenceFlow(doc, processElem, id, oldTarget, null);
            } else {
                addSequenceFlow(doc, processElem, afterId, id, null);
            }
        } else {
            Element before = resolveElement(doc, beforeQuery);
            if (before == null) throw new IllegalArgumentException("Target element 'before' not found: " + beforeQuery);
            String beforeId = before.getAttribute("id");

            Element incomingFlow = findFirstSequenceFlowTo(doc, beforeId);
            if (incomingFlow != null) {
                incomingFlow.setAttribute("targetRef", id);
                addSequenceFlow(doc, processElem, id, beforeId, null);
            } else {
                addSequenceFlow(doc, processElem, id, beforeId, null);
            }
        }
    }

    private void connectElements(Document doc, Element processElem, Map<String, Object> op) {
        String fromQuery = requireStr(op, "from", "fromId", "source", "sourceRef");
        String toQuery = requireStr(op, "to", "toId", "target", "targetRef");
        String label = optStr(op, null, "label", "condition", "name");

        Element from = resolveElement(doc, fromQuery);
        if (from == null) throw new IllegalArgumentException("Source element not found: " + fromQuery);
        Element to = resolveElement(doc, toQuery);
        if (to == null) throw new IllegalArgumentException("Target element not found: " + toQuery);

        addSequenceFlow(doc, processElem, from.getAttribute("id"), to.getAttribute("id"), label);
    }

    private void disconnectElements(Document doc, Map<String, Object> op) {
        String fromQuery = requireStr(op, "from", "fromId", "source", "sourceRef");
        String toQuery = requireStr(op, "to", "toId", "target", "targetRef");

        Element from = resolveElement(doc, fromQuery);
        if (from == null) throw new IllegalArgumentException("Source element not found: " + fromQuery);
        Element to = resolveElement(doc, toQuery);
        if (to == null) throw new IllegalArgumentException("Target element not found: " + toQuery);

        Element flow = findSequenceFlow(doc, from.getAttribute("id"), to.getAttribute("id"));
        if (flow != null && flow.getParentNode() != null) {
            flow.getParentNode().removeChild(flow);
            removeDiRefs(doc, flow.getAttribute("id"));
        }
    }

    private void addGateway(Document doc, Element processElem, Map<String, Object> op) {
        String type = optStr(op, "exclusive", "gatewayType", "type").toLowerCase(Locale.ROOT);
        String name = optStr(op, "", "name", "label");
        String gwId = optStr(op, uniqueId(doc, "gw"), "id", "elementId");

        String tagName = switch (type) {
            case "parallel", "and" -> "parallelGateway";
            case "inclusive", "or" -> "inclusiveGateway";
            default -> "exclusiveGateway";
        };

        Element gateway = doc.createElementNS(BPMN_NS, "bpmn:" + tagName);
        gateway.setAttribute("id", gwId);
        if (!name.isBlank()) gateway.setAttribute("name", name);
        processElem.appendChild(gateway);

        String afterQuery = optStr(op, null, "after", "afterElementId");
        if (afterQuery != null) {
            Element after = resolveElement(doc, afterQuery);
            if (after == null) throw new IllegalArgumentException("Target element to insert gateway after not found: " + afterQuery);
            String afterId = after.getAttribute("id");

            Element existingFlow = findFirstSequenceFlowFrom(doc, afterId);
            if (existingFlow != null) {
                String oldTarget = existingFlow.getAttribute("targetRef");
                existingFlow.setAttribute("targetRef", gwId);
                addSequenceFlow(doc, processElem, gwId, oldTarget, null);
            } else {
                addSequenceFlow(doc, processElem, afterId, gwId, null);
            }
        }
    }

    private void addEvent(Document doc, Element processElem, Map<String, Object> op, String opType) {
        String name = optStr(op, "Event", "name", "label");
        String eventType = optStr(op, opType.contains("start") ? "start" : opType.contains("end") ? "end" : "intermediate", "eventType", "type");
        String eventId = optStr(op, uniqueId(doc, eventType), "id", "elementId");

        String tagName;
        if ("start".equalsIgnoreCase(eventType) || "add_start_event".equalsIgnoreCase(opType)) {
            tagName = "startEvent";
        } else if ("end".equalsIgnoreCase(eventType) || "add_end_event".equalsIgnoreCase(opType)) {
            tagName = "endEvent";
        } else if ("timer".equalsIgnoreCase(eventType)) {
            tagName = "intermediateCatchEvent";
        } else {
            tagName = "intermediateThrowEvent";
        }

        Element event = doc.createElementNS(BPMN_NS, "bpmn:" + tagName);
        event.setAttribute("id", eventId);
        event.setAttribute("name", name);

        // If timer event, add timerEventDefinition
        if ("timer".equalsIgnoreCase(eventType) || op.containsKey("duration")) {
            String duration = optStr(op, "PT24H", "duration", "timeout");
            Element timerDef = doc.createElementNS(BPMN_NS, "bpmn:timerEventDefinition");
            Element timeDuration = doc.createElementNS(BPMN_NS, "bpmn:timeDuration");
            timeDuration.setTextContent(duration);
            timerDef.appendChild(timeDuration);
            event.appendChild(timerDef);
        }

        processElem.appendChild(event);

        String afterQuery = optStr(op, null, "after", "afterElementId");
        String beforeQuery = optStr(op, null, "before", "beforeElementId");

        if (afterQuery != null) {
            Element after = resolveElement(doc, afterQuery);
            if (after != null) {
                addSequenceFlow(doc, processElem, after.getAttribute("id"), eventId, null);
            }
        } else if (beforeQuery != null) {
            Element before = resolveElement(doc, beforeQuery);
            if (before != null) {
                addSequenceFlow(doc, processElem, eventId, before.getAttribute("id"), null);
            }
        }
    }

    private void addCondition(Document doc, Map<String, Object> op) {
        String label = requireStr(op, "label", "condition", "name");
        String flowId = optStr(op, null, "flowId", "id");

        Element flow = null;
        if (flowId != null) {
            flow = resolveElement(doc, flowId);
        } else {
            String fromQuery = optStr(op, null, "from", "source");
            String toQuery = optStr(op, null, "to", "target");
            if (fromQuery != null && toQuery != null) {
                Element from = resolveElement(doc, fromQuery);
                Element to = resolveElement(doc, toQuery);
                if (from != null && to != null) {
                    flow = findSequenceFlow(doc, from.getAttribute("id"), to.getAttribute("id"));
                }
            }
        }

        if (flow == null) throw new IllegalArgumentException("Sequence flow not found to add condition");
        flow.setAttribute("name", label);
    }

    private void changeAllTasks(Document doc, Element processElem, Map<String, Object> op) {
        String toType = requireStr(op, "toType", "type", "targetType");
        String toTagName = normalizeTaskTag(toType);

        String[] taskTags = {"task", "userTask", "serviceTask", "manualTask", "scriptTask", "sendTask", "receiveTask", "businessRuleTask"};
        List<Element> tasksToChange = new ArrayList<>();

        for (String tag : taskTags) {
            NodeList list = doc.getElementsByTagNameNS("*", tag);
            for (int i = 0; i < list.getLength(); i++) {
                tasksToChange.add((Element) list.item(i));
            }
        }

        for (Element task : tasksToChange) {
            replaceElementType(doc, task, toTagName);
        }
    }

    private void addSubprocess(Document doc, Element processElem, Map<String, Object> op) {
        String name = optStr(op, "Subprocess", "name", "label");
        String subId = optStr(op, uniqueId(doc, "sub"), "id");

        Element subProcess = doc.createElementNS(BPMN_NS, "bpmn:subProcess");
        subProcess.setAttribute("id", subId);
        subProcess.setAttribute("name", name);
        processElem.appendChild(subProcess);

        String afterQuery = optStr(op, null, "after", "afterElementId");
        if (afterQuery != null) {
            Element after = resolveElement(doc, afterQuery);
            if (after != null) {
                addSequenceFlow(doc, processElem, after.getAttribute("id"), subId, null);
            }
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private String normalizeTaskTag(String rawType) {
        if (rawType == null) return "userTask";
        String lower = rawType.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (lower.contains("service")) return "serviceTask";
        if (lower.contains("manual")) return "manualTask";
        if (lower.contains("send")) return "sendTask";
        if (lower.contains("receive")) return "receiveTask";
        if (lower.contains("script")) return "scriptTask";
        if (lower.contains("rule")) return "businessRuleTask";
        if (lower.contains("user")) return "userTask";
        return "task";
    }

    private String normalizeTag(String rawType) {
        if (rawType == null) return "task";
        String lower = rawType.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (lower.contains("parallel")) return "parallelGateway";
        if (lower.contains("inclusive")) return "inclusiveGateway";
        if (lower.contains("exclusive") || lower.contains("xor")) return "exclusiveGateway";
        if (lower.contains("start")) return "startEvent";
        if (lower.contains("end")) return "endEvent";
        return normalizeTaskTag(rawType);
    }

    private Element findSequenceFlow(Document doc, String sourceId, String targetId) {
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (sourceId.equals(f.getAttribute("sourceRef")) && targetId.equals(f.getAttribute("targetRef"))) {
                return f;
            }
        }
        return null;
    }

    private Element findFirstSequenceFlowFrom(Document doc, String sourceId) {
        List<Element> list = findSequenceFlowsFrom(doc, sourceId);
        return list.isEmpty() ? null : list.get(0);
    }

    private Element findFirstSequenceFlowTo(Document doc, String targetId) {
        List<Element> list = findSequenceFlowsTo(doc, targetId);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<Element> findSequenceFlowsFrom(Document doc, String sourceId) {
        List<Element> result = new ArrayList<>();
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (sourceId.equals(f.getAttribute("sourceRef"))) {
                result.add(f);
            }
        }
        return result;
    }

    private List<Element> findSequenceFlowsTo(Document doc, String targetId) {
        List<Element> result = new ArrayList<>();
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (targetId.equals(f.getAttribute("targetRef"))) {
                result.add(f);
            }
        }
        return result;
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
        NodeList all = doc.getElementsByTagName("*");
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (bpmnElementId.equals(e.getAttribute("bpmnElement"))) {
                toRemove.add(e);
            }
        }
        for (Element e : toRemove) {
            if (e.getParentNode() != null) {
                e.getParentNode().removeChild(e);
            }
        }
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
