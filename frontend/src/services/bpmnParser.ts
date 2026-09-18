/**
 * BPMN 2.0 XML Parser & Topology Extractor
 * Generic, domain-agnostic parser supporting Camunda, Signavio, ProcessIQ, and any standard BPMN 2.0 XML.
 * Extracts tasks, gateways, events, and sequence flows regardless of namespace prefix (bpmn:, bpmn2:, or un-prefixed).
 */

import type { ProcessTopology, SimulationParameters, DurationDistribution } from './simulationTypes';

export function parseBpmnXml(xmlString: string): ProcessTopology {
  if (!xmlString || typeof xmlString !== 'string') {
    throw new Error('Invalid BPMN XML input: expected non-empty string.');
  }

  // Parse XML using DOMParser (browser) or fallback regex parser
  let doc: Document | null = null;
  if (typeof window !== 'undefined' && window.DOMParser) {
    const parser = new window.DOMParser();
    doc = parser.parseFromString(xmlString, 'text/xml');
    const parseError = doc.querySelector('parsererror');
    if (parseError) {
      throw new Error('XML Parsing Error: ' + parseError.textContent);
    }
  } else {
    return parseBpmnXmlRegex(xmlString);
  }

  const nodes: ProcessTopology['nodes'] = {};
  const sequenceFlows: ProcessTopology['sequenceFlows'] = {};
  const startNodeIds: string[] = [];
  const endNodeIds: string[] = [];

  const getElements = (tagNames: string[]): Element[] => {
    const results: Element[] = [];
    if (!doc) return results;
    tagNames.forEach((tagName) => {
      const els = doc!.getElementsByTagName(tagName);
      for (let i = 0; i < els.length; i++) results.push(els[i]);
      const prefixes = ['bpmn:', 'bpmn2:', 'sem:'];
      prefixes.forEach((p) => {
        const pEls = doc!.getElementsByTagName(p + tagName);
        for (let i = 0; i < pEls.length; i++) results.push(pEls[i]);
      });
    });
    return results;
  };

  // 1. Parse Sequence Flows
  const flowElements = getElements(['sequenceFlow']);
  flowElements.forEach((el) => {
    const id = el.getAttribute('id');
    const name = el.getAttribute('name') || '';
    const sourceRef = el.getAttribute('sourceRef');
    const targetRef = el.getAttribute('targetRef');
    if (id && sourceRef && targetRef) {
      sequenceFlows[id] = { id, name, sourceRef, targetRef };
    }
  });

  // 2. Parse Task / Activity nodes
  const taskTags = [
    'task',
    'userTask',
    'serviceTask',
    'scriptTask',
    'manualTask',
    'businessRuleTask',
    'sendTask',
    'receiveTask',
    'callActivity'
  ];
  const taskElements = getElements(taskTags);
  taskElements.forEach((el) => {
    const id = el.getAttribute('id');
    if (!id || nodes[id]) return;
    const name = el.getAttribute('name') || id;

    nodes[id] = {
      id,
      name,
      type: 'task',
      incoming: [],
      outgoing: []
    };
  });

  // 3. Parse Gateways
  const gatewayTags = [
    'exclusiveGateway',
    'parallelGateway',
    'inclusiveGateway',
    'complexGateway',
    'eventBasedGateway'
  ];
  const gatewayElements = getElements(gatewayTags);
  gatewayElements.forEach((el) => {
    const id = el.getAttribute('id');
    if (!id || nodes[id]) return;
    const name = el.getAttribute('name') || id;
    const localName = el.localName || el.nodeName.split(':').pop();

    nodes[id] = {
      id,
      name,
      type:
        localName === 'parallelGateway'
          ? 'parallelGateway'
          : localName === 'inclusiveGateway'
          ? 'inclusiveGateway'
          : 'exclusiveGateway',
      incoming: [],
      outgoing: []
    };
  });

  // 4. Parse Events
  const eventTags = [
    'startEvent',
    'endEvent',
    'intermediateCatchEvent',
    'intermediateThrowEvent',
    'boundaryEvent'
  ];
  const eventElements = getElements(eventTags);
  eventElements.forEach((el) => {
    const id = el.getAttribute('id');
    if (!id || nodes[id]) return;
    const name = el.getAttribute('name') || id;
    const localName = el.localName || el.nodeName.split(':').pop();

    const isStart = localName === 'startEvent';
    const isEnd = localName === 'endEvent';

    nodes[id] = {
      id,
      name,
      type: isStart ? 'startEvent' : isEnd ? 'endEvent' : 'event',
      incoming: [],
      outgoing: []
    };

    if (isStart) startNodeIds.push(id);
    if (isEnd) endNodeIds.push(id);
  });

  // 5. Connect incoming and outgoing flows to nodes
  Object.values(sequenceFlows).forEach((flow) => {
    if (nodes[flow.sourceRef]) {
      nodes[flow.sourceRef].outgoing.push(flow.id);
    }
    if (nodes[flow.targetRef]) {
      nodes[flow.targetRef].incoming.push(flow.id);
    }
  });

  // 6. Handle edge cases
  if (startNodeIds.length === 0) {
    Object.values(nodes).forEach((n) => {
      if (n.incoming.length === 0 && n.outgoing.length > 0) {
        startNodeIds.push(n.id);
      }
    });
  }

  if (endNodeIds.length === 0) {
    Object.values(nodes).forEach((n) => {
      if (n.outgoing.length === 0) {
        endNodeIds.push(n.id);
      }
    });
  }

  // 7. Extract process metadata
  const processEl = doc.querySelector('process, bpmn\\:process, bpmn2\\:process');
  const processId = processEl ? processEl.getAttribute('id') || 'Process_1' : 'Process_1';
  const processName = processEl ? processEl.getAttribute('name') || processId : 'Business Process';

  return {
    id: processId,
    name: processName,
    nodes,
    sequenceFlows,
    startNodeIds,
    endNodeIds,
    tasks: Object.values(nodes).filter((n) => n.type === 'task'),
    gateways: Object.values(nodes).filter((n) => n.type.includes('Gateway')) as ProcessTopology['gateways'],
    events: Object.values(nodes).filter(
      (n) => n.type.includes('Event') || n.type === 'event'
    ) as ProcessTopology['events']
  };
}

