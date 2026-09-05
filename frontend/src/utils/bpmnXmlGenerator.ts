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

  // Filter out any end events that are not referenced in the graph edges (BUG 8 fix)
  if (endEvents.length > 1) {
    const referencedToIds = new Set(edges.map((e) => e.to));
    const activeEndEvents = endEvents.filter((ev) => referencedToIds.has(ev.id));
    if (activeEndEvents.length > 0) {
      endEvents = activeEndEvents;
    } else {
      endEvents = [endEvents[0]];
    }
  }

  // Valid flow node IDs map
  const allFlowNodes: { node: GraphNode; kind: 'start' | 'end' | 'intermediate' | 'activity' | 'gateway' }[] = [];
  startEvents.forEach((n) => allFlowNodes.push({ node: n, kind: 'start' }));
  activityNodes.forEach((n) => allFlowNodes.push({ node: n, kind: 'activity' }));
  gatewayNodes.forEach((n) => allFlowNodes.push({ node: n, kind: 'gateway' }));
  intermediateEvents.forEach((n) => allFlowNodes.push({ node: n, kind: 'intermediate' }));
  endEvents.forEach((n) => allFlowNodes.push({ node: n, kind: 'end' }));

  const validNodeIds = new Set<string>(allFlowNodes.map((item) => sanitizeId(item.node.id)));

  // Generate and Deduplicate Sequence Flows
  const flows: FlowElement[] = [];
  const seenFlowPairs = new Set<string>();

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

  // Fallback: If End Event has no incoming flow, connect from last activity
  const lastEndId = endEvents.length > 0 ? sanitizeId(endEvents[0].id) : null;
  const lastActivityId = activityNodes.length > 0 ? sanitizeId(activityNodes[activityNodes.length - 1].id) : null;
  if (lastEndId && lastActivityId) {
    const hasEndFlow = flows.some((f) => f.to === lastEndId);
    if (!hasEndFlow) {
      addFlow(lastActivityId, lastEndId, `Flow_${lastActivityId}_end`);
    }
  }

  // -------------------------------------------------------------
  // 2D Branch-Aware Topological Layout Engine
  // -------------------------------------------------------------
  const outgoingMap = new Map<string, string[]>();
  const incomingMap = new Map<string, string[]>();

  validNodeIds.forEach((id) => {
    outgoingMap.set(id, []);
    incomingMap.set(id, []);
  });

  flows.forEach((flow) => {
    outgoingMap.get(flow.from)?.push(flow.to);
    incomingMap.get(flow.to)?.push(flow.from);
  });

  // 1. Calculate topological depth (X column)
  const depthMap = new Map<string, number>();
  const startIds = startEvents.map((s) => sanitizeId(s.id));
  const queue: string[] = startIds.length > 0 ? [...startIds] : Array.from(validNodeIds).filter((id) => (incomingMap.get(id)?.length || 0) === 0);

  queue.forEach((id) => depthMap.set(id, 0));

  let iterations = 0;
  const maxIterations = validNodeIds.size * 3;
  while (queue.length > 0 && iterations < maxIterations) {
    iterations++;
    const current = queue.shift()!;
    const currentDepth = depthMap.get(current) || 0;
    const nextNodes = outgoingMap.get(current) || [];

    for (const next of nextNodes) {
      const existingDepth = depthMap.get(next);
      if (existingDepth === undefined || currentDepth + 1 > existingDepth) {
        depthMap.set(next, currentDepth + 1);
        queue.push(next);
      }
    }
  }

  // Assign depths for any disconnected components
  validNodeIds.forEach((id) => {
    if (!depthMap.has(id)) {
      depthMap.set(id, 0);
    }
  });

  // 2. Calculate vertical branch tracks (Y row)
  const trackMap = new Map<string, number>();
  const branchTraversed = new Set<string>();

  startIds.forEach((id) => trackMap.set(id, 0));

  const trackQueue: string[] = startIds.length > 0 ? [...startIds] : Array.from(validNodeIds).filter((id) => (incomingMap.get(id)?.length || 0) === 0);

  while (trackQueue.length > 0) {
    const u = trackQueue.shift()!;
    if (branchTraversed.has(u)) continue;
    branchTraversed.add(u);

    const currentTrack = trackMap.get(u) || 0;
    const children = outgoingMap.get(u) || [];

    if (children.length === 1) {
      const child = children[0];
      if (!trackMap.has(child)) {
        trackMap.set(child, currentTrack);
      }
      trackQueue.push(child);
    } else if (children.length > 1) {
      // Gateway / Decision branch point: distribute children across distinct Y lanes
      // Child 0 (e.g. yes / primary branch) -> upper lane (currentTrack - 1)
      // Child 1 (e.g. no / cascade) -> lower lane (currentTrack + 1)
      // Additional branches spread symmetrically
      children.forEach((child, idx) => {
        if (!trackMap.has(child)) {
          const laneOffset = idx === 0 ? -1 : idx;
          trackMap.set(child, currentTrack + laneOffset);
        }
        trackQueue.push(child);
      });
    }
  }

  // End events align to center track (track 0)
  endEvents.forEach((ev) => {
    const eId = sanitizeId(ev.id);
    trackMap.set(eId, 0);
  });

  // 3. Compute final geometric coordinates
  const positions = new Map<string, ElementPosition>();
  const startX = 180;
  const centerY = 240;
  const colSpacing = 200;
  const rowSpacing = 140;

  // Track occupied (depth, track) slots to resolve any coordinate collisions
  const occupiedSlots = new Set<string>();

  allFlowNodes.forEach(({ node, kind }) => {
    const id = sanitizeId(node.id);
    const depth = depthMap.get(id) || 0;
    let track = trackMap.get(id) || 0;

    let slotKey = `${depth}_${track}`;
    while (occupiedSlots.has(slotKey)) {
      track += 1;
      slotKey = `${depth}_${track}`;
    }
    occupiedSlots.add(slotKey);

    const posX = startX + depth * colSpacing;
    const posY = centerY + track * rowSpacing;

    if (kind === 'start' || kind === 'end' || kind === 'intermediate') {
      positions.set(id, { x: posX, y: posY - 18, width: 36, height: 36 });
    } else if (kind === 'gateway') {
      positions.set(id, { x: posX, y: posY - 25, width: 50, height: 50 });
    } else {
      positions.set(id, { x: posX, y: posY - 40, width: 140, height: 80 });
    }
  });

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

  // Intermediate Events (Timer Throw / Catch Events)
  intermediateEvents.forEach((ev) => {
    const evId = sanitizeId(ev.id);
    if (ev.metadata?.eventType === 'timer') {
      const dur = ev.metadata?.duration || 'P7D';
      xml += `    <bpmn:intermediateCatchEvent id="${evId}" name="${escapeXml(ev.label || 'Timer')}">\n`;
      xml += `      <bpmn:timerEventDefinition id="${evId}_timerDef">\n`;
      xml += `        <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">${escapeXml(dur)}</bpmn:timeDuration>\n`;
      xml += `      </bpmn:timerEventDefinition>\n`;
      xml += `    </bpmn:intermediateCatchEvent>\n`;
    } else {
      xml += `    <bpmn:intermediateThrowEvent id="${evId}" name="${escapeXml(ev.label || 'Event')}" />\n`;
    }
  });

