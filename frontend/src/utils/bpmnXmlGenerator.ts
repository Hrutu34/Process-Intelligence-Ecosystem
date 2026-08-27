import type {
  CanonicalProcessGraph,
  GraphNode,
  GraphEdge,
} from '../../../../backend/src/main/java/com/pie/shared/types/dto';

interface ElementPosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface FlowElement {
  id: string;
  from: string;
  to: string;
  label?: string;
}

function sanitizeId(id: string): string {
  if (!id) return 'element_' + Math.random().toString(36).substring(2, 8);
  const sanitized = id.replace(/[^a-zA-Z0-9_-]/g, '_');
  return /^[0-9]/.test(sanitized) ? `node_${sanitized}` : sanitized;
}

function escapeXml(unsafe: string): string {
  if (!unsafe) return '';
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

export function canonicalGraphToBpmnXml(graph: CanonicalProcessGraph): string {
  const rawGraphId = graph.graphId || 'canonical_process';
  const processId = 'Process_' + sanitizeId(rawGraphId);
  const processName = rawGraphId.replace(/^graph-/, '').replace(/[-_]/g, ' ');

  const nodes = graph.nodes || [];
  const edges = graph.edges || [];

  const activityNodes = nodes.filter((n) => n.type === 'Activity');
  const roleNodes = nodes.filter((n) => n.type === 'Role');
  const gatewayNodes = nodes.filter((n) => n.type === 'Gateway');
  const eventNodes = nodes.filter((n) => n.type === 'Event');

  // Identify Start, End, and Intermediate Events
  let startEvents = eventNodes.filter((e) => e.metadata?.eventType === 'start');
  let endEvents = eventNodes.filter((e) => e.metadata?.eventType === 'end');
  const intermediateEvents = eventNodes.filter(
    (e) => e.metadata?.eventType !== 'start' && e.metadata?.eventType !== 'end'
  );

  // If no start event exists, create a virtual one
  if (startEvents.length === 0 && (activityNodes.length > 0 || eventNodes.length === 0)) {
    const virtualStart: GraphNode = {
      id: 'event_start_process',
      type: 'Event',
      label: 'Start Process',
      metadata: { eventType: 'start' },
    };
    startEvents = [virtualStart];
  }

  // If no end event exists, create a virtual one
  if (endEvents.length === 0 && (activityNodes.length > 0 || eventNodes.length === 0)) {
    const virtualEnd: GraphNode = {
      id: 'event_end_process',
      type: 'Event',
      label: 'Process Completed',
      metadata: { eventType: 'end' },
    };
    endEvents = [virtualEnd];
  }

  // Position layout calculations
  const positions = new Map<string, ElementPosition>();
  let currentX = 180;
  const centerY = 200;

  // 1. Position Start Events
  startEvents.forEach((ev) => {
    const sId = sanitizeId(ev.id);
    positions.set(sId, { x: currentX, y: centerY - 18, width: 36, height: 36 });
    currentX += 100;
  });

  // 2. Position Activities, Gateways, and Intermediate Events
  const flowNodesOrdered: GraphNode[] = [];

  activityNodes.forEach((act) => {
    flowNodesOrdered.push(act);
    const actId = sanitizeId(act.id);
    positions.set(actId, { x: currentX, y: centerY - 40, width: 140, height: 80 });
    currentX += 200;

    // Check if this activity connects to a gateway
    const gwEdge = edges.find((e) => e.from === act.id && (e.to.startsWith('gateway-') || e.to.includes('gateway')));
    if (gwEdge) {
      const gw = gatewayNodes.find((g) => g.id === gwEdge.to);
      if (gw) {
        const gwId = sanitizeId(gw.id);
        if (!positions.has(gwId)) {
          flowNodesOrdered.push(gw);
          positions.set(gwId, { x: currentX, y: centerY - 25, width: 50, height: 50 });
          currentX += 150;
        }
      }
    }
  });

  // Position any remaining Gateways
  gatewayNodes.forEach((gw) => {
    const gwId = sanitizeId(gw.id);
    if (!positions.has(gwId)) {
      flowNodesOrdered.push(gw);
      positions.set(gwId, { x: currentX, y: centerY - 25, width: 50, height: 50 });
      currentX += 150;
    }
  });

  // Position Intermediate Events
  intermediateEvents.forEach((ev) => {
    const evId = sanitizeId(ev.id);
    if (!positions.has(evId)) {
      flowNodesOrdered.push(ev);
      positions.set(evId, { x: currentX, y: centerY - 18, width: 36, height: 36 });
      currentX += 120;
    }
  });

  // 3. Position End Events
  endEvents.forEach((ev) => {
    const eId = sanitizeId(ev.id);
    positions.set(eId, { x: currentX, y: centerY - 18, width: 36, height: 36 });
    currentX += 100;
  });

  // Valid flow node IDs set
  const validNodeIds = new Set<string>(positions.keys());

  // 4. Generate and Deduplicate Sequence Flows
  const flows: FlowElement[] = [];
  const seenFlowPairs = new Set<string>();

  // Helper to add a valid flow
  const addFlow = (fromRaw: string, toRaw: string, edgeId?: string, label?: string) => {
    const from = sanitizeId(fromRaw);
    const to = sanitizeId(toRaw);

    if (!validNodeIds.has(from) || !validNodeIds.has(to) || from === to) {
      return;
    }

    const pairKey = `${from}__to__${to}`;
    if (seenFlowPairs.has(pairKey)) {
      return;
    }
    seenFlowPairs.add(pairKey);

    const fId = edgeId ? sanitizeId(edgeId) : `Flow_${from}_${to}`;
    flows.push({
      id: fId,
      from,
      to,
      label: label ? label.trim() : undefined,
    });
  };

  // Add all sequence and conditional edges from the graph
  edges
    .filter((e) => e.edgeType === 'sequence' || e.edgeType === 'conditional')
    .forEach((e) => {
      addFlow(e.from, e.to, e.id, e.label);
    });

  // Fallback: If Start Event has no outgoing flow, connect to first activity
  const firstStartId = startEvents.length > 0 ? sanitizeId(startEvents[0].id) : null;
  const firstActivityId = activityNodes.length > 0 ? sanitizeId(activityNodes[0].id) : null;
  if (firstStartId && firstActivityId) {
    const hasStartFlow = flows.some((f) => f.from === firstStartId);
    if (!hasStartFlow) {
      addFlow(firstStartId, firstActivityId, `Flow_start_${firstActivityId}`);
    }
  }

  // Fallback: Connect linear chain if no sequence flows were provided
  if (flows.length === 0 && activityNodes.length > 0) {
    for (let i = 0; i < activityNodes.length - 1; i++) {
      addFlow(activityNodes[i].id, activityNodes[i + 1].id);
    }
  }

  // Fallback: If End Event has no incoming flow, connect from last activity
  const lastEndId = endEvents.length > 0 ? sanitizeId(endEvents[0].id) : null;
  const lastActivityId = activityNodes.length > 0 ? sanitizeId(activityNodes[activityNodes.length - 1].id) : null;
  if (lastEndId && lastActivityId) {
    const hasEndFlow = flows.some((f) => f.to === lastEndId);
    if (!hasEndFlow) {
      addFlow(lastActivityId, lastEndId, `Flow_${lastActivityId}_end`);
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

  // Start Events
  startEvents.forEach((ev) => {
    const sId = sanitizeId(ev.id);
    xml += `    <bpmn:startEvent id="${sId}" name="${escapeXml(ev.label || 'Start')}" />\n`;
  });

  // Intermediate Events
  intermediateEvents.forEach((ev) => {
    const evId = sanitizeId(ev.id);
    xml += `    <bpmn:intermediateThrowEvent id="${evId}" name="${escapeXml(ev.label || 'Event')}" />\n`;
  });

  // Activities (User Tasks)
  activityNodes.forEach((act) => {
    const actId = sanitizeId(act.id);
    const roleRef = act.metadata?.roleRef;
    const roleObj = roleNodes.find((r) => r.id === roleRef || sanitizeId(r.id) === roleRef);
    const taskName = roleObj ? `${act.label} (${roleObj.label})` : act.label;
    xml += `    <bpmn:userTask id="${actId}" name="${escapeXml(taskName)}" />\n`;
  });

  // Gateways
  gatewayNodes.forEach((gw) => {
    const gwId = sanitizeId(gw.id);
    xml += `    <bpmn:exclusiveGateway id="${gwId}" name="${escapeXml(gw.label || 'Decision')}" />\n`;
  });

  // End Events
  endEvents.forEach((ev) => {
    const eId = sanitizeId(ev.id);
    xml += `    <bpmn:endEvent id="${eId}" name="${escapeXml(ev.label || 'End')}" />\n`;
  });

  // Sequence Flows
  flows.forEach((flow) => {
    const nameAttr = flow.label ? ` name="${escapeXml(flow.label)}"` : '';
    xml += `    <bpmn:sequenceFlow id="${flow.id}" sourceRef="${flow.from}" targetRef="${flow.to}"${nameAttr} />\n`;
  });

  xml += `  </bpmn:process>\n`;

  // BPMN Diagram Interchange (BPMNDI)
  xml += `  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${processId}">\n`;

  // Shapes - strictly for elements that exist in positions
  positions.forEach((pos, id) => {
    xml += `      <bpmndi:BPMNShape id="${id}_di" bpmnElement="${id}">
        <dc:Bounds x="${Math.round(pos.x)}" y="${Math.round(pos.y)}" width="${Math.round(pos.width)}" height="${Math.round(pos.height)}" />
      </bpmndi:BPMNShape>\n`;
  });

  // Edges (Waypoints) - strictly for elements where both from & to have positions
  flows.forEach((flow) => {
    const fromPos = positions.get(flow.from);
    const toPos = positions.get(flow.to);
    if (fromPos && toPos) {
      const startWaypointX = Math.round(fromPos.x + fromPos.width);
      const startWaypointY = Math.round(fromPos.y + fromPos.height / 2);
      const endWaypointX = Math.round(toPos.x);
      const endWaypointY = Math.round(toPos.y + toPos.height / 2);

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