/**
 * Fallback regex-based parser when running in Node.js test environment without DOMParser
 */
function parseBpmnXmlRegex(xml: string): ProcessTopology {
  const nodes: ProcessTopology['nodes'] = {};
  const sequenceFlows: ProcessTopology['sequenceFlows'] = {};
  const startNodeIds: string[] = [];
  const endNodeIds: string[] = [];

  // Match sequence flows
  const flowRegex =
    /<(?:[\w-]+:)?sequenceFlow\s+[^>]*?id="([^"]+)"[^>]*?sourceRef="([^"]+)"[^>]*?targetRef="([^"]+)"[^>]*?(?:name="([^"]*)")?[^>]*?>/gi;
  let match: RegExpExecArray | null;
  while ((match = flowRegex.exec(xml)) !== null) {
    const [, id, sourceRef, targetRef, name = ''] = match;
    sequenceFlows[id] = { id, name, sourceRef, targetRef };
  }

  // Match task nodes
  const taskRegex =
    /<(?:[\w-]+:)?(userTask|serviceTask|scriptTask|manualTask|businessRuleTask|sendTask|receiveTask|task)\s+[^>]*?id="([^"]+)"(?:[^>]*?name="([^"]*)")?[^>]*?>/gi;
  while ((match = taskRegex.exec(xml)) !== null) {
    const [, , id, name = id] = match;
    nodes[id] = { id, name, type: 'task', incoming: [], outgoing: [] };
  }

  // Match gateways
  const gwRegex =
    /<(?:[\w-]+:)?(exclusiveGateway|parallelGateway|inclusiveGateway)\s+[^>]*?id="([^"]+)"(?:[^>]*?name="([^"]*)")?[^>]*?>/gi;
  while ((match = gwRegex.exec(xml)) !== null) {
    const [, gwType, id, name = id] = match;
    nodes[id] = {
      id,
      name,
      type:
        gwType === 'parallelGateway'
          ? 'parallelGateway'
          : gwType === 'inclusiveGateway'
          ? 'inclusiveGateway'
          : 'exclusiveGateway',
      incoming: [],
      outgoing: []
    };
  }

  // Match events
  const eventRegex =
    /<(?:[\w-]+:)?(startEvent|endEvent|intermediateCatchEvent)\s+[^>]*?id="([^"]+)"(?:[^>]*?name="([^"]*)")?[^>]*?>/gi;
  while ((match = eventRegex.exec(xml)) !== null) {
    const [, evType, id, name = id] = match;
    const isStart = evType === 'startEvent';
    const isEnd = evType === 'endEvent';
    nodes[id] = {
      id,
      name,
      type: isStart ? 'startEvent' : isEnd ? 'endEvent' : 'event',
      incoming: [],
      outgoing: []
    };
    if (isStart) startNodeIds.push(id);
    if (isEnd) endNodeIds.push(id);
  }

  // Connect flows
  Object.values(sequenceFlows).forEach((flow) => {
    if (nodes[flow.sourceRef]) nodes[flow.sourceRef].outgoing.push(flow.id);
    if (nodes[flow.targetRef]) nodes[flow.targetRef].incoming.push(flow.id);
  });

  if (startNodeIds.length === 0) {
    Object.values(nodes).forEach((n) => {
      if (n.incoming.length === 0 && n.outgoing.length > 0) startNodeIds.push(n.id);
    });
  }

  if (endNodeIds.length === 0) {
    Object.values(nodes).forEach((n) => {
      if (n.outgoing.length === 0) endNodeIds.push(n.id);
    });
  }

  const procMatch = xml.match(/<(?:[\w-]+:)?process\s+[^>]*?id="([^"]+)"(?:[^>]*?name="([^"]*)")?[^>]*?>/i);
  const processId = procMatch ? procMatch[1] : 'Process_1';
  const processName = procMatch ? procMatch[2] || processId : 'Business Process';

  return {
    id: processId,
    name: processName,
    nodes,
    sequenceFlows,
    startNodeIds,
    endNodeIds,
    tasks: Object.values(nodes).filter((n) => n.type === 'task'),
    gateways: Object.values(nodes).filter((n) => n.type.includes('Gateway')) as ProcessTopology['gateways'],
    events: Object.values(nodes).filter(
      (n) => n.type.includes('Event') || n.type === 'event'
    ) as ProcessTopology['events']
  };
}

