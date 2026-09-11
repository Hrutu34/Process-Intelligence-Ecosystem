import type { CanonicalProcessGraph, GraphNode } from '../../../backend/src/main/java/com/pie/shared/types/dto';

export interface ProcessNarrative {
  title: string;
  executiveSummary: string;
  triggerNarrative: string;
  flowSteps: string[];
  decisionNarratives: string[];
  concurrencyNarratives: string[];
  outcomeNarrative: string;
  fullMarkdown: string;
}

export function generateProcessNarrative(graph: CanonicalProcessGraph, processName?: string): ProcessNarrative {
  const name = processName || graph.graphId.replace(/^graph-|^Proc_/, '').replace(/[-_]/g, ' ') || 'Business Process';
  const nodes = graph.nodes || [];
  const edges = graph.edges || [];

  const startEvents = nodes.filter((n) => n.type === 'Event' && n.metadata?.eventType === 'start');
  const endEvents = nodes.filter((n) => n.type === 'Event' && n.metadata?.eventType === 'end');
  const activities = nodes.filter((n) => n.type === 'Activity');
  const gateways = nodes.filter((n) => n.type === 'Gateway');
  const roles = nodes.filter((n) => n.type === 'Role');

  const roleMap = new Map<string, string>();
  roles.forEach((r) => roleMap.set(r.id, r.label));

  // Role lookup by activity
  const getActivityPerformer = (act: GraphNode): string => {
    if (act.metadata?.roleRef && roleMap.has(act.metadata.roleRef)) {
      return roleMap.get(act.metadata.roleRef)!;
    }
    // Check association edges
    const assoc = edges.find((e) => e.edgeType === 'association' && e.to === act.id);
    if (assoc && roleMap.has(assoc.from)) {
      return roleMap.get(assoc.from)!;
    }
    return '';
  };

  // 1. Trigger / Initiation Narrative
  let triggerNarrative = '';
  if (startEvents.length > 0) {
    const sName = startEvents[0].label || 'Process Trigger';
    triggerNarrative = `The process is initiated upon **"${sName}"**.`;
  } else if (activities.length > 0) {
    triggerNarrative = `The workflow begins directly with the step **"${activities[0].label}"**.`;
  } else {
    triggerNarrative = 'The workflow initiation trigger is currently undefined.';
  }

  // 2. Sequential Step Breakdown
  const flowSteps: string[] = [];
  activities.forEach((act, idx) => {
    const performer = getActivityPerformer(act);
    const roleText = performer ? ` by the **${performer}**` : '';
    flowSteps.push(`Step ${idx + 1}: **${act.label}**${roleText}.`);
  });

  // 3. Decision Points Narrative
  const decisionNarratives: string[] = [];
  const exclusiveGws = gateways.filter((g) => g.metadata?.gatewayType !== 'parallel');
  exclusiveGws.forEach((gw) => {
    const gwLabel = gw.label?.trim() ? `**"${gw.label}"**` : 'a decision gateway';
    const outgoing = edges.filter((e) => e.from === gw.id);

    if (outgoing.length > 1) {
      const branchDescriptions = outgoing.map((e) => {
        const cond = e.label ? `If *"${e.label}"*` : 'Otherwise';
        const targetNode = nodes.find((n) => n.id === e.to);
        const targetName = targetNode?.label || e.to;
        return `${cond} $\\rightarrow$ proceed to **${targetName}**`;
      });
      decisionNarratives.push(`At ${gwLabel}, the process evaluates criteria: ${branchDescriptions.join('; ')}.`);
    } else if (outgoing.length === 1) {
      const targetNode = nodes.find((n) => n.id === outgoing[0].to);
      decisionNarratives.push(`At ${gwLabel}, the workflow follows a single direct branch to **${targetNode?.label || outgoing[0].to}** without alternative routing.`);
    } else {
      decisionNarratives.push(`At ${gwLabel}, no outgoing paths are configured.`);
    }
  });

  // 4. Concurrency & Parallel Tracks
  const concurrencyNarratives: string[] = [];
  const parallelSplits = gateways.filter((g) => {
    const isPa = g.metadata?.gatewayType === 'parallel' || g.label?.toLowerCase().includes('parallel');
    const outCount = edges.filter((e) => e.from === g.id).length;
    return isPa && outCount > 1;
  });

  parallelSplits.forEach((gw) => {
    const outgoing = edges.filter((e) => e.from === gw.id);
    const branchNames = outgoing.map((e) => {
      const target = nodes.find((n) => n.id === e.to);
      return target ? `**${target.label}**` : e.to;
    });
    concurrencyNarratives.push(
      `Concurrent Execution: The workflow splits into parallel execution tracks (${branchNames.join(', ')}). All concurrent activities must complete before progressing to synchronization.`
    );
  });

  // 5. Outcome Narrative
  let outcomeNarrative = '';
  if (endEvents.length > 0) {
    const endNames = endEvents.map((e) => `**"${e.label || 'Process Completed'}"**`).join(' or ');
    outcomeNarrative = `The process concludes successfully when reaching ${endNames}.`;
  } else {
    outcomeNarrative = 'The workflow terminates after completing the final activity; no explicit BPMN End Event is modeled.';
  }

  // 6. Executive Summary
  const rolesCount = roles.length;
  const rolesText = rolesCount > 0
    ? ` coordinated across **${roles.map((r) => r.label).join(', ')}**`
    : '';

  const executiveSummary =
    `The **${name}** coordinates **${activities.length} key activities**${rolesText}. ` +
    `It commences with ${startEvents[0]?.label ? `"${startEvents[0].label}"` : 'initial intake'}, ` +
    `navigates through ${gateways.length > 0 ? `${gateways.length} governance checkpoints` : 'linear sequential steps'}, ` +
    `and culminates in ${endEvents.length > 0 ? endEvents.map((e) => `"${e.label}"`).join(' / ') : 'workflow closure'}.`;

  // 7. Full Structured Markdown
  const fullMarkdown = `### 📋 Process Overview: ${name}

${executiveSummary}

#### 1. Workflow Initiation
${triggerNarrative}

#### 2. Key Operational Activities
${flowSteps.length > 0 ? flowSteps.map((s) => `- ${s}`).join('\n') : '- No activities configured.'}

${
  decisionNarratives.length > 0
    ? `#### 3. Decision Logic & Branching Criteria\n${decisionNarratives.map((d) => `- ${d}`).join('\n')}\n`
    : ''
}
${
  concurrencyNarratives.length > 0
    ? `#### 4. Parallel Workstreams & Synchronization\n${concurrencyNarratives.map((c) => `- ${c}`).join('\n')}\n`
    : ''
}
#### 5. Completion & Process Outcomes
${outcomeNarrative}
`;

  return {
    title: name,
    executiveSummary,
    triggerNarrative,
    flowSteps,
    decisionNarratives,
    concurrencyNarratives,
    outcomeNarrative,
    fullMarkdown,
  };
}
