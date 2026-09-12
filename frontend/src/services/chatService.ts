import type {
  ProcessKnowledgeDTO,
  CanonicalProcessGraph,
  ProcessQualityReportDTO,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';

const BACKEND_URL = 'http://localhost:8080';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatContext {
  processName?: string;
  bpmnXml?: string;
  knowledge?: ProcessKnowledgeDTO | null;
  graph?: CanonicalProcessGraph | null;
  qualityReport?: ProcessQualityReportDTO | null;
}

export interface ChatRequest {
  question: string;
  history: ChatMessage[];
  context: ChatContext;
}

export interface ChatResponse {
  answer: string;
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
  operations: EditOperation[];
  updatedXml: string | null;
  applied: boolean;
  error: string | null;
}

export interface ApplyEditsRequest {
  bpmnXml: string;
  operations: EditOperation[];
}

export interface ApplyEditsResponse {
  updatedXml: string | null;
  applied: string[];
  failed: string[];
}

class ChatService {
  async ask(request: ChatRequest): Promise<string> {
    const response = await fetch(`${BACKEND_URL}/api/v1/process/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      throw new Error(`Chat request failed with HTTP ${response.status}`);
    }
    const data: ChatResponse = await response.json();
    return data.answer || 'No response received.';
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
}

export const chatService = new ChatService();