/**
 * Domain-specific terminology inference
 */
export function inferProcessDomain(processName = '', tasks: Array<{ id: string; name: string }> = []) {
  const allText = (processName + ' ' + tasks.map((t) => t.name || '').join(' ')).toLowerCase();

  if (allText.includes('wash') || allText.includes('car') || allText.includes('vehicle')) {
    return {
      domainName: 'Car Wash & Detailing Service',
      entityName: 'Cars / Vehicles',
      entitySingular: 'Car',
      caseCountLabel: 'Number of Cars to Wash (Volume)',
      arrivalLabel: 'Time Between Arriving Cars',
      slaLabel: 'Max Wash & Dry Target Limit',
      arrivalHelpText: 'Average time between customer vehicles arriving in line.',
      slaHelpText: 'Customer target: vehicle wash, dry, and polish completion limit.',
      defaultArrivalMinutes: 6,
      defaultSlaHours: 1.5
    };
  }

  if (
    allText.includes('claim') ||
    allText.includes('damage') ||
    allText.includes('insurance') ||
    allText.includes('fraud')
  ) {
    return {
      domainName: 'Insurance Claim Settlement',
      entityName: 'Insurance Claims',
      entitySingular: 'Claim',
      caseCountLabel: 'Total Claims to Process',
      arrivalLabel: 'Claim Submission Frequency',
      slaLabel: 'Claim Settlement SLA Target',
      arrivalHelpText: 'Average minutes between policyholders submitting new claims.',
      slaHelpText: 'Regulatory target: maximum hours from intake to final settlement/payment.',
      defaultArrivalMinutes: 20,
      defaultSlaHours: 12
    };
  }

  if (
    allText.includes('ticket') ||
    allText.includes('it') ||
    allText.includes('incident') ||
    allText.includes('bug') ||
    allText.includes('helpdesk')
  ) {
    return {
      domainName: 'IT Incident & Helpdesk Support',
      entityName: 'Support Tickets',
      entitySingular: 'Ticket',
      caseCountLabel: 'Total IT Tickets to Resolve',
      arrivalLabel: 'Ticket Influx Frequency',
      slaLabel: 'Ticket Resolution SLA Target',
      arrivalHelpText: 'Average minutes between employees submitting new helpdesk tickets.',
      slaHelpText: 'Target hours before an IT incident is considered an SLA breach.',
      defaultArrivalMinutes: 15,
      defaultSlaHours: 8
    };
  }

  if (
    allText.includes('invoice') ||
    allText.includes('purchase') ||
    allText.includes('requisition') ||
    allText.includes('expense') ||
    allText.includes('reimbursement')
  ) {
    return {
      domainName: 'Finance, Procurement & Accounts Payable',
      entityName: 'Invoices / Requisitions',
      entitySingular: 'Invoice',
      caseCountLabel: 'Total Invoices / POs to Process',
      arrivalLabel: 'Invoice Submission Frequency',
      slaLabel: 'Payment Approval SLA Target',
      arrivalHelpText: 'Average minutes between incoming vendor bills or purchase requests.',
      slaHelpText: 'Target hours to verify and approve payment without vendor penalties.',
      defaultArrivalMinutes: 25,
      defaultSlaHours: 24
    };
  }

  if (
    allText.includes('onboard') ||
    allText.includes('employee') ||
    allText.includes('leave') ||
    allText.includes('hr') ||
    allText.includes('hire')
  ) {
    return {
      domainName: 'Human Resources & Employee Operations',
      entityName: 'Employee Requests / Hires',
      entitySingular: 'Request',
      caseCountLabel: 'Total Employees / Requests to Process',
      arrivalLabel: 'Request Influx Frequency',
      slaLabel: 'HR Onboarding / Approval Deadline',
      arrivalHelpText: 'Average minutes between new candidate submissions or leave requests.',
      slaHelpText: 'Target hours for HR and managers to process paperwork and provisioning.',
      defaultArrivalMinutes: 45,
      defaultSlaHours: 48
    };
  }

  if (
    allText.includes('order') ||
    allText.includes('fulfillment') ||
    allText.includes('warehouse') ||
    allText.includes('ship') ||
    allText.includes('pack')
  ) {
    return {
      domainName: 'Supply Chain & Order Fulfillment',
      entityName: 'Customer Orders',
      entitySingular: 'Order',
      caseCountLabel: 'Total Customer Orders to Fulfill',
      arrivalLabel: 'Order Influx Frequency',
      slaLabel: 'Order Dispatch SLA Target',
      arrivalHelpText: 'Average minutes between customers placing new orders online.',
      slaHelpText: 'Same-day or next-day shipping guarantee threshold.',
      defaultArrivalMinutes: 10,
      defaultSlaHours: 12
    };
  }

  const cleanTitle = (processName || 'Business Process').replace(/[_-]/g, ' ');
  return {
    domainName: cleanTitle,
    entityName: 'Process Cases',
    entitySingular: 'Case',
    caseCountLabel: `Total ${cleanTitle} Instances to Run`,
    arrivalLabel: 'Case Inter-Arrival Frequency',
    slaLabel: 'Process Completion SLA Limit',
    arrivalHelpText: 'Average time between new instances entering the process.',
    slaHelpText: 'Target limit in hours before an instance is flagged as delayed.',
    defaultArrivalMinutes: 20,
    defaultSlaHours: 12
  };
}

