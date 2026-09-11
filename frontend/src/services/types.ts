import type {
  ProcessKnowledgeDTO,
  CanonicalProcessGraph,
  GraphNode,
  GraphEdge
} from '../../../backend/src/main/java/com/pie/shared/types/dto';

export type { ProcessKnowledgeDTO, CanonicalProcessGraph, GraphNode, GraphEdge };

export interface UserSession {
  id: string;
  name: string;
  email: string;
  role: string;
  avatarUrl?: string;
  isLoggedIn: boolean;
}

export type ProcessStatus = 'Draft' | 'Processing' | 'Validated' | 'Needs Review' | 'Published';

export interface ProcessVersion {
  version: string;
  timestamp: string;
  summary: string;
  qualityScore: number;
  author: string;
}

export interface ProcessInsight {
  type: 'bottleneck' | 'automation' | 'risk' | 'sla';
  title: string;
  description: string;
  impact: 'High' | 'Medium' | 'Low';
  suggestion?: string;
}

export interface ValidationIssue {
  id: string;
  category: 'Structure' | 'Activities' | 'Ownership' | 'Gateways' | 'Events';
  severity: 'Critical' | 'Warning' | 'Info';
  title: string;
  description: string;
  affectedNodeId?: string;
  suggestedFix?: string;
  isApplied?: boolean;
}

export interface SourceTrace {
  entityName: string;
  entityType: string;
  sourceText: string;
  documentName: string;
  pageOrSection?: string;
}

export interface ProcessEntity {
  id: string;
  name: string;
  description: string;
  sourceDocument: string;
  sourceType: 'PDF' | 'DOCX' | 'TXT' | 'FreeText' | 'BPMN';
  rawText?: string;
  createdAt: string;
  lastUpdated: string;
  status: ProcessStatus;
  qualityScore: number;
  currentVersion: string;
  versions: ProcessVersion[];
  knowledge: ProcessKnowledgeDTO;
  graph?: CanonicalProcessGraph;
  bpmnXml?: string;
  narrative?: {
    executiveSummary: string;
    triggerNarrative: string;
    flowSteps: string[];
    decisionNarratives: string[];
    concurrencyNarratives: string[];
    outcomeNarrative: string;
    fullMarkdown: string;
  };
  insights: ProcessInsight[];
  validationIssues: ValidationIssue[];
  sourceTraces: SourceTrace[];
  aiSummary?: {
    executiveSummary: string;
    auditReadinessScore: number;
    auditStatus: 'Audit Ready' | 'Action Required' | 'Non-Compliant';
    recommendations: string[];
    complianceNotes: string[];
  };
}

export interface DocumentItem {
  id: string;
  name: string;
  size: string;
  type: 'PDF' | 'DOCX' | 'TXT' | 'FreeText' | 'BPMN';
  uploadedAt: string;
  status: 'Uploaded' | 'Processing' | 'Completed' | 'Failed';
  extractedEntitiesCount: number;
  linkedProcessId?: string;
  linkedProcessName?: string;
  fileSnippet?: string;
}

export interface KnowledgeActorItem {
  name: string;
  role: string;
  processes: string[];
  documents: string[];
  activityCount: number;
}

export interface KnowledgeActivityItem {
  name: string;
  assignedRole: string;
  processName: string;
  documentName: string;
  isAutomated: boolean;
}

export interface KnowledgeDecisionItem {
  name: string;
  decisionType: 'Exclusive' | 'Parallel' | 'Inclusive';
  processName: string;
  conditionCount: number;
}

export interface GlobalKnowledgeStats {
  actorsCount: number;
  activitiesCount: number;
  decisionsCount: number;
  eventsCount: number;
  systemsCount: number;
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  timestamp: string;
  suggestedActions?: string[];
}
