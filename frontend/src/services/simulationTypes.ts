/**
 * Process Mining & Discrete Event Simulation Engine Data Types
 */

export interface ProcessTopology {
  id?: string;
  name?: string;
  tasks: Array<{ id: string; name: string; incoming?: string[]; outgoing?: string[] }>;
  gateways: Array<{
    id: string;
    name: string;
    type: 'exclusiveGateway' | 'parallelGateway' | 'inclusiveGateway';
    incoming: string[];
    outgoing: string[];
  }>;
  events: Array<{
    id: string;
    name: string;
    type: 'startEvent' | 'endEvent' | 'intermediateEvent';
    incoming?: string[];
    outgoing?: string[];
  }>;
  sequenceFlows: Record<string, { id: string; name: string; sourceRef: string; targetRef: string }>;
  nodes: Record<string, { id: string; name: string; type: string; incoming: string[]; outgoing: string[] }>;
  startNodeIds: string[];
  endNodeIds: string[];
}

export type DurationDistribution = 'normal' | 'constant';

export interface TaskParameter {
  id: string;
  name: string;
  distribution: DurationDistribution;
  meanDuration: number;
  stdDev: number;
  minDuration?: number;
  maxDuration?: number;
  resourceCapacity: number;
  hourlyLaborRate: number;
  costPerExecution: number;
  reworkRate: number;
  rationale?: string;
}

export interface GatewayBranchParameter {
  flowId: string;
  name: string;
  probability: number;
  ruleDescription?: string;
}

export interface GatewayParameter {
  id: string;
  name: string;
  branches: GatewayBranchParameter[];
}

export interface GlobalSimulationParameter {
  caseCount: number;
  arrivalMeanMinutes: number;
  arrivalDistribution: 'exponential' | 'poisson' | 'uniform' | 'constant';
  slaThresholdHours: number;
  workHoursPerDay?: number;
  domainName?: string;
  entityName?: string;
  entitySingular?: string;
  caseCountLabel?: string;
  arrivalLabel?: string;
  slaLabel?: string;
  arrivalHelpText?: string;
  slaHelpText?: string;
  domain?: string;
  domainSummary?: string;
  keyBottleneckPrediction?: string;
}

export interface SimulationParameters {
  global: GlobalSimulationParameter;
  tasks: Record<string, TaskParameter>;
  gateways: Record<string, GatewayParameter>;
  meta?: {
    inferredBy?: string;
    timestamp?: string;
    domain?: string;
    summary?: string;
    predictedBottleneck?: string;
  };
}

export interface ExecutiveDiagnosis {
  severity: 'critical' | 'warning' | 'healthy';
  headline: string;
  rootCause: string;
  recommendation: string;
  bottleneckTask: string;
  entityName: string;
  entitySingular: string;
}

export interface ActivityBottleneck {
  id: string;
  name: string;
  executions: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
  maxWaitMinutes: number;
  utilizationPercent: number;
  bottleneckScore: number;
  capacity: number;
  cost: number;
}

export interface HistogramBin {
  range: string;
  count: number;
  isBreached: boolean;
}

export interface EventLogEntry {
  caseId: string;
  activityId: string;
  activityName: string;
  queueEntryTime: number;
  startTime: number;
  completeTime: number;
  waitTimeMinutes: number;
  durationMinutes: number;
  cost: number;
  resource: string;
}

export interface SimulationSummary {
  totalCases: number;
  completedCases: number;
  simulationHorizonHours: number;
  avgCycleTimeHours: number;
  avgWaitTimeHours?: number;
  avgServiceTimeHours?: number;
  minCycleTimeHours: number;
  maxCycleTimeHours: number;
  slaThresholdHours: number;
  slaAdherencePercent: number;
  slaBreachedCount: number;
  slaBinRange?: string;
  primaryBottleneck: string;
  totalProcessCost: number;
  avgCostPerCase: number;
  executiveDiagnosis: ExecutiveDiagnosis;
}

export interface SimulationResult {
  summary: SimulationSummary;
  activityBottlenecks: ActivityBottleneck[];
  histogram: HistogramBin[];
  eventLog: EventLogEntry[];
  cases: Array<{
    caseId: string;
    startTime: number;
    endTime: number;
    cycleTimeMinutes: number;
    cycleTimeHours: number;
    isSlaBreached: boolean;
    totalCost: number;
  }>;
  executionTimeMs: number;
}

export interface DynamicChartSpec {
  chartTitle: string;
  chartSubtitle?: string;
  chartType: 'bar' | 'line' | 'area' | 'radar';
  xAxisKey: string;
  yAxisKeys: Array<{
    key: string;
    name: string;
    color?: string;
    stackId?: string;
  }>;
  data: Array<Record<string, string | number>>;
  managerialInsight?: string;
}
