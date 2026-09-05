export interface ProcessDocumentDTO {
  documentId: string;
  name: string;
  content: string;
  sourceType: string;
}

export interface ProcessKnowledgeDTO {
  activities: string[];
  actors: string[];
  roles: string[];
  systems: string[];
  events: string[];
  gateways: string[];
  inputs: string[];
  outputs: string[];
  businessRules: string[];
  risks: string[];
  conflicts: string[];
}

export interface ClassificationResultDTO {
  category: string;
  confidence: number;
}

export type NodeType =
  | 'Activity'
  | 'Event'
  | 'Gateway'
  | 'Role'
  | 'System'
  | 'DataArtifact';

export type EdgeType =
  | 'sequence'
  | 'conditional'
  | 'association';

export type GatewayType = 'exclusive' | 'parallel' | 'inclusive';

export type EventType = 'start' | 'intermediate' | 'end' | 'timer';

export interface NodeMetadata {
  roleRef?: string;
  systemRef?: string;
  gatewayType?: GatewayType;
  eventType?: EventType;
  [key: string]: unknown;
}

export interface GraphNode {
  id: string;
  type: NodeType;
  label: string;
  metadata?: NodeMetadata;
}

export interface GraphEdge {
  id: string;
  from: string;
  to: string;
  edgeType: EdgeType;
  label?: string | null;
  confidence?: number | null;
}

export interface ProcessGraphDTO {
  graphId: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface ValidationResultDTO {
  issues: string[];
  warnings: string[];
  recommendations: string[];
}

export interface ValidationIssueDTO {
  ruleId: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'WARNING';
  elementId?: string | null;
  issue: string;
  suggestion: string;
}

export interface ProcessQualityReportDTO {
  valid: boolean;
  qualityScore: number;
  issues: ValidationIssueDTO[];
  recommendations: string[];
}

export interface ReviewReportDTO {
  summary: string;
  issues: string[];
  recommendations: string[];
}