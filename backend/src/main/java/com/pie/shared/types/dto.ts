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

export interface ProcessNode {
  id: string;
  type: string;
  label: string;
}

export interface ProcessEdge {
  fromId: string;
  toId: string;
  condition?: string;
}

export interface ProcessGraphDTO {
  nodes: ProcessNode[];
  edges: ProcessEdge[];
}

export interface ValidationResultDTO {
  issues: string[];
  warnings: string[];
  recommendations: string[];
}

export interface ReviewReportDTO {
  summary: string;
  issues: string[];
  recommendations: string[];
}