import type {
  CanonicalProcessGraph,
  GraphNode,
  GraphEdge,
  ProcessKnowledgeDTO,
  NodeType,
  EdgeType,
  GatewayType,
  EventType,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';

export interface BpmnClientParseResult {
  graph: CanonicalProcessGraph;
  knowledge: ProcessKnowledgeDTO;
  processName: string;
  processId: string;
}

function slugify(text: string): string {
  if (!text) return 'element';
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

export function parseBpmnXmlClient(xmlContent: string): BpmnClientParseResult {
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlContent, 'application/xml');

  const parserError = doc.querySelector('parsererror');
  if (parserError) {
    throw new Error('XML Parsing failed: ' + parserError.textContent);
  }

  const nodeMap = new Map<string, GraphNode>();
  const edges: GraphEdge[] = [];
  const nodeToLaneMap = new Map<string, string>();
  const roleNames: string[] = [];

  // 1. Lanes & Roles
  const lanes = doc.querySelectorAll('lane');
  lanes.forEach((lane, idx) => {
    const laneName = lane.getAttribute('name') || `Role ${idx + 1}`;
    roleNames.push(laneName);
    const roleId = `role-${slugify(laneName)}`;

    if (!nodeMap.has(roleId)) {
      nodeMap.set(roleId, {
        id: roleId,
        type: 'Role' as NodeType,
        label: laneName,
        metadata: {},
      });
    }

    const flowRefs = lane.querySelectorAll('flowNodeRef');
    flowRefs.forEach((ref) => {
      const refText = (ref.textContent || '').trim();
      if (refText) {
        nodeToLaneMap.set(refText, roleId);
      }
    });
  });

  // 2. Process metadata
  const procElem = doc.querySelector('process');
  const processId = procElem?.getAttribute('id') || 'imported-process';
  const processName = procElem?.getAttribute('name') || 'Imported Business Process';

  // 3. Start Events
  doc.querySelectorAll('startEvent').forEach((elem) => {
    const id = elem.getAttribute('id') || 'start';
    const name = elem.getAttribute('name') || 'Start';
    nodeMap.set(id, {
      id,
      type: 'Event' as NodeType,
      label: name,
      metadata: { eventType: 'start' as EventType },
    });
  });

  // 4. End Events
  doc.querySelectorAll('endEvent').forEach((elem) => {
    const id = elem.getAttribute('id') || 'end';
    const name = elem.getAttribute('name') || 'End';
    nodeMap.set(id, {
      id,
      type: 'Event' as NodeType,
      label: name,
      metadata: { eventType: 'end' as EventType },
    });
  });

  // 5. Intermediate Events
  doc.querySelectorAll('intermediateCatchEvent, intermediateThrowEvent').forEach((elem) => {
    const id = elem.getAttribute('id') || `event-${Date.now()}`;
    const name = elem.getAttribute('name') || 'Intermediate Event';
    nodeMap.set(id, {
      id,
      type: 'Event' as NodeType,
      label: name,
      metadata: { eventType: 'intermediate' as EventType },
    });
  });

  // 6. Activities / Tasks
  const taskSelectors = 'task, userTask, serviceTask, manualTask, scriptTask, sendTask, receiveTask';
  doc.querySelectorAll(taskSelectors).forEach((elem) => {
    const id = elem.getAttribute('id') || `task-${Date.now()}`;
    const name = elem.getAttribute('name') || 'Unnamed Activity';
    const roleRef = nodeToLaneMap.get(id);

    nodeMap.set(id, {
      id,
      type: 'Activity' as NodeType,
      label: name,
      metadata: { roleRef },
    });
  });

  // 7. Gateways (Exclusive, Parallel, Inclusive)
  doc.querySelectorAll('exclusiveGateway').forEach((elem) => {
    const id = elem.getAttribute('id') || `gw-${Date.now()}`;
    const name = elem.getAttribute('name') || '';
    nodeMap.set(id, {
      id,
      type: 'Gateway' as NodeType,
      label: name,
      metadata: { gatewayType: 'exclusive' as GatewayType },
    });
  });

  doc.querySelectorAll('parallelGateway').forEach((elem) => {
    const id = elem.getAttribute('id') || `gw-pa-${Date.now()}`;
    const name = elem.getAttribute('name') || 'Parallel Split / Join';
    nodeMap.set(id, {
      id,
      type: 'Gateway' as NodeType,
      label: name,
      metadata: { gatewayType: 'parallel' as GatewayType },
    });
  });

  doc.querySelectorAll('inclusiveGateway').forEach((elem) => {
    const id = elem.getAttribute('id') || `gw-in-${Date.now()}`;
    const name = elem.getAttribute('name') || 'Inclusive Gateway';
    nodeMap.set(id, {
      id,
      type: 'Gateway' as NodeType,
      label: name,
      metadata: { gatewayType: 'inclusive' as GatewayType },
    });
  });

  // 8. Sequence Flows
  doc.querySelectorAll('sequenceFlow').forEach((elem, idx) => {
    const id = elem.getAttribute('id') || `flow-${idx + 1}`;
    const sourceRef = elem.getAttribute('sourceRef');
    const targetRef = elem.getAttribute('targetRef');
    const name = elem.getAttribute('name');

    if (sourceRef && targetRef) {
      edges.push({
        id,
        from: sourceRef,
        to: targetRef,
        edgeType: name ? ('conditional' as EdgeType) : ('sequence' as EdgeType),
        label: name || null,
      });
    }
  });

  // 9. Link Roles to Activities via Association Edges
  nodeToLaneMap.forEach((roleId, activityId) => {
    if (nodeMap.has(roleId) && nodeMap.has(activityId)) {
      edges.push({
        id: `assoc-${roleId}-${activityId}`,
        from: roleId,
        to: activityId,
        edgeType: 'association' as EdgeType,
        label: 'Performs',
      });
    }
  });

  const graphNodes = Array.from(nodeMap.values());
  const graph: CanonicalProcessGraph = {
    graphId: processId,
    nodes: graphNodes,
    edges,
  };

  const activities = graphNodes.filter((n) => n.type === 'Activity').map((n) => n.label);
  const gateways = graphNodes.filter((n) => n.type === 'Gateway').map((n) => n.label).filter(Boolean);
  const events = graphNodes.filter((n) => n.type === 'Event').map((n) => n.label);

  const knowledge: ProcessKnowledgeDTO = {
    activities,
    actors: roleNames,
    roles: roleNames,
    systems: [],
    events,
    gateways,
    inputs: ['Source BPMN 2.0 XML'],
    outputs: ['Orchestrated Process Execution'],
    businessRules: ['Conforms to OMG BPMN 2.0 specification'],
    risks: [],
    conflicts: [],
  };

  return {
    graph,
    knowledge,
    processName,
    processId,
  };
}
