import React, { useEffect, useRef, useState } from 'react';
import type {
  ProcessKnowledgeDTO,
  ProcessGraphDTO,
  ProcessQualityReportDTO,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { TypingMarkdown } from './TypingMarkdown';
import {
  chatService,
  type ChatMessage,
  type EditOperation,
  type SelectedElementInfo,
  type ValidationSummary,
} from '../services/chatService';
import './ChatDock.css';

interface Props {
  processName?: string;
  bpmnXml?: string | null;
  knowledge?: ProcessKnowledgeDTO | null;
  graph?: ProcessGraphDTO | null;
  qualityReport?: ProcessQualityReportDTO | null;
  selectedElement?: SelectedElementInfo | null;
  onClearSelectedElement?: () => void;
  sourceText?: string;
  onBpmnUpdated?: (xml: string) => void;
}

type ChatMode = 'ask' | 'edit';

interface PendingEdit {
  plan: string;
  steps: string[];
  operations: EditOperation[];
  proposedXml?: string;
}

const QUICK_COMMANDS = [
  { label: '⚡ Explain Process', prompt: 'Explain this process step-by-step from start to end.', mode: 'ask' as const },
  { label: '💡 Suggest Improvements', prompt: 'Analyze this process and suggest key improvements, bottlenecks, and missing exception paths.', mode: 'ask' as const },
  { label: '🔍 Audit & Validate', prompt: 'Audit this process and list all defects, risks, and missing elements from the quality report.', mode: 'ask' as const },
  { label: '📑 Trace Document', prompt: 'Does this BPMN model accurately match the source document requirements and business rules?', mode: 'ask' as const },
  { label: '⤺ Undo Last Edit', prompt: 'undo that', mode: 'edit' as const },
];

const SUGGESTED_ASK = [
  'Explain the flow from start to end.',
  'What decision gateways are in this process?',
  'Does this BPMN match the document requirements?',
  'What is the highest severity quality defect?',
];

const SUGGESTED_EDIT = [
  'Add an approval step after Document Verification.',
  'Remove the payment task and reconnect.',
  'Make the review task a service task.',
  'Add an XOR gateway after Order Validation.',
];

export const ChatDock: React.FC<Props> = ({
  processName,
  bpmnXml,
  knowledge,
  graph,
  qualityReport,
  selectedElement,
  onClearSelectedElement,
  sourceText,
  onBpmnUpdated,
}) => {
  const [open, setOpen] = useState<boolean>(false);
  const [mode, setMode] = useState<ChatMode>('ask');
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    const saved = localStorage.getItem('pie_chat_history');
    return saved ? JSON.parse(saved) : [];
  });

  useEffect(() => {
    localStorage.setItem('pie_chat_history', JSON.stringify(messages));
  }, [messages]);

  const [pendingEdit, setPendingEdit] = useState<PendingEdit | null>(null);
  const [currentVersion, setCurrentVersion] = useState<number | null>(null);
  const [lastValidation, setLastValidation] = useState<ValidationSummary | null>(null);
  const [input, setInput] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [downloadReady, setDownloadReady] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, loading, open, pendingEdit, selectedElement]);

  const hasContext = Boolean(bpmnXml || knowledge || graph);
  const suggestions = mode === 'edit' ? SUGGESTED_EDIT : SUGGESTED_ASK;

  const handleUndo = async () => {
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      const res = await chatService.undo(processName || 'default', 1);
      if (res.success && res.updatedXml) {
        onBpmnUpdated?.(res.updatedXml);
        setDownloadReady(res.updatedXml);
        setCurrentVersion(res.currentVersion || null);
        setMessages((prev) => [
          ...prev,
          { role: 'assistant', content: `⤺ ${res.message}` },
        ]);
      } else {
        setError(res.message || 'Cannot undo further.');
      }
    } catch {
      setError('Undo failed. Backend service unavailable.');
    } finally {
      setLoading(false);
    }
  };

  const handleRedo = async () => {
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      const res = await chatService.redo(processName || 'default', 1);
      if (res.success && res.updatedXml) {
        onBpmnUpdated?.(res.updatedXml);
        setDownloadReady(res.updatedXml);
        setCurrentVersion(res.currentVersion || null);
        setMessages((prev) => [
          ...prev,
          { role: 'assistant', content: `⤼ ${res.message}` },
        ]);
      } else {
        setError(res.message || 'Cannot redo further.');
      }
    } catch {
      setError('Redo failed. Backend service unavailable.');
    } finally {
      setLoading(false);
    }
  };

  const send = async (rawText?: string, explicitMode?: ChatMode) => {
    const question = (rawText ?? input).trim();
    if (!question || loading) return;

    const targetMode = explicitMode || mode;
    const nextHistory: ChatMessage[] = [...messages, { role: 'user', content: question }];
    setMessages(nextHistory);
    setInput('');
    setLoading(true);
    setError(null);

    const contextPayload = {
      processName,
      bpmnXml: bpmnXml || undefined,
      knowledge,
      graph,
      qualityReport,
      selectedElement: selectedElement || undefined,
      sourceText: sourceText || undefined,
    };

    try {
      if (targetMode === 'ask') {
        const res = await chatService.ask({
          question,
          history: messages,
          context: contextPayload,
        });

        if (res.updatedXml) {
          onBpmnUpdated?.(res.updatedXml);
          setDownloadReady(res.updatedXml);
          if (res.currentVersion) setCurrentVersion(res.currentVersion);
        }

        setMessages([...nextHistory, { role: 'assistant', content: res.answer }]);
      } else {
        const editResponse = await chatService.askEdit({
          question,
          history: messages,
          context: contextPayload,
          autoApply: false,
        });

        if (editResponse.updatedXml) {
          onBpmnUpdated?.(editResponse.updatedXml);
          setDownloadReady(editResponse.updatedXml);
          if (editResponse.currentVersion) setCurrentVersion(editResponse.currentVersion);
          if (editResponse.validation) setLastValidation(editResponse.validation);
        }

        const stepsList = editResponse.steps && editResponse.steps.length > 0
          ? editResponse.steps
          : editResponse.operations && editResponse.operations.length > 0
          ? editResponse.operations.map((o, i) => `${i + 1}. ${o.op}${describeOp(o)}`)
          : [];

        const opsSummary = stepsList.length > 0
          ? `\n\nProposed plan:\n${stepsList.join('\n')}`
          : '';

        setMessages([
          ...nextHistory,
          {
            role: 'assistant',
            content: `${editResponse.plan}${opsSummary}${
              editResponse.error ? `\n\n⚠ ${editResponse.error}` : ''
            }`,
          },
        ]);

        if (editResponse.operations && editResponse.operations.length > 0 && !editResponse.applied) {
          setPendingEdit({
            plan: editResponse.plan,
            steps: stepsList,
            operations: editResponse.operations,
          });
        }
      }
    } catch {
      setError('Could not reach the AI service. Check that the backend is running.');
      setMessages([
        ...nextHistory,
        {
          role: 'assistant',
          content:
            'Sorry — I could not reach the AI service. Please make sure the backend is running and try again.',
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
        plan: pendingEdit.plan,
        processId: processName || 'default',
      });

      if (!result.updatedXml) {
        setMessages((prev) => [
          ...prev,
          {
            role: 'assistant',
            content: `Could not apply the changes. ${
              result.failed?.length ? result.failed.join('; ') : ''
            }`,
          },
        ]);
      } else {
        onBpmnUpdated?.(result.updatedXml);
        setDownloadReady(result.updatedXml);
        if (result.currentVersion) setCurrentVersion(result.currentVersion);
        if (result.validation) setLastValidation(result.validation);

        const valNote = result.validation
          ? ` [Quality Score: ${result.validation.qualityScore}/100${
              result.validation.valid ? ' · Valid' : ' · Needs review'
            }]`
          : '';

        setMessages((prev) => [
          ...prev,
          {
            role: 'assistant',
            content: `✓ Applied ${result.applied.length} change(s). ${
              result.failed.length ? `Skipped: ${result.failed.join('; ')}. ` : ''
            }Updated BPMN model loaded${valNote}.`,
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
          {/* Header */}
          <div className="chat-dock-header">
            <div className="chat-dock-title">
              <span className="chat-dock-dot" />
              <div>
                <div className="chat-dock-headline">
                  <strong>BPMN Copilot</strong>
                  {currentVersion && (
                    <span className="chat-version-badge" title="Active BPMN Version">
                      v{currentVersion}
                    </span>
                  )}
                </div>
                <div className="chat-dock-subtitle">
                  {hasContext
                    ? `Grounded: ${processName || 'current process'}${
                        lastValidation
                          ? ` · ${lastValidation.qualityScore}/100 score`
                          : qualityReport
                          ? ` · ${qualityReport.issues?.length || 0} defects`
                          : ''
                      }`
                    : 'No process loaded — ask general BPMN questions'}
                </div>
              </div>
            </div>

            <div className="chat-dock-header-actions">
              <button
                type="button"
                className="chat-dock-icon-btn"
                onClick={handleUndo}
                title="Undo last change (⤺)"
                disabled={loading}
              >
                ⤺
              </button>
              <button
                type="button"
                className="chat-dock-icon-btn"
                onClick={handleRedo}
                title="Redo next change (⤼)"
                disabled={loading}
              >
                ⤼
              </button>
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

          {/* Mode Tabs */}
          <div className="chat-dock-mode-tabs">
            <button
              type="button"
              className={`chat-dock-mode-tab ${mode === 'ask' ? 'active' : ''}`}
              onClick={() => setMode('ask')}
            >
              💬 Ask & Inspect
            </button>
            <button
              type="button"
              className={`chat-dock-mode-tab ${mode === 'edit' ? 'active' : ''}`}
              onClick={() => setMode('edit')}
              disabled={!bpmnXml}
              title={bpmnXml ? 'Edit BPMN safely with Copilot' : 'Load a BPMN first'}
            >
              ✎ Edit Diagram
            </button>
          </div>

          {/* Quick Command Shortcuts Bar */}
          <div className="chat-quick-actions custom-scrollbar">
            {QUICK_COMMANDS.map((qc) => (
              <button
                key={qc.label}
                type="button"
                className="chat-quick-chip"
                onClick={() => send(qc.prompt, qc.mode)}
                disabled={loading}
              >
                {qc.label}
              </button>
            ))}
          </div>

          {/* Chat Messages Body */}
          <div className="chat-dock-body" ref={scrollRef}>
            {messages.length === 0 && (
              <div className="chat-dock-empty">
                <div className="chat-dock-empty-title">
                  {mode === 'edit' ? 'BPMN Graph Copilot' : 'Process Copilot'}
                </div>
                <p>
                  {mode === 'edit'
                    ? 'State your requested changes in plain English. I treat the diagram as a safe graph, build an edit plan, auto-reconnect paths on deletion, and validate structural integrity.'
                    : 'Ask questions about flows, roles, decision gateways, or test traceability against your source documents.'}
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
                <div className="chat-msg-content">
                  <TypingMarkdown
                    content={m.content}
                    isStreaming={m.role === 'assistant' && idx === messages.length - 1}
                    speed={10}
                  />
                </div>
              </div>
            ))}

            {/* Pending Edit Review Card */}
            {pendingEdit && !loading && (
              <div className="chat-dock-pending">
                <div className="chat-dock-pending-header">
                  <span className="pending-badge">EDIT PLAN</span>
                  <strong>Review Proposed Graph Modification</strong>
                </div>
                <div className="chat-dock-pending-plan">{pendingEdit.plan}</div>

                {pendingEdit.steps && pendingEdit.steps.length > 0 && (
                  <div className="chat-dock-pending-steps">
                    {pendingEdit.steps.map((step, sIdx) => (
                      <div key={sIdx} className="pending-step-item">
                        <span className="step-num">{sIdx + 1}</span>
                        <span>{step.replace(/^\d+\.\s*/, '')}</span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="chat-dock-pending-actions">
                  <button
                    type="button"
                    className="chat-dock-btn chat-dock-btn-primary"
                    onClick={applyPending}
                  >
                    ✓ Apply Changes to BPMN
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

            {/* Post-Edit Quality Indicator */}
            {lastValidation && !loading && (
              <div className="chat-dock-validation-banner">
                <span className="val-icon">{lastValidation.valid ? '✓' : '⚠'}</span>
                <div className="val-text">
                  <strong>BPMN Validation: {lastValidation.qualityScore}/100</strong>
                  <span>
                    {lastValidation.valid
                      ? 'All start/end events, flows, and gateways are structurally sound.'
                      : `${lastValidation.issueCount} issue(s) flagged.`}
                  </span>
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

          {/* Selected Element Focus Context Chip */}
          {selectedElement && (
            <div className="chat-dock-selection-chip">
              <div className="selection-chip-info">
                <span className="selection-target-icon">🎯</span>
                <span className="selection-type-tag">{selectedElement.type}</span>
                <span className="selection-name" title={selectedElement.name}>
                  {selectedElement.name}
                </span>
              </div>
              <div className="selection-chip-actions">
                <button
                  type="button"
                  className="chip-btn"
                  onClick={() => send(`Explain the step "${selectedElement.name}"`, 'ask')}
                  title="Explain this element"
                >
                  Explain
                </button>
                <button
                  type="button"
                  className="chip-btn"
                  onClick={() =>
                    send(`Make "${selectedElement.name}" an automated service task`, 'edit')
                  }
                  title="Convert to automated service task"
                >
                  Automate
                </button>
                <button
                  type="button"
                  className="chip-btn"
                  onClick={() => send(`Add a step after "${selectedElement.name}"`, 'edit')}
                  title="Insert task after this"
                >
                  + Add Step
                </button>
                <button
                  type="button"
                  className="chip-btn chip-btn-danger"
                  onClick={() =>
                    send(`Delete the element "${selectedElement.name}" and reconnect`, 'edit')
                  }
                  title="Delete element safely"
                >
                  Delete
                </button>
                {onClearSelectedElement && (
                  <button
                    type="button"
                    className="chip-btn-close"
                    onClick={onClearSelectedElement}
                    title="Deselect"
                  >
                    ✕
                  </button>
                )}
              </div>
            </div>
          )}

          {error && <div className="chat-dock-error">{error}</div>}

          {/* Input Row */}
          <div className="chat-dock-input-row">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKey}
              placeholder={
                selectedElement
                  ? `Instruction for "${selectedElement.name}" (e.g. "Move this before...", "Make automatic")...`
                  : mode === 'edit'
                  ? 'e.g. Add manager approval after document verification...'
                  : hasContext
                  ? 'Ask about this process, decision gateways, or SOP traceability...'
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
              {loading ? '...' : mode === 'edit' ? 'Plan Edit' : 'Send'}
            </button>
          </div>
        </div>
      )}

      {/* Floating Action Button */}
      <button
        type="button"
        className="chat-dock-fab"
        onClick={() => setOpen((v) => !v)}
        title={open ? 'Close Copilot' : 'Open BPMN Copilot'}
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
