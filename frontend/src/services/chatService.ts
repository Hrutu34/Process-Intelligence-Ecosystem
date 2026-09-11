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

class ChatService {
  async ask(request: ChatRequest): Promise<string> {
    try {
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
    } catch (err) {
      console.error('Chat service error:', err);
      throw err;
    }
  }
}

export const chatService = new ChatService();