function getTaskTagName(act: GraphNode): string {
  const metaType = act.metadata?.taskType?.toLowerCase();
  if (metaType === 'service' || metaType === 'servicetask') return 'bpmn:serviceTask';
  if (metaType === 'manual' || metaType === 'manualtask') return 'bpmn:manualTask';
  if (metaType === 'script' || metaType === 'scripttask') return 'bpmn:scriptTask';
  if (metaType === 'send' || metaType === 'sendtask') return 'bpmn:sendTask';
  if (metaType === 'receive' || metaType === 'receivetask') return 'bpmn:receiveTask';

  const label = (act.label || '').toLowerCase();
  // Automated systems, MQTT, telemetry, ERP integrations, API calls
  if (
    label.includes('mqtt') ||
    label.includes('telemetry') ||
    label.includes('erp') ||
    label.includes('api') ||
    label.includes('database') ||
    label.includes('detects anomaly') ||
    label.includes('automated') ||
    label.includes('webhook')
  ) {
    if (label.includes('send') || label.includes('alert')) {
      return 'bpmn:sendTask';
    }
    return 'bpmn:serviceTask';
  }

  // Physical hands-on manual maintenance activities
  if (
    label.includes('replace') ||
    label.includes('swap') ||
    label.includes('repair') ||
    label.includes('hardware') ||
    label.includes('install')
  ) {
    return 'bpmn:manualTask';
  }

  // Human user interactions (reviews, decisions, supervisory actions)
  return 'bpmn:userTask';
}

  // Activities (Semantic BPMN Task Types: Service, Manual, Send, User)
  activityNodes.forEach((act) => {
    const actId = sanitizeId(act.id);
    const roleRef = act.metadata?.roleRef;
    const roleObj = roleNodes.find((r) => r.id === roleRef || sanitizeId(r.id) === roleRef);
    const taskName = roleObj ? `${act.label} (${roleObj.label})` : act.label;
    const tagName = getTaskTagName(act);
    xml += `    <${tagName} id="${actId}" name="${escapeXml(taskName)}" />\n`;
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

  // Edges (Waypoints) - orthogonal stepped routing for clean branch presentation
  flows.forEach((flow) => {
    const fromPos = positions.get(flow.from);
    const toPos = positions.get(flow.to);
    if (fromPos && toPos) {
      const fromCenterX = Math.round(fromPos.x + fromPos.width / 2);
      const fromCenterY = Math.round(fromPos.y + fromPos.height / 2);
      const toCenterX = Math.round(toPos.x + toPos.width / 2);
      const toCenterY = Math.round(toPos.y + toPos.height / 2);

      xml += `      <bpmndi:BPMNEdge id="${flow.id}_di" bpmnElement="${flow.id}">\n`;

      if (Math.abs(fromCenterY - toCenterY) < 5) {
        // Straight horizontal connection
        const startX = Math.round(fromPos.x + fromPos.width);
        const endX = Math.round(toPos.x);
        xml += `        <di:waypoint x="${startX}" y="${fromCenterY}" />\n`;
        xml += `        <di:waypoint x="${endX}" y="${toCenterY}" />\n`;
      } else if (toPos.x <= fromPos.x) {
        // Loop back route: curves underneath nodes
        const loopBottomY = Math.round(Math.max(fromPos.y + fromPos.height, toPos.y + toPos.height) + 35);
        xml += `        <di:waypoint x="${fromCenterX}" y="${Math.round(fromPos.y + fromPos.height)}" />\n`;
        xml += `        <di:waypoint x="${fromCenterX}" y="${loopBottomY}" />\n`;
        xml += `        <di:waypoint x="${toCenterX}" y="${loopBottomY}" />\n`;
        xml += `        <di:waypoint x="${toCenterX}" y="${Math.round(toPos.y + toPos.height)}" />\n`;
      } else {
        // Forward branch / convergence stepped connector
        const startX = Math.round(fromPos.x + fromPos.width);
        const endX = Math.round(toPos.x);
        const midX = Math.round(startX + Math.max(25, (endX - startX) / 2));
        xml += `        <di:waypoint x="${startX}" y="${fromCenterY}" />\n`;
        xml += `        <di:waypoint x="${midX}" y="${fromCenterY}" />\n`;
        xml += `        <di:waypoint x="${midX}" y="${toCenterY}" />\n`;
        xml += `        <di:waypoint x="${endX}" y="${toCenterY}" />\n`;
      }

      xml += `      </bpmndi:BPMNEdge>\n`;
    }
  });

  xml += `    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

  return xml;
}
