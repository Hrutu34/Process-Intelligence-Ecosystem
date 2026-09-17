import type {
  ProcessKnowledgeDTO,
  ProcessGraphDTO,
  ProcessQualityReportDTO,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';

const BACKEND_URL = 'http://localhost:8080';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface SelectedElementInfo {
  id: string;
  name: string;
  type: string;
  incoming?: string[];
  outgoing?: string[];
  x?: number;
  y?: number;
}

export interface ValidationSummary {
  valid: boolean;
  qualityScore: number;
  issueCount: number;
  topIssues?: string[];
}

export interface ChatContext {
  processName?: string;
  bpmnXml?: string;
  knowledge?: ProcessKnowledgeDTO | null;
  graph?: ProcessGraphDTO | null;
  qualityReport?: ProcessQualityReportDTO | null;
  selectedElement?: SelectedElementInfo | null;
  sourceText?: string;
}

export interface ChatRequest {
  question: string;
  history: ChatMessage[];
  context: ChatContext;
}

export interface ChatResponse {
  answer: string;
  updatedXml?: string | null;
  currentVersion?: number | null;
}

export interface EditOperation {
  op: string;
  [key: string]: unknown;
}

export interface ChatEditRequest {
  question: string;
  history: ChatMessage[];
  context: ChatContext;
  autoApply: boolean;
}

export interface ChatEditResponse {
  plan: string;
  steps?: string[];
  operations: EditOperation[];
  updatedXml: string | null;
  applied: boolean;
  error: string | null;
  validation?: ValidationSummary | null;
  currentVersion?: number | null;
}

export interface ApplyEditsRequest {
  bpmnXml: string;
  operations: EditOperation[];
  plan?: string;
  processId?: string;
}

export interface ApplyEditsResponse {
  updatedXml: string | null;
  applied: string[];
  failed: string[];
  validation?: ValidationSummary | null;
  currentVersion?: number | null;
}

export interface UndoResponse {
  success: boolean;
  message: string;
  updatedXml: string | null;
  currentVersion?: number | null;
}

export interface VersionEntry {
  versionNumber: number;
  description: string;
  timestamp: string;
  operations: EditOperation[];
}

export interface VersionStateResponse {
  currentVersionIndex: number;
  history: VersionEntry[];
}

class ChatService {
  async ask(request: ChatRequest): Promise<ChatResponse> {
    const response = await fetch(`${BACKEND_URL}/api/v1/process/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      throw new Error(`Chat request failed with HTTP ${response.status}`);
    }
    return await response.json();
  }

  async askEdit(request: ChatEditRequest): Promise<ChatEditResponse> {
    const response = await fetch(`${BACKEND_URL}/api/v1/process/chat-edit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      throw new Error(`Chat edit request failed with HTTP ${response.status}`);
    }
    return await response.json();
  }

  async applyEdits(request: ApplyEditsRequest): Promise<ApplyEditsResponse> {
    const response = await fetch(`${BACKEND_URL}/api/v1/process/apply-edits`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      throw new Error(`Apply edits request failed with HTTP ${response.status}`);
    }
    return await response.json();
  }

  async undo(processId?: string, steps: number = 1): Promise<UndoResponse> {
    const response = await fetch(`${BACKEND_URL}/api/v1/process/undo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ processId, steps }),
    });
    if (!response.ok) {
      throw new Error(`Undo request failed with HTTP ${response.status}`);
    }
    return await response.json();
  }

  async redo(processId?: string, steps: number = 1): Promise<UndoResponse> {
    const response = await fetch(`${BACKEND_URL}/api/v1/process/redo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ processId, steps }),
    });
    if (!response.ok) {
      throw new Error(`Redo request failed with HTTP ${response.status}`);
    }
    return await response.json();
  }

  async getVersions(processId?: string): Promise<VersionStateResponse> {
    const p = processId ? `?processId=${encodeURIComponent(processId)}` : '';
    const response = await fetch(`${BACKEND_URL}/api/v1/process/versions${p}`);
    if (!response.ok) {
      throw new Error(`Get versions failed with HTTP ${response.status}`);
    }
    return await response.json();
  }
}

export const chatService = new ChatService();
