import React, { useEffect, useRef, useState } from 'react';
import type {
  ProcessKnowledgeDTO,
  CanonicalProcessGraph,
  ProcessQualityReportDTO,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { chatService, type ChatMessage } from '../services/chatService';
import './ChatDock.css';

interface Props {
  processName?: string;
  bpmnXml?: string | null;
  knowledge?: ProcessKnowledgeDTO | null;
  graph?: CanonicalProcessGraph | null;
  qualityReport?: ProcessQualityReportDTO | null;
}

const SUGGESTED_PROMPTS = [
  'Summarize this process in 3 sentences.',
  'What are the biggest quality issues?',
  'Explain the decision gateways.',
  'Suggest fixes for the flagged defects.',
];

export const ChatDock: React.FC<Props> = ({
  processName,
  bpmnXml,
  knowledge,
  graph,
  qualityReport,
}) => {
  const [open, setOpen] = useState<boolean>(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, loading, open]);

  const hasContext = Boolean(bpmnXml || knowledge || graph);

  const send = async (rawText?: string) => {
    const question = (rawText ?? input).trim();
    if (!question || loading) return;

    const nextHistory: ChatMessage[] = [...messages, { role: 'user', content: question }];
    setMessages(nextHistory);
    setInput('');
    setLoading(true);
    setError(null);

    try {
      const answer = await chatService.ask({
        question,
        history: messages,
        context: {
          processName,
          bpmnXml: bpmnXml || undefined,
          knowledge,
          graph,
          qualityReport,
        },
      });
      setMessages([...nextHistory, { role: 'assistant', content: answer }]);
    } catch {
      setError('Could not reach the AI service. Check that the backend and Ollama are running.');
      setMessages([
        ...nextHistory,
        {
          role: 'assistant',
          content:
            'Sorry — I could not reach the AI service. Please make sure the backend and Ollama are running, then try again.',
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleKey = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  const clearChat = () => {
    setMessages([]);
    setError(null);
  };

  return (
    <div className={`chat-dock ${open ? 'chat-dock-open' : ''}`}>
      {open && (
        <div className="chat-dock-panel">
          <div className="chat-dock-header">
            <div className="chat-dock-title">
              <span className="chat-dock-dot" />
              <div>
                <strong>Process Copilot</strong>
                <div className="chat-dock-subtitle">
                  {hasContext
                    ? `Grounded on: ${processName || 'current process'}`
                    : 'No process loaded — ask general BPMN questions'}
                </div>
              </div>
            </div>
            <div className="chat-dock-header-actions">
              <button
                type="button"
                className="chat-dock-icon-btn"
                onClick={clearChat}
                title="Clear conversation"
              >
                ⟲
              </button>
              <button
                type="button"
                className="chat-dock-icon-btn"
                onClick={() => setOpen(false)}
                title="Minimize"
              >
                ✕
              </button>
            </div>
          </div>

          <div className="chat-dock-body" ref={scrollRef}>
            {messages.length === 0 && (
              <div className="chat-dock-empty">
                <div className="chat-dock-empty-title">Ask about your process</div>
                <p>
                  I can explain the flow, describe roles and decisions, list quality defects,
                  and suggest fixes based on the currently loaded BPMN model.
                </p>
                <div className="chat-dock-suggestions">
                  {SUGGESTED_PROMPTS.map((prompt) => (
                    <button
                      type="button"
                      key={prompt}
                      className="chat-dock-suggestion"
                      onClick={() => send(prompt)}
                      disabled={loading}
                    >
                      {prompt}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((m, idx) => (
              <div key={idx} className={`chat-msg chat-msg-${m.role}`}>
                <div className="chat-msg-role">{m.role === 'user' ? 'You' : 'Copilot'}</div>
                <div className="chat-msg-content">{m.content}</div>
              </div>
            ))}

            {loading && (
              <div className="chat-msg chat-msg-assistant">
                <div className="chat-msg-role">Copilot</div>
                <div className="chat-msg-content chat-msg-loading">
                  <span className="dot" />
                  <span className="dot" />
                  <span className="dot" />
                </div>
              </div>
            )}
          </div>

          {error && <div className="chat-dock-error">{error}</div>}

          <div className="chat-dock-input-row">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKey}
              placeholder={
                hasContext
                  ? 'Ask about this process...'
                  : 'Ask a BPMN question...'
              }
              rows={2}
              disabled={loading}
            />
            <button
              type="button"
              className="chat-dock-send"
              onClick={() => send()}
              disabled={loading || !input.trim()}
            >
              {loading ? '...' : 'Send'}
            </button>
          </div>
        </div>
      )}

      <button
        type="button"
        className="chat-dock-fab"
        onClick={() => setOpen((v) => !v)}
        title={open ? 'Close copilot' : 'Open copilot'}
      >
        {open ? '×' : '💬'}
      </button>
    </div>
  );
};
