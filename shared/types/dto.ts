export interface ProcessDocumentDTO {
  documentId: string;
  name: string;
  content: string;
  sourceType: string;
}

export interface ProcessKnowledgeDTO {
  activities: string[];
  actors: string[];
  systems: string[];
  events: string[];
  decisions: string[];
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