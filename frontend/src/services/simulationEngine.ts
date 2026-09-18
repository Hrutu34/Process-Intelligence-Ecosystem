/**
 * ProcessIQ Discrete-Event & Monte Carlo Process Simulator
 * Domain-agnostic engine that models:
 * - Case arrivals (Poisson / Exponential or Uniform)
 * - Resource capacity constraints & FIFO Queues
 * - Activity duration distributions (Normal or Constant)
 * - Exclusive (XOR) Gateway branch routing with custom probabilities
 * - Parallel (AND) Gateway split & barrier join synchronization
 * - Rework loops and cost models (labor + fixed costs)
 * - Event log generation for Process Mining tools (Celonis, Disco, PM4Py)
 */

import type {
  ProcessTopology,
  SimulationParameters,
  SimulationResult,
  TaskParameter,
  ActivityBottleneck,
  HistogramBin,
  EventLogEntry,
  ExecutiveDiagnosis,
  SimulationSummary
} from './simulationTypes';

export function runDiscreteEventSimulation(
  parsedBpmn: ProcessTopology,
  parameters: SimulationParameters
): SimulationResult {
  const startTimeMs = performance.now();

  const global = parameters.global || {
    caseCount: 400,
    arrivalMeanMinutes: 20,
    arrivalDistribution: 'exponential',
    slaThresholdHours: 12,
    workHoursPerDay: 8
  };

  const caseCount = Math.max(10, Math.min(5000, Number(global.caseCount) || 400));
  const arrivalMean = Math.max(0.5, Number(global.arrivalMeanMinutes) || 20);
  const slaThresholdMin = (Number(global.slaThresholdHours) || 12) * 60;

  // Initialize Resource Pools for each task
  const taskResources: Record<string, number[]> = {};
  const taskMetrics: Record<
    string,
    {
      id: string;
      name: string;
      executions: number;
      totalWaitTime: number;
      totalServiceTime: number;
      totalCost: number;
      maxWaitTime: number;
      reworkCount: number;
      capacity: number;
    }
  > = {};

  parsedBpmn.tasks.forEach((task) => {
    const taskParam = parameters.tasks?.[task.id] || {
      id: task.id,
      name: task.name,
      resourceCapacity: 2,
      meanDuration: 30,
      stdDev: 8,
      distribution: 'normal',
      costPerExecution: 15,
      hourlyLaborRate: 35,
      reworkRate: 0.05
    };

    const capacity = Math.max(1, Number(taskParam.resourceCapacity) || 1);
    taskResources[task.id] = new Array(capacity).fill(0); // Timestamps when worker is next free

    taskMetrics[task.id] = {
      id: task.id,
      name: task.name || task.id,
      executions: 0,
      totalWaitTime: 0,
      totalServiceTime: 0,
      totalCost: 0,
      maxWaitTime: 0,
      reworkCount: 0,
      capacity
    };
  });

  const cases: Array<{
    caseId: string;
    arrivalTime: number;
    completeTime: number;
    cycleTimeMinutes: number;
    cycleTimeHours: number;
    waitTimeMinutes: number;
    serviceTimeMinutes: number;
    totalCost: number;
    slaBreached: boolean;
    activitiesCount: number;
  }> = [];

  const eventLog: EventLogEntry[] = [];
  let currentClock = 0;

  // Topological route graph
  const nodes = parsedBpmn.nodes;
  const flows = parsedBpmn.sequenceFlows;
  const startNodeIds =
    parsedBpmn.startNodeIds && parsedBpmn.startNodeIds.length > 0
      ? parsedBpmn.startNodeIds
      : Object.keys(nodes).slice(0, 1);

  // Helper: Sample Duration based on distribution (Normal or Constant)
  const sampleDuration = (taskParam: TaskParameter): number => {
    const mean = Math.max(0.5, Number(taskParam.meanDuration) || 15);
    const dist = taskParam.distribution || 'normal';

    if (dist === 'constant') {
      return mean;
    } else {
      // Normal distribution via Box-Muller transform
      const stdDev = Math.max(0.1, Number(taskParam.stdDev) || mean * 0.25);
      let u1 = Math.random();
      const u2 = Math.random();
      while (u1 === 0) u1 = Math.random(); // avoid log(0)
      const z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
      return Math.max(0.5, mean + z0 * stdDev);
    }
  };

  // Helper: Sample Inter-Arrival time
  const sampleArrivalDelta = (): number => {
    if (global.arrivalDistribution === 'constant') return arrivalMean;
    const u = Math.random();
    return Math.max(0.5, -arrivalMean * Math.log(1 - (u === 1 ? 0.9999 : u)));
  };

  // -------------------------------------------------------------
  // SIMULATION RUN: Process N Cases
  // -------------------------------------------------------------
  for (let caseNum = 1; caseNum <= caseCount; caseNum++) {
    const caseId = 'C-' + String(caseNum).padStart(4, '0');

    // Arrival time advances
    const arrivalDelta = sampleArrivalDelta();
    currentClock += arrivalDelta;
    const caseArrivalTime = currentClock;

    const caseStartTime = caseArrivalTime;
    let caseEndTime = caseArrivalTime;
    let caseTotalWait = 0;
    let caseTotalService = 0;
    let caseTotalCost = 0;
    const visitedActivities: string[] = [];

    // Queue of tokens currently traveling in this case instance
    const activeTokens: Array<{ nodeId: string; tokenTime: number }> = startNodeIds.map((startId) => ({
      nodeId: startId,
      tokenTime: caseArrivalTime
    }));

    // Join barrier map for parallel gateways: { gatewayId: [tokenTime, ...] }
    const parallelJoinState: Record<string, number[]> = {};

    let stepCount = 0;
    const MAX_STEPS = 150; // Safeguard against runaway loops

    while (activeTokens.length > 0 && stepCount < MAX_STEPS) {
      stepCount++;
      const currentToken = activeTokens.shift();
      if (!currentToken) continue;

      const node = nodes[currentToken.nodeId];
      if (!node) continue;

      let tokenTime = currentToken.tokenTime;

      // 1. Process TASK
      if (node.type === 'task') {
        const taskParam: TaskParameter = parameters.tasks?.[node.id] || {
          id: node.id,
          name: node.name,
          distribution: 'normal',
          meanDuration: 30,
          stdDev: 8,
          resourceCapacity: 2,
          costPerExecution: 15,
          hourlyLaborRate: 35,
          reworkRate: 0.05
        };

        const workers = taskResources[node.id] || [0];
        const capacity = workers.length;

        // Find earliest available worker in pool
        let earliestWorkerIdx = 0;
        let earliestTime = workers[0];
        for (let w = 1; w < capacity; w++) {
          if (workers[w] < earliestTime) {
            earliestTime = workers[w];
            earliestWorkerIdx = w;
          }
        }

        // Wait time in queue until worker is available
        const queueWaitTime = Math.max(0, earliestTime - tokenTime);
        const serviceStartTime = tokenTime + queueWaitTime;
        const duration = sampleDuration(taskParam);
        const serviceEndTime = serviceStartTime + duration;

        // Reserve worker
        workers[earliestWorkerIdx] = serviceEndTime;

        // Calculate Cost
        const fixedCost = Number(taskParam.costPerExecution) || 0;
        const laborCost = (duration / 60) * (Number(taskParam.hourlyLaborRate) || 0);
        const taskCost = fixedCost + laborCost;

        // Record Metrics
        const m = taskMetrics[node.id];
        if (m) {
          m.executions++;
          m.totalWaitTime += queueWaitTime;
          m.totalServiceTime += duration;
          m.totalCost += taskCost;
          if (queueWaitTime > m.maxWaitTime) m.maxWaitTime = queueWaitTime;
        }

        caseTotalWait += queueWaitTime;
        caseTotalService += duration;
        caseTotalCost += taskCost;
        visitedActivities.push(node.name || node.id);

        // Record to Event Log
        eventLog.push({
          caseId,
          activityId: node.id,
          activityName: node.name || node.id,
          queueEntryTime: Number(tokenTime.toFixed(2)),
          startTime: Number(serviceStartTime.toFixed(2)),
          completeTime: Number(serviceEndTime.toFixed(2)),
          waitTimeMinutes: Number(queueWaitTime.toFixed(2)),
          durationMinutes: Number(duration.toFixed(2)),
          cost: Number(taskCost.toFixed(2)),
          resource: `${node.name ? node.name.slice(0, 10) : 'Agent'}_#${earliestWorkerIdx + 1}`
        });

        // Advance token time
        tokenTime = serviceEndTime;

        // Check for Rework Loop
        const reworkRate = Number(taskParam.reworkRate) || 0;
        if (reworkRate > 0 && Math.random() < reworkRate && stepCount < MAX_STEPS - 2) {
          if (m) m.reworkCount++;
          activeTokens.push({ nodeId: node.id, tokenTime });
          continue;
        }
      }

      // Check if this is an End Event or terminal node (no outgoing sequence flows)
      if (node.type === 'endEvent' || !node.outgoing || node.outgoing.length === 0) {
        if (tokenTime > caseEndTime) caseEndTime = tokenTime;
        continue;
      }

      // 2. Process Exclusive Gateway (XOR)
      if (node.type === 'exclusiveGateway' && node.outgoing.length > 0) {
        const gwParam = parameters.gateways?.[node.id];
        let chosenFlowId = node.outgoing[0];

        if (gwParam && gwParam.branches && gwParam.branches.length > 0) {
          // Monte Carlo roulette wheel selection
          const rand = Math.random();
          let cumulative = 0;
          for (const branch of gwParam.branches) {
            cumulative += branch.probability;
            if (rand <= cumulative) {
              chosenFlowId = branch.flowId;
              break;
            }
          }
        } else if (node.outgoing.length > 1) {
          // Default uniform random pick
          const idx = Math.floor(Math.random() * node.outgoing.length);
          chosenFlowId = node.outgoing[idx];
        }

        const nextFlow = flows[chosenFlowId];
        if (nextFlow && nodes[nextFlow.targetRef]) {
          activeTokens.push({
            nodeId: nextFlow.targetRef,
            tokenTime
          });
        }
        continue;
      }

      // 3. Process Parallel Gateway (AND)
      if (node.type === 'parallelGateway') {
        // Parallel Join Barrier (multiple incoming flows)
        if (node.incoming && node.incoming.length > 1) {
          if (!parallelJoinState[node.id]) parallelJoinState[node.id] = [];
          parallelJoinState[node.id].push(tokenTime);

          // If not all incoming branches arrived, wait
          if (parallelJoinState[node.id].length < node.incoming.length) {
            continue;
          }

          // All parallel branches arrived: synchronize at max token arrival time
          tokenTime = Math.max(...parallelJoinState[node.id]);
          delete parallelJoinState[node.id]; // Reset for this case
        }

        // Parallel Fork (multiple outgoing flows)
        if (node.outgoing) {
          node.outgoing.forEach((flowId) => {
            const flow = flows[flowId];
            if (flow && nodes[flow.targetRef]) {
              activeTokens.push({
                nodeId: flow.targetRef,
                tokenTime
              });
            }
          });
        }
        continue;
      }

      // 4. Default / Sequence flow forward (Tasks, Start events, intermediate events)
      if (node.outgoing) {
        node.outgoing.forEach((flowId) => {
          const flow = flows[flowId];
          if (flow && nodes[flow.targetRef]) {
            activeTokens.push({
              nodeId: flow.targetRef,
              tokenTime
            });
          }
        });
      }
    }

    const totalCycleTimeMin = Math.max(1, caseEndTime - caseStartTime);
    const slaBreached = totalCycleTimeMin > slaThresholdMin;

    cases.push({
      caseId,
      arrivalTime: Number(caseStartTime.toFixed(2)),
      completeTime: Number(caseEndTime.toFixed(2)),
      cycleTimeMinutes: Number(totalCycleTimeMin.toFixed(2)),
      cycleTimeHours: Number((totalCycleTimeMin / 60).toFixed(2)),
      waitTimeMinutes: Number(caseTotalWait.toFixed(2)),
      serviceTimeMinutes: Number(caseTotalService.toFixed(2)),
      totalCost: Number(caseTotalCost.toFixed(2)),
      slaBreached,
      activitiesCount: visitedActivities.length
    });
  }

  // -------------------------------------------------------------
  // AGGREGATE METRICS & BOTTLE-NECK ANALYSIS
  // -------------------------------------------------------------
  const maxCompleteTime = cases.length > 0 ? Math.max(...cases.map((c) => c.completeTime)) : currentClock;
  const simHorizonMinutes = Math.max(1, maxCompleteTime);

  // Activity bottleneck statistics
  const activityBottlenecks: ActivityBottleneck[] = Object.values(taskMetrics).map((m) => {
    const avgWaitMin = m.executions > 0 ? m.totalWaitTime / m.executions : 0;
    const avgServiceMin = m.executions > 0 ? m.totalServiceTime / m.executions : 0;

    // Resource Utilization Rate: (total busy minutes) / (capacity * simulation horizon)
    const theoreticalMaxMinutes = m.capacity * simHorizonMinutes;
    const utilization =
      theoreticalMaxMinutes > 0
        ? Math.min(100, Number(((m.totalServiceTime / theoreticalMaxMinutes) * 100).toFixed(1)))
        : 0;

    return {
      id: m.id,
      name: m.name,
      executions: m.executions,
      avgWaitMinutes: Number(avgWaitMin.toFixed(2)),
      avgServiceMinutes: Number(avgServiceMin.toFixed(2)),
      maxWaitMinutes: Number(m.maxWaitTime.toFixed(2)),
      utilizationPercent: utilization,
      bottleneckScore: 0, // calculated next
      capacity: m.capacity,
      cost: Number(m.totalCost.toFixed(2))
    };
  });

  // Calculate relative bottleneck score (0 to 100)
  const maxWait = Math.max(...activityBottlenecks.map((a) => a.avgWaitMinutes), 1);
  activityBottlenecks.forEach((a) => {
    const waitFactor = (a.avgWaitMinutes / maxWait) * 70;
    const utilFactor = (a.utilizationPercent / 100) * 30;
    a.bottleneckScore = Math.min(100, Math.round(waitFactor + utilFactor));
  });

  // Sort bottlenecks descending
  activityBottlenecks.sort((a, b) => b.bottleneckScore - a.bottleneckScore);
  const primaryBottleneck = activityBottlenecks[0] || null;

  // End-to-end Cycle time percentiles
  const sortedCycleHours = cases.map((c) => c.cycleTimeHours).sort((a, b) => a - b);
  const minCycle = sortedCycleHours[0] || 0;
  const maxCycle = sortedCycleHours[sortedCycleHours.length - 1] || 0;

  const totalCost = cases.reduce((acc, c) => acc + c.totalCost, 0);
  const avgCostPerCase = cases.length > 0 ? totalCost / cases.length : 0;
  const avgCycleHours =
    cases.length > 0 ? cases.reduce((acc, c) => acc + c.cycleTimeHours, 0) / cases.length : 0;

  const breachedCount = cases.filter((c) => c.slaBreached).length;
  const slaAdherencePercent =
    cases.length > 0 ? Number((((cases.length - breachedCount) / cases.length) * 100).toFixed(1)) : 100;
  const slaThresholdHours = Number((slaThresholdMin / 60).toFixed(1));

  // 10-bin histogram of Cycle Time for Recharts
  const binCount = 10;
  const binStep = Math.max(0.1, (maxCycle - minCycle) / binCount);
  const histogram: HistogramBin[] = [];
  let slaBinRange: string | undefined;

  for (let i = 0; i < binCount; i++) {
    const rangeStart = Number((minCycle + i * binStep).toFixed(1));
    const rangeEnd = Number((minCycle + (i + 1) * binStep).toFixed(1));
    const count = cases.filter((c) => {
      if (i === binCount - 1) return c.cycleTimeHours >= rangeStart && c.cycleTimeHours <= rangeEnd;
      return c.cycleTimeHours >= rangeStart && c.cycleTimeHours < rangeEnd;
    }).length;

    const rangeKey = `${rangeStart}-${rangeEnd}h`;

    if (slaThresholdHours >= rangeStart && slaThresholdHours <= rangeEnd && !slaBinRange) {
      slaBinRange = rangeKey;
    }

    histogram.push({
      range: rangeKey,
      count,
      isBreached: rangeEnd > slaThresholdHours
    });
  }

  if (!slaBinRange && histogram.length > 0) {
    if (slaThresholdHours < minCycle) {
      slaBinRange = histogram[0].range;
    } else {
      slaBinRange = histogram[histogram.length - 1].range;
    }
  }

  const executionTimeMs = performance.now() - startTimeMs;

  const summary: SimulationSummary = {
    totalCases: cases.length,
    completedCases: cases.length,
    simulationHorizonHours: Number((simHorizonMinutes / 60).toFixed(1)),
    avgCycleTimeHours: Number(avgCycleHours.toFixed(2)),
    minCycleTimeHours: Number(minCycle.toFixed(2)),
    maxCycleTimeHours: Number(maxCycle.toFixed(2)),
    slaThresholdHours,
    slaBinRange,
    slaAdherencePercent,
    slaBreachedCount: breachedCount,
    primaryBottleneck: primaryBottleneck ? primaryBottleneck.name : 'None',
    totalProcessCost: Math.round(totalCost),
    avgCostPerCase: Number(avgCostPerCase.toFixed(2)),
    executiveDiagnosis: {} as ExecutiveDiagnosis
  };

  // Generate contextual AI diagnosis for the executive dashboard
  const executiveDiagnosis = generateExecutiveProcessDiagnosis(
    parsedBpmn,
    parameters,
    summary,
    activityBottlenecks
  );
  summary.executiveDiagnosis = executiveDiagnosis;

  return {
    summary,
    activityBottlenecks,
    histogram,
    cases: cases.slice(0, 300).map((c) => ({
      caseId: c.caseId,
      startTime: c.arrivalTime,
      endTime: c.completeTime,
      cycleTimeMinutes: c.cycleTimeMinutes,
      cycleTimeHours: c.cycleTimeHours,
      isSlaBreached: c.slaBreached,
      totalCost: c.totalCost
    })),
    eventLog: eventLog.slice(0, 1000),
    executionTimeMs: Math.round(executionTimeMs)
  };
}

/**
 * Synthesizes an executive, domain-aware operational diagnosis
 */
export function generateExecutiveProcessDiagnosis(
  parsedBpmn: ProcessTopology,
  parameters: SimulationParameters,
  summary: SimulationSummary,
  activityBottlenecks: ActivityBottleneck[]
): ExecutiveDiagnosis {
  const domainName = parameters.global?.domainName || parsedBpmn?.name || 'Business Process';
  const entityName = parameters.global?.entityName || 'cases';
  const entitySingular = parameters.global?.entitySingular || 'case';
  const primary = activityBottlenecks && activityBottlenecks[0];

  if (!primary || primary.executions === 0) {
    return {
      severity: 'healthy',
      headline: `Simulation completed smoothly for ${summary.totalCases} ${entityName}`,
      rootCause: `All activities maintained stable queue throughput with high (${summary.slaAdherencePercent}%) SLA adherence.`,
      recommendation: 'Current headcount and machine capacities are well balanced.',
      bottleneckTask: 'None',
      entityName,
      entitySingular
    };
  }

  const isSevere = summary.slaAdherencePercent < 65 || primary.bottleneckScore >= 65 || primary.avgWaitMinutes > 20;
  const isModerate = summary.slaAdherencePercent < 85 || primary.bottleneckScore >= 35 || primary.avgWaitMinutes > 6;

  const severity: 'critical' | 'warning' | 'healthy' = isSevere ? 'critical' : isModerate ? 'warning' : 'healthy';

  const totalActivityTime = primary.avgWaitMinutes + primary.avgServiceMinutes || 1;
  const waitRatioPct = Math.min(99, Math.round((primary.avgWaitMinutes / totalActivityTime) * 100));

  let headline = '';
  let rootCause = '';
  let recommendation = '';

  if (severity === 'critical') {
    headline = `Critical Bottleneck Identified at "${primary.name}"`;
    rootCause = `In this ${domainName}, ${entityName} spend an average of ${primary.avgWaitMinutes} min waiting in line at "${primary.name}" (${waitRatioPct}% of its total cycle time), pulling overall on-time rate down to ${summary.slaAdherencePercent}%. Resource utilization is stressed at ${primary.utilizationPercent}%.`;
    recommendation = `Increase capacity for "${primary.name}" from ${primary.capacity} to ${primary.capacity + 1} workers, or convert manual steps into automated tasks.`;
  } else if (severity === 'warning') {
    headline = `Moderate Delay Notice at "${primary.name}"`;
    rootCause = `"${primary.name}" is the primary constraint with an average queue wait of ${primary.avgWaitMinutes} min. Process SLA adherence is currently ${summary.slaAdherencePercent}%.`;
    recommendation = `Consider adding 1 additional worker to "${primary.name}" or reducing average processing time by 15-20% to achieve >90% SLA compliance.`;
  } else {
    headline = `Healthy Throughput across all ${domainName} stages`;
    rootCause = `Process is operating with ${summary.slaAdherencePercent}% SLA compliance across ${summary.totalCases} ${entityName}. Longest queue wait is only ${primary.avgWaitMinutes} min at "${primary.name}".`;
    recommendation = `The current operational parameters are optimal. You can test reducing worker headcount to identify potential cost savings.`;
  }

  return {
    severity,
    headline,
    rootCause,
    recommendation,
    bottleneckTask: primary.name,
    entityName,
    entitySingular
  };
}

/**
 * Compares two simulation scenarios
 */
export function compareScenarios(baseline: SimulationResult, whatIf: SimulationResult) {
  if (!baseline || !whatIf) return null;

  const b = baseline.summary;
  const w = whatIf.summary;

  const calcDelta = (baseVal: number, whatIfVal: number, lowerIsBetter = true) => {
    const diff = whatIfVal - baseVal;
    const pct = baseVal !== 0 ? (diff / baseVal) * 100 : 0;
    const isImprovement = lowerIsBetter ? diff < 0 : diff > 0;
    return {
      diff: Number(diff.toFixed(2)),
      pct: Number(pct.toFixed(1)),
      isImprovement
    };
  };

  return {
    cycleTime: calcDelta(b.avgCycleTimeHours, w.avgCycleTimeHours, true),
    cost: calcDelta(b.avgCostPerCase, w.avgCostPerCase, true),
    slaAdherence: calcDelta(b.slaAdherencePercent, w.slaAdherencePercent, false),
    bottleneckShift: {
      from: b.primaryBottleneck,
      to: w.primaryBottleneck,
      changed: b.primaryBottleneck !== w.primaryBottleneck
    }
  };
}