/**
 * Generates sensible default simulation parameters for any parsed BPMN model.
 * Inspects node names to tailor realistic duration distributions (strictly Normal or Constant).
 */
export function generateDefaultParameters(parsedBpmn: ProcessTopology): SimulationParameters {
  const domainMeta = inferProcessDomain(parsedBpmn.name, parsedBpmn.tasks);

  const parameters: SimulationParameters = {
    global: {
      caseCount: 400,
      arrivalMeanMinutes: domainMeta.defaultArrivalMinutes || 20,
      arrivalDistribution: 'exponential',
      slaThresholdHours: domainMeta.defaultSlaHours || 12,
      workHoursPerDay: 8,
      domainName: domainMeta.domainName,
      entityName: domainMeta.entityName,
      entitySingular: domainMeta.entitySingular,
      caseCountLabel: domainMeta.caseCountLabel,
      arrivalLabel: domainMeta.arrivalLabel,
      slaLabel: domainMeta.slaLabel,
      arrivalHelpText: domainMeta.arrivalHelpText,
      slaHelpText: domainMeta.slaHelpText
    },
    tasks: {},
    gateways: {}
  };

  // 1. Task parameters (strictly 'normal' or 'constant')
  parsedBpmn.tasks.forEach((task) => {
    const label = (task.name || '').toLowerCase();

    let meanDuration = 30;
    let stdDev = 8;
    let distribution: DurationDistribution = 'normal';
    let resourceCapacity = 2;
    let costPerExecution = 15;
    let hourlyLaborRate = 35;
    let reworkRate = 0.05;

    if (
      label.includes('approval') ||
      label.includes('mgr') ||
      label.includes('manager') ||
      label.includes('sign-off')
    ) {
      meanDuration = 60;
      stdDev = 15;
      distribution = 'normal';
      resourceCapacity = 1;
      hourlyLaborRate = 75;
      costPerExecution = 30;
      reworkRate = 0.08;
    } else if (
      label.includes('investigate') ||
      label.includes('damage') ||
      label.includes('audit') ||
      label.includes('inspect') ||
      label.includes('specialist')
    ) {
      meanDuration = 90;
      stdDev = 25;
      distribution = 'normal';
      resourceCapacity = 2;
      hourlyLaborRate = 55;
      costPerExecution = 45;
      reworkRate = 0.1;
    } else if (
      label.includes('auto') ||
      label.includes('system') ||
      label.includes('screen') ||
      label.includes('scan') ||
      label.includes('post') ||
      label.includes('disburse') ||
      label.includes('notify') ||
      label.includes('generate')
    ) {
      meanDuration = 5;
      stdDev = 0;
      distribution = 'constant';
      resourceCapacity = 8;
      hourlyLaborRate = 5;
      costPerExecution = 2;
      reworkRate = 0.01;
    } else if (
      label.includes('wash') ||
      label.includes('clean') ||
      label.includes('dry') ||
      label.includes('polish')
    ) {
      meanDuration = 10;
      stdDev = 2;
      distribution = 'normal';
      resourceCapacity = 1;
      hourlyLaborRate = 20;
      costPerExecution = 8;
      reworkRate = 0.02;
    } else if (
      label.includes('triage') ||
      label.includes('log') ||
      label.includes('intake') ||
      label.includes('categorize') ||
      label.includes('requisition')
    ) {
      meanDuration = 15;
      stdDev = 4;
      distribution = 'normal';
      resourceCapacity = 3;
      hourlyLaborRate = 30;
      costPerExecution = 10;
      reworkRate = 0.04;
    }

    parameters.tasks[task.id] = {
      id: task.id,
      name: task.name,
      distribution,
      meanDuration,
      stdDev: distribution === 'constant' ? 0 : stdDev,
      minDuration: Math.max(1, Math.round(meanDuration * 0.5)),
      maxDuration: Math.round(meanDuration * 1.8),
      resourceCapacity,
      costPerExecution,
      hourlyLaborRate,
      reworkRate
    };
  });

  // 2. Gateway branch parameters (Exclusive XOR)
  parsedBpmn.gateways.forEach((gateway) => {
    if (gateway.type === 'exclusiveGateway' && gateway.outgoing && gateway.outgoing.length > 0) {
      const branches: Array<{ flowId: string; name: string; probability: number }> = [];
      let assignedSum = 0;
      let unassignedCount = 0;

      gateway.outgoing.forEach((flowId) => {
        const flow = parsedBpmn.sequenceFlows[flowId];
        const flowName = flow ? flow.name || '' : '';
        const pctMatch = flowName.match(/(\d+)%/);

        if (pctMatch) {
          const prob = parseInt(pctMatch[1], 10) / 100;
          branches.push({ flowId, name: flowName || flowId, probability: prob });
          assignedSum += prob;
        } else {
          branches.push({ flowId, name: flowName || flowId, probability: -1 });
          unassignedCount++;
        }
      });

      if (unassignedCount > 0) {
        const remaining = Math.max(0.05, 1 - assignedSum);
        const perBranch = remaining / unassignedCount;
        branches.forEach((b) => {
          if (b.probability === -1) b.probability = perBranch;
        });
      }

      const total = branches.reduce((sum, b) => sum + b.probability, 0) || 1;
      branches.forEach((b) => {
        b.probability = Number((b.probability / total).toFixed(2));
      });

      parameters.gateways[gateway.id] = {
        id: gateway.id,
        name: gateway.name,
        branches
      };
    }
  });

  return parameters;
}
