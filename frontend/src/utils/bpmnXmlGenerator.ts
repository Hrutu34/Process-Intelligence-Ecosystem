import type {
  ProcessGraphDTO,
  GraphNode,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';

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
      case '<': return '&lt;';
      case '>': return '&gt;';
      case '&': return '&amp;';
      case '\'': return '&apos;';
      case '"': return '&quot;';
      default: return c;
    }
  });
}

export function canonicalGraphToBpmnXml(graph: ProcessGraphDTO): string {
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

  // Fallback virtual events if none detected
  if (startEvents.length === 0 && (activityNodes.length > 0 || eventNodes.length === 0)) {
    startEvents = [{
      id: 'event_start_process',
      type: 'Event',
      label: 'Start Process',
      metadata: { eventType: 'start' },
    }];
  }

  if (endEvents.length === 0 && (activityNodes.length > 0 || eventNodes.length === 0)) {
    endEvents = [{
      id: 'event_end_process',
      type: 'Event',
      label: 'Process Completed',
      metadata: { eventType: 'end' },
    }];
  }

  if (endEvents.length > 1) {
    const referencedToIds = new Set(edges.map((e) => e.to));
    const activeEndEvents = endEvents.filter((ev) => referencedToIds.has(ev.id));
    if (activeEndEvents.length > 0) {
      endEvents = activeEndEvents;
    } else {
      endEvents = [endEvents[0]];
    }
  }

  // Map Valid Flow Nodes
  const allFlowNodes: { node: GraphNode; kind: 'start' | 'end' | 'intermediate' | 'activity' | 'gateway' }[] = [];
  startEvents.forEach((n) => allFlowNodes.push({ node: n, kind: 'start' }));
  activityNodes.forEach((n) => allFlowNodes.push({ node: n, kind: 'activity' }));
  gatewayNodes.forEach((n) => allFlowNodes.push({ node: n, kind: 'gateway' }));
  intermediateEvents.forEach((n) => allFlowNodes.push({ node: n, kind: 'intermediate' }));
  endEvents.forEach((n) => allFlowNodes.push({ node: n, kind: 'end' }));

  const validNodeIds = new Set<string>(allFlowNodes.map((item) => sanitizeId(item.node.id)));

  // Generate & Deduplicate Sequence Flows
  const flows: FlowElement[] = [];
  const seenFlowPairs = new Set<string>();

  const addFlow = (fromRaw: string, toRaw: string, edgeId?: string, label?: string) => {
    const from = sanitizeId(fromRaw);
    const to = sanitizeId(toRaw);

    if (!validNodeIds.has(from) || !validNodeIds.has(to) || from === to) return;

    const pairKey = `${from}__to__${to}`;
    if (seenFlowPairs.has(pairKey)) return;
    seenFlowPairs.add(pairKey);

    const fId = edgeId ? sanitizeId(edgeId) : `Flow_${from}_${to}`;
    flows.push({ id: fId, from, to, label: label ? label.trim() : undefined });
  };

  edges
    .filter((e) => e.edgeType === 'sequence' || e.edgeType === 'conditional')
    .forEach((e) => addFlow(e.from, e.to, e.id, e.label ?? undefined));

  // Connections Fallbacks
  const firstStartId = startEvents.length > 0 ? sanitizeId(startEvents[0].id) : null;
  const firstActivityId = activityNodes.length > 0 ? sanitizeId(activityNodes[0].id) : null;
  if (firstStartId && firstActivityId && !flows.some((f) => f.from === firstStartId)) {
    addFlow(firstStartId, firstActivityId, `Flow_start_${firstActivityId}`);
  }

  const lastEndId = endEvents.length > 0 ? sanitizeId(endEvents[0].id) : null;
  const lastActivityId = activityNodes.length > 0 ? sanitizeId(activityNodes[activityNodes.length - 1].id) : null;
  if (lastEndId && lastActivityId && !flows.some((f) => f.to === lastEndId)) {
    addFlow(lastActivityId, lastEndId, `Flow_${lastActivityId}_end`);
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

  validNodeIds.forEach((id) => { if (!depthMap.has(id)) depthMap.set(id, 0); });

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
      if (!trackMap.has(child)) trackMap.set(child, currentTrack);
      trackQueue.push(child);
    } else if (children.length > 1) {
      children.forEach((child, idx) => {
        if (!trackMap.has(child)) {
          const laneOffset = idx === 0 ? -1 : idx;
          trackMap.set(child, currentTrack + laneOffset);
        }
        trackQueue.push(child);
      });
    }
  }

  endEvents.forEach((ev) => trackMap.set(sanitizeId(ev.id), 0));

  // 3. Compute final geometric coordinates
  const positions = new Map<string, ElementPosition>();
  const startX = 180;
  const centerY = 280;
  const colSpacing = 240; // Increased spacing to prevent overlaps
  const rowSpacing = 160; // Increased spacing to prevent overlaps

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
  <bpmn:process id="${processId}" name="${escapeXml(processName)}" isExecutable="true">\n`;

  startEvents.forEach((ev) => xml += `    <bpmn:startEvent id="${sanitizeId(ev.id)}" name="${escapeXml(ev.label || 'Start')}" />\n`);
  
  intermediateEvents.forEach((ev) => {
    const evId = sanitizeId(ev.id);
    if (ev.metadata?.eventType === 'timer') {
      const dur = typeof ev.metadata?.duration === 'string' ? ev.metadata.duration : 'P7D';
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
    const taskType = act.metadata?.taskType;
    const metaType = typeof taskType === 'string' ? taskType.toLowerCase() : undefined;
    if (metaType === 'service' || metaType === 'servicetask') return 'bpmn:serviceTask';
    if (metaType === 'manual' || metaType === 'manualtask') return 'bpmn:manualTask';
    if (metaType === 'script' || metaType === 'scripttask') return 'bpmn:scriptTask';
    if (metaType === 'send' || metaType === 'sendtask') return 'bpmn:sendTask';
    if (metaType === 'receive' || metaType === 'receivetask') return 'bpmn:receiveTask';

    const label = (act.label || '').toLowerCase();
    if (label.includes('mqtt') || label.includes('telemetry') || label.includes('erp') || label.includes('api') || label.includes('database') || label.includes('detects anomaly') || label.includes('automated') || label.includes('webhook')) {
      if (label.includes('send') || label.includes('alert')) return 'bpmn:sendTask';
      return 'bpmn:serviceTask';
    }
    if (label.includes('replace') || label.includes('swap') || label.includes('repair') || label.includes('hardware') || label.includes('install')) {
      return 'bpmn:manualTask';
    }
    return 'bpmn:userTask';
  }

  activityNodes.forEach((act) => {
    const actId = sanitizeId(act.id);
    const roleRef = act.metadata?.roleRef;
    const roleObj = roleNodes.find((r) => r.id === roleRef || sanitizeId(r.id) === roleRef);
    const taskName = roleObj ? `${act.label} (${roleObj.label})` : act.label;
    const tagName = getTaskTagName(act);
    xml += `    <${tagName} id="${actId}" name="${escapeXml(taskName)}" />\n`;
  });

  gatewayNodes.forEach((gw) => {
    xml += `    <bpmn:exclusiveGateway id="${sanitizeId(gw.id)}" name="${escapeXml(gw.label || 'Decision')}" />\n`;
  });

  endEvents.forEach((ev) => {
    xml += `    <bpmn:endEvent id="${sanitizeId(ev.id)}" name="${escapeXml(ev.label || 'End')}" />\n`;
  });

  flows.forEach((flow) => {
    const nameAttr = flow.label ? ` name="${escapeXml(flow.label)}"` : '';
    xml += `    <bpmn:sequenceFlow id="${flow.id}" sourceRef="${flow.from}" targetRef="${flow.to}"${nameAttr} />\n`;
  });

  xml += `  </bpmn:process>\n`;

  // BPMN Diagram Interchange (BPMNDI)
  xml += `  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${processId}">\n`;

  positions.forEach((pos, id) => {
    xml += `      <bpmndi:BPMNShape id="${id}_di" bpmnElement="${id}">
        <dc:Bounds x="${Math.round(pos.x)}" y="${Math.round(pos.y)}" width="${Math.round(pos.width)}" height="${Math.round(pos.height)}" />
      </bpmndi:BPMNShape>\n`;
  });

  // Staggered Orthogonal Edges Mapping
  flows.forEach((flow) => {
    const fromPos = positions.get(flow.from);
    const toPos = positions.get(flow.to);
    const fromNode = allFlowNodes.find(n => sanitizeId(n.node.id) === flow.from);

    if (fromPos && toPos) {
      const incomingEdges = flows.filter(f => f.to === flow.to);
      const inIndex = incomingEdges.indexOf(flow);
      const inTotal = incomingEdges.length;

      const outgoingEdges = flows.filter(f => f.from === flow.from);
      const outIndex = outgoingEdges.indexOf(flow);
      const outTotal = outgoingEdges.length;

      // Distribute entry anchors vertically on the left side of the target block
      const entryY = Math.round(toPos.y + (toPos.height * (inIndex + 1)) / (inTotal + 1));

      xml += `      <bpmndi:BPMNEdge id="${flow.id}_di" bpmnElement="${flow.id}">\n`;

      if (fromNode?.kind === 'gateway') {
        // Gateways dynamically route North, South, or East to avoid overlapping
        if (toPos.y + toPos.height < fromPos.y) {
          // Exit North
          const startX = Math.round(fromPos.x + fromPos.width / 2);
          const startY = Math.round(fromPos.y);
          const turnY = Math.round(fromPos.y - 20 - (outIndex * 15));
          xml += `        <di:waypoint x="${startX}" y="${startY}" />\n`;
          xml += `        <di:waypoint x="${startX}" y="${turnY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x - 20}" y="${turnY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x - 20}" y="${entryY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x}" y="${entryY}" />\n`;
        } else if (toPos.y > fromPos.y + fromPos.height) {
          // Exit South
          const startX = Math.round(fromPos.x + fromPos.width / 2);
          const startY = Math.round(fromPos.y + fromPos.height);
          const turnY = Math.round(fromPos.y + fromPos.height + 20 + (outIndex * 15));
          xml += `        <di:waypoint x="${startX}" y="${startY}" />\n`;
          xml += `        <di:waypoint x="${startX}" y="${turnY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x - 20}" y="${turnY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x - 20}" y="${entryY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x}" y="${entryY}" />\n`;
        } else {
          // Exit East
          const startX = Math.round(fromPos.x + fromPos.width);
          const startY = Math.round(fromPos.y + fromPos.height / 2);
          xml += `        <di:waypoint x="${startX}" y="${startY}" />\n`;
          if (toPos.x <= startX) { // Loopback
            const loopY = Math.round(Math.max(fromPos.y + fromPos.height, toPos.y + toPos.height) + 40 + (inIndex * 15));
            xml += `        <di:waypoint x="${startX + 20}" y="${startY}" />\n`;
            xml += `        <di:waypoint x="${startX + 20}" y="${loopY}" />\n`;
            xml += `        <di:waypoint x="${toPos.x - 20}" y="${loopY}" />\n`;
            xml += `        <di:waypoint x="${toPos.x - 20}" y="${entryY}" />\n`;
          } else if (Math.abs(startY - entryY) > 5) {
            const midX = Math.round(startX + (toPos.x - startX) / 2);
            xml += `        <di:waypoint x="${midX}" y="${startY}" />\n`;
            xml += `        <di:waypoint x="${midX}" y="${entryY}" />\n`;
          }
          xml += `        <di:waypoint x="${toPos.x}" y="${entryY}" />\n`;
        }
      } else {
        // Standard Nodes exit East
        const startX = Math.round(fromPos.x + fromPos.width);
        const startY = Math.round(fromPos.y + (fromPos.height * (outIndex + 1)) / (outTotal + 1));
        xml += `        <di:waypoint x="${startX}" y="${startY}" />\n`;

        if (toPos.x <= startX) { // Loopback
          const loopY = Math.round(Math.max(fromPos.y + fromPos.height, toPos.y + toPos.height) + 40 + (inIndex * 15));
          xml += `        <di:waypoint x="${startX + 20}" y="${startY}" />\n`;
          xml += `        <di:waypoint x="${startX + 20}" y="${loopY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x - 20}" y="${loopY}" />\n`;
          xml += `        <di:waypoint x="${toPos.x - 20}" y="${entryY}" />\n`;
        } else if (Math.abs(startY - entryY) > 5) {
          const midX = Math.round(startX + (toPos.x - startX) / 2) + (outIndex * 10);
          xml += `        <di:waypoint x="${midX}" y="${startY}" />\n`;
          xml += `        <di:waypoint x="${midX}" y="${entryY}" />\n`;
        }
        xml += `        <di:waypoint x="${toPos.x}" y="${entryY}" />\n`;
      }

      xml += `      </bpmndi:BPMNEdge>\n`;
    }
  });

  xml += `    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

  return xml;
}