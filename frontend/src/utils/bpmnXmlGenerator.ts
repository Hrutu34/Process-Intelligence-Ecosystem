import type {
  CanonicalProcessGraph,
  GraphNode,
  GraphEdge
} from '../../../../backend/src/main/java/com/pie/shared/types/dto';

interface ElementPosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

export function canonicalGraphToBpmnXml(graph: CanonicalProcessGraph): string {
  const processId = 'Process_' + (graph.graphId || '1').replace(/[^a-zA-Z0-9_]/g, '_');
  const processName = (graph.graphId || 'Canonical Process').replace(/^graph-/, '').replace(/-/g, ' ');

  const activityNodes = graph.nodes.filter((n) => n.type === 'Activity');
  const roleNodes = graph.nodes.filter((n) => n.type === 'Role');
  const gatewayNodes = graph.nodes.filter((n) => n.type === 'Gateway');
  const eventNodes = graph.nodes.filter((n) => n.type === 'Event');

  const sequenceAndConditionalEdges = graph.edges.filter(
    (e) => e.edgeType === 'sequence' || e.edgeType === 'conditional'
  );

  // Position layout calculations
  const positions = new Map<string, ElementPosition>();
  let currentX = 180;
  const centerY = 200;

  // 1. Start Event
  const startEvent = eventNodes.find((e) => e.metadata?.eventType === 'start');
  const startEventId = startEvent ? startEvent.id : 'event_start_process';
  const startEventLabel = startEvent ? startEvent.label : 'Start Process';
  positions.set(startEventId, { x: currentX, y: centerY - 18, width: 36, height: 36 });
  currentX += 100;

  // 2. Interleave Activities & Gateways based on sequence flow
  activityNodes.forEach((act) => {
    positions.set(act.id, { x: currentX, y: centerY - 40, width: 140, height: 80 });
    currentX += 200;

    // Check if this activity connects to a gateway
    const gwEdge = graph.edges.find((e) => e.from === act.id && e.to.startsWith('gateway-'));
    if (gwEdge) {
      const gw = gatewayNodes.find((g) => g.id === gwEdge.to);
      if (gw && !positions.has(gw.id)) {
        positions.set(gw.id, { x: currentX, y: centerY - 25, width: 50, height: 50 });
        currentX += 150;
      }
    }
  });

  // Position any remaining gateways
  gatewayNodes.forEach((gw) => {
    if (!positions.has(gw.id)) {
      positions.set(gw.id, { x: currentX, y: centerY - 25, width: 50, height: 50 });
      currentX += 150;
    }
  });

  // 3. End Event
  const endEvent = eventNodes.find((e) => e.metadata?.eventType === 'end');
  const endEventId = endEvent ? endEvent.id : 'event_end_process';
  const endEventLabel = endEvent ? endEvent.label : 'Process Completed';
  positions.set(endEventId, { x: currentX, y: centerY - 18, width: 36, height: 36 });

  // Generate sequence flows between nodes
  const flows: { id: string; from: string; to: string; label?: string }[] = [];

  // Start event to first node
  if (activityNodes.length > 0) {
    const firstTarget = activityNodes[0].id;
    flows.push({
      id: `Flow_start_${firstTarget}`,
      from: startEventId,
      to: firstTarget,
    });
  }

  // Canonical graph sequence and conditional edges
  sequenceAndConditionalEdges.forEach((e) => {
    flows.push({
      id: e.id.replace(/[^a-zA-Z0-9_]/g, '_'),
      from: e.from,
      to: e.to,
      label: e.label || undefined,
    });
  });

  // Last activity to End event (if not already connected)
  if (activityNodes.length > 0) {
    const lastActivity = activityNodes[activityNodes.length - 1];
    const alreadyConnectedToEnd = flows.some((f) => f.to === endEventId);
    if (!alreadyConnectedToEnd) {
      flows.push({
        id: `Flow_${lastActivity.id}_end`,
        from: lastActivity.id,
        to: endEventId,
      });
    }
  }

  // XML Builder
  let xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  id="Definitions_1"
                  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="${processId}" name="${escapeXml(processName)}" isExecutable="true">
`;

  // Start Event
  xml += `    <bpmn:startEvent id="${startEventId}" name="${escapeXml(startEventLabel)}" />\n`;

  // User Tasks / Activities
  activityNodes.forEach((act) => {
    const roleRef = act.metadata?.roleRef;
    const roleObj = roleNodes.find((r) => r.id === roleRef);
    const taskName = roleObj ? `${act.label} (${roleObj.label})` : act.label;
    xml += `    <bpmn:userTask id="${act.id}" name="${escapeXml(taskName)}" />\n`;
  });

  // Exclusive Gateways
  gatewayNodes.forEach((gw) => {
    xml += `    <bpmn:exclusiveGateway id="${gw.id}" name="${escapeXml(gw.label)}" />\n`;
  });

  // End Event
  xml += `    <bpmn:endEvent id="${endEventId}" name="${escapeXml(endEventLabel)}" />\n`;

  // Sequence Flows
  flows.forEach((flow) => {
    const nameAttr = flow.label ? ` name="${escapeXml(flow.label)}"` : '';
    xml += `    <bpmn:sequenceFlow id="${flow.id}" sourceRef="${flow.from}" targetRef="${flow.to}"${nameAttr} />\n`;
  });

  xml += `  </bpmn:process>\n`;

  // BPMN Diagram Interchange (BPMNDI)
  xml += `  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${processId}">\n`;

  // Shapes
  positions.forEach((pos, id) => {
    xml += `      <bpmndi:BPMNShape id="${id}_di" bpmnElement="${id}">
        <dc:Bounds x="${pos.x}" y="${pos.y}" width="${pos.width}" height="${pos.height}" />
      </bpmndi:BPMNShape>\n`;
  });

  // Edges (Waypoints)
  flows.forEach((flow) => {
    const fromPos = positions.get(flow.from);
    const toPos = positions.get(flow.to);
    if (fromPos && toPos) {
      const startWaypointX = fromPos.x + fromPos.width;
      const startWaypointY = fromPos.y + fromPos.height / 2;
      const endWaypointX = toPos.x;
      const endWaypointY = toPos.y + toPos.height / 2;

      xml += `      <bpmndi:BPMNEdge id="${flow.id}_di" bpmnElement="${flow.id}">
        <di:waypoint x="${startWaypointX}" y="${startWaypointY}" />
        <di:waypoint x="${endWaypointX}" y="${endWaypointY}" />
      </bpmndi:BPMNEdge>\n`;
    }
  });

  xml += `    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

  return xml;
}

function escapeXml(unsafe: string): string {
  return unsafe.replace(/[<>&'"]/g, (c) => {
    switch (c) {
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '&':
        return '&amp;';
      case '\'':
        return '&apos;';
      case '"':
        return '&quot;';
      default:
        return c;
    }
  });
}
