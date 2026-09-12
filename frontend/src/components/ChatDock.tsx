import React, { useEffect, useRef, useState } from 'react';
import type {
  ProcessKnowledgeDTO,
  CanonicalProcessGraph,
  ProcessQualityReportDTO,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { chatService, type ChatMessage, type EditOperation } from '../services/chatService';
import './ChatDock.css';

interface Props {
  processName?: string;
  bpmnXml?: string | null;
  knowledge?: ProcessKnowledgeDTO | null;
  graph?: CanonicalProcessGraph | null;
  qualityReport?: ProcessQualityReportDTO | null;
  onBpmnUpdated?: (xml: string) => void;
}

type ChatMode = 'ask' | 'edit';

interface PendingEdit {
  plan: string;
  operations: EditOperation[];
  proposedXml?: string;
}

const SUGGESTED_ASK = [
  'Summarize this process in 3 sentences.',
  'What defects are in the quality report?',
  'Explain the decision gateways.',
  'What is the biggest risk in this process?',
];

const SUGGESTED_EDIT = [
  'Add an end event after the last task.',
  'Rename the vague task to something concrete.',
  'Add a start event before the first task.',
  'Delete the orphan task.',
];

export const ChatDock: React.FC<Props> = ({
  processName,
  bpmnXml,
  knowledge,
  graph,
  qualityReport,
  onBpmnUpdated,
}) => {
  const [open, setOpen] = useState<boolean>(false);
  const [mode, setMode] = useState<ChatMode>('ask');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pendingEdit, setPendingEdit] = useState<PendingEdit | null>(null);
  const [input, setInput] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [downloadReady, setDownloadReady] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, loading, open, pendingEdit]);

  const hasContext = Boolean(bpmnXml || knowledge || graph);
  const suggestions = mode === 'edit' ? SUGGESTED_EDIT : SUGGESTED_ASK;

  const send = async (rawText?: string) => {
    const question = (rawText ?? input).trim();
    if (!question || loading) return;

    const nextHistory: ChatMessage[] = [...messages, { role: 'user', content: question }];
    setMessages(nextHistory);
    setInput('');
    setLoading(true);
    setError(null);

    try {
      if (mode === 'ask') {
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
      } else {
        const editResponse = await chatService.askEdit({
          question,
          history: messages,
          context: {
            processName,
            bpmnXml: bpmnXml || undefined,
            knowledge,
            graph,
            qualityReport,
          },
          autoApply: false,
        });

        const opsSummary = editResponse.operations && editResponse.operations.length > 0
          ? `\n\nProposed changes:\n${editResponse.operations
              .map((o, i) => `${i + 1}. ${o.op}${describeOp(o)}`)
              .join('\n')}`
          : '';

        setMessages([
          ...nextHistory,
          {
            role: 'assistant',
            content:
              `${editResponse.plan}${opsSummary}${
                editResponse.error ? `\n\n⚠ ${editResponse.error}` : ''
              }`,
          },
        ]);

        if (editResponse.operations && editResponse.operations.length > 0) {
          setPendingEdit({ plan: editResponse.plan, operations: editResponse.operations });
        }
      }
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

  const applyPending = async () => {
    if (!pendingEdit || !bpmnXml) return;
    setLoading(true);
    setError(null);
    try {
      const result = await chatService.applyEdits({
        bpmnXml,
        operations: pendingEdit.operations,
      });
      if (!result.updatedXml) {
        setMessages((prev) => [
          ...prev,
          {
            role: 'assistant',
            content:
              `Could not apply the changes. ${result.failed?.length ? result.failed.join('; ') : ''}`,
          },
        ]);
      } else {
        onBpmnUpdated?.(result.updatedXml);
        setDownloadReady(result.updatedXml);
        setMessages((prev) => [
          ...prev,
          {
            role: 'assistant',
            content:
              `Applied ${result.applied.length} change(s). ${
                result.failed.length ? `Skipped: ${result.failed.join('; ')}. ` : ''
              }The updated BPMN is loaded in the canvas and ready to download.`,
          },
        ]);
      }
      setPendingEdit(null);
    } catch {
      setError('Failed to apply changes. Backend may be unavailable.');
    } finally {
      setLoading(false);
    }
  };

  const discardPending = () => setPendingEdit(null);

  const downloadUpdatedXml = () => {
    if (!downloadReady) return;
    const blob = new Blob([downloadReady], { type: 'application/xml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${processName || 'process'}-edited.bpmn`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleKey = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  const clearChat = () => {
    setMessages([]);
    setPendingEdit(null);
    setDownloadReady(null);
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
                    ? `Grounded on: ${processName || 'current process'}${
                        qualityReport ? ` · ${qualityReport.issues?.length || 0} defects` : ''
                      }`
                    : 'No process loaded — ask general BPMN questions'}
                </div>
              </div>
            </div>
            <div className="chat-dock-header-actions">
              {downloadReady && (
                <button
                  type="button"
                  className="chat-dock-icon-btn chat-dock-download"
                  onClick={downloadUpdatedXml}
                  title="Download updated BPMN"
                >
                  ⬇
                </button>
              )}
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

          <div className="chat-dock-mode-tabs">
            <button
              type="button"
              className={`chat-dock-mode-tab ${mode === 'ask' ? 'active' : ''}`}
              onClick={() => setMode('ask')}
            >
              💬 Ask
            </button>
            <button
              type="button"
              className={`chat-dock-mode-tab ${mode === 'edit' ? 'active' : ''}`}
              onClick={() => setMode('edit')}
              disabled={!bpmnXml}
              title={bpmnXml ? 'Edit the BPMN via chat' : 'Load a BPMN first'}
            >
              ✎ Edit BPMN
            </button>
          </div>

          <div className="chat-dock-body" ref={scrollRef}>
            {messages.length === 0 && (
              <div className="chat-dock-empty">
                <div className="chat-dock-empty-title">
                  {mode === 'edit' ? 'Describe a change' : 'Ask about your process'}
                </div>
                <p>
                  {mode === 'edit'
                    ? 'Tell me what to change. I will propose a plan, you review it, then I apply only that change to your BPMN. Other elements stay untouched.'
                    : 'I can explain the flow, describe roles and decisions, list quality defects, and suggest fixes.'}
                </p>
                <div className="chat-dock-suggestions">
                  {suggestions.map((prompt) => (
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

            {pendingEdit && !loading && (
              <div className="chat-dock-pending">
                <div className="chat-dock-pending-title">Review pending change</div>
                <div className="chat-dock-pending-plan">{pendingEdit.plan}</div>
                <div className="chat-dock-pending-actions">
                  <button
                    type="button"
                    className="chat-dock-btn chat-dock-btn-primary"
                    onClick={applyPending}
                  >
                    ✓ Apply to BPMN
                  </button>
                  <button
                    type="button"
                    className="chat-dock-btn chat-dock-btn-ghost"
                    onClick={discardPending}
                  >
                    Discard
                  </button>
                </div>
              </div>
            )}

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
                mode === 'edit'
                  ? 'e.g. Add end event after Post to accounting'
                  : hasContext
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
              {loading ? '...' : mode === 'edit' ? 'Propose' : 'Send'}
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

function describeOp(op: EditOperation): string {
  const parts: string[] = [];
  Object.entries(op).forEach(([k, v]) => {
    if (k === 'op') return;
    parts.push(`${k}=${JSON.stringify(v)}`);
  });
  return parts.length ? ` (${parts.join(', ')})` : '';
}
