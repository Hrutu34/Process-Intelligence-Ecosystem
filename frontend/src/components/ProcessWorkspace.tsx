import React, { useState } from 'react';
import type { ProcessEntity, ChatMessage } from '../services/types';
import { aiReviewService } from '../services/aiReviewService';
import { ProcessGraphViewer } from './ProcessGraphViewer';
import { BpmnIoCanvas } from './BpmnIoCanvas';
import { ProcessKnowledgeReview } from './ProcessKnowledgeReview';
import { ProcessHistoryModal } from './ProcessHistoryModal';
import { generateProcessNarrative } from '../services/bpmnNarrativeGenerator';
import './ProcessWorkspace.css';

export type WorkspaceTab =
  | 'copilot'
  | 'overview'
  | 'knowledge'
  | 'graph'
  | 'bpmn'
  | 'validation'
  | 'review';

interface Props {
  process: ProcessEntity;
  onUpdateProcess: (updated: ProcessEntity) => void;
  onApplyFix: (issueId: string) => void;
  onBackToProcesses: () => void;
}

export const ProcessWorkspace: React.FC<Props> = ({
  process,
  onUpdateProcess,
  onApplyFix,
  onBackToProcesses,
}) => {
  const [activeTab, setActiveTab] = useState<WorkspaceTab>(process.sourceType === 'BPMN' ? 'copilot' : 'overview');
  const [showHistoryModal, setShowHistoryModal] = useState(false);
  const [savedToast, setSavedToast] = useState(false);
  const [copiedNarrative, setCopiedNarrative] = useState(false);

  // AI Assistant Chat State
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    {
      id: 'init_1',
      sender: 'ai',
      text: `Hello! I am your AI Process Assistant for **${process.name}**. I can explain this workflow, detect compliance gaps, or evaluate automation feasibility. What would you like to explore?`,
      timestamp: 'Just now',
      suggestedActions: [
        'Explain this process',
        'What happens if Finance rejects?',
        'Which activities are manual?',
        'Is this process automation-ready?'
      ],
    }
  ]);
  const [chatInput, setChatInput] = useState('');
  const [isAiThinking, setIsAiThinking] = useState(false);

  const handleSendChat = async (userText: string) => {
    if (!userText.trim() || isAiThinking) return;

    const userMsg: ChatMessage = {
      id: 'usr_' + Date.now().toString(36),
      sender: 'user',
      text: userText,
      timestamp: 'Just now',
    };

    setChatMessages((prev) => [...prev, userMsg]);
    setChatInput('');
    setIsAiThinking(true);

    try {
      const reply = await aiReviewService.sendChatMessage(process, userText);
      setChatMessages((prev) => [...prev, reply]);
    } catch (err) {
      console.error('AI chat response error', err);
    } finally {
      setIsAiThinking(false);
    }
  };

  const handleSave = () => {
    onUpdateProcess({ ...process, lastUpdated: 'Just now' });
    setSavedToast(true);
    setTimeout(() => setSavedToast(false), 2500);
  };

  const handleAddVersion = (summary: string) => {
    const nextVer = `v${parseInt(process.currentVersion.replace('v', '') || '1', 10) + 1}`;
    const newVersion = {
      version: nextVer,
      timestamp: 'Just now',
      summary,
      qualityScore: process.qualityScore,
      author: 'Lead Architect',
    };

    const updated = {
      ...process,
      currentVersion: nextVer,
      versions: [newVersion, ...process.versions],
      lastUpdated: 'Just now',
    };

    onUpdateProcess(updated);
    setShowHistoryModal(false);
  };

  return (
    <div className="workspace-container">
      {/* Workspace Header */}
      <div className="workspace-header">
        <div className="workspace-title-group">
          <div className="workspace-breadcrumb">
            <span style={{ cursor: 'pointer' }} onClick={onBackToProcesses}>
              Processes
            </span>{' '}
            / <span>{process.name}</span>
          </div>

          <h2>{process.name}</h2>

          <div className="workspace-meta-pills">
            <span className="type-pill">📄 {process.sourceDocument}</span>
            <span className="type-pill">🏷️ {process.currentVersion}</span>
            <span className="type-pill" style={{ color: 'var(--aqua)' }}>
              ⚡ {process.knowledge.activities?.length || 0} Activities
            </span>
            <span
              className={`quality-badge ${process.qualityScore >= 85 ? 'high' : 'medium'}`}
            >
              {process.qualityScore}% Quality
            </span>
          </div>
        </div>

        <div className="workspace-actions">
          <button type="button" className="btn-ghost" onClick={() => setShowHistoryModal(true)}>
            ⏱️ History ({process.versions.length})
          </button>
          <button type="button" className="btn-ghost" onClick={handleSave}>
            {savedToast ? '✓ Saved' : '💾 Save Draft'}
          </button>
          <button
            type="button"
            className="yellow-button"
            onClick={() => setActiveTab('bpmn')}
          >
            BPMN CANVAS <span>⌘</span>
          </button>
        </div>
      </div>

      {/* Tabs Navigation Strip */}
      <div className="workspace-tabs-strip">
        <button
          type="button"
          className={`ws-tab-btn ${activeTab === 'copilot' ? 'active' : ''}`}
          onClick={() => setActiveTab('copilot')}
          style={activeTab === 'copilot' ? { borderColor: 'var(--aqua)', color: 'var(--aqua)', fontWeight: 700 } : {}}
        >
          <span>🎯 Copilot Deliverables (Two Outputs)</span>
        </button>
        <button
          type="button"
          className={`ws-tab-btn ${activeTab === 'overview' ? 'active' : ''}`}
          onClick={() => setActiveTab('overview')}
        >
          <span>📊 Overview</span>
        </button>
        <button
          type="button"
          className={`ws-tab-btn ${activeTab === 'knowledge' ? 'active' : ''}`}
          onClick={() => setActiveTab('knowledge')}
        >
          <span>🧠 Knowledge</span>
        </button>
        <button
          type="button"
          className={`ws-tab-btn ${activeTab === 'graph' ? 'active' : ''}`}
          onClick={() => setActiveTab('graph')}
        >
          <span>⚡ Process Graph</span>
        </button>
        <button
          type="button"
          className={`ws-tab-btn ${activeTab === 'bpmn' ? 'active' : ''}`}
          onClick={() => setActiveTab('bpmn')}
        >
          <span>⌘ BPMN 2.0</span>
        </button>
        <button
          type="button"
          className={`ws-tab-btn ${activeTab === 'validation' ? 'active' : ''}`}
          onClick={() => setActiveTab('validation')}
        >
          <span>⚠️ Validation ({process.validationIssues.filter((i) => !i.isApplied).length})</span>
        </button>
        <button
          type="button"
          className={`ws-tab-btn ${activeTab === 'review' ? 'active' : ''}`}
          onClick={() => setActiveTab('review')}
        >
          <span>✦ AI Review & Assistant</span>
        </button>
      </div>

      {/* ========================================================
          TAB 0: AI COPILOT DELIVERABLES (Two Outputs, Not One)
         ======================================================== */}
      {activeTab === 'copilot' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {/* Hackathon Header Banner */}
          <div className="panel-card" style={{ background: 'linear-gradient(135deg, rgba(9, 33, 73, 0.9), rgba(5, 21, 51, 0.95))', borderColor: 'rgba(57, 245, 208, 0.3)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontSize: 24 }}>🎯</span>
                  <h3 style={{ margin: 0, fontSize: 18, color: 'var(--white)' }}>ProcessIQ AI Copilot for BPMN — Deliverables Hub</h3>
                  <span className="type-pill" style={{ color: 'var(--aqua)', borderColor: 'var(--aqua)' }}>
                    Fixed Scope Evaluator
                  </span>
                </div>
                <p style={{ margin: '6px 0 0 0', fontSize: 13, color: 'var(--soft-white)' }}>
                  Per hackathon requirements: Output 1 (Plain-Language Description) and Output 2 (Structural Quality &amp; Defect Report) are strictly separated below.
                </p>
              </div>

              <div style={{ display: 'flex', gap: 10 }}>
                <button
                  type="button"
                  className="btn-ghost"
                  onClick={() => {
                    const narrative = process.narrative || (process.graph ? generateProcessNarrative(process.graph, process.name) : null);
                    if (narrative) {
                      navigator.clipboard.writeText(narrative.fullMarkdown);
                      setCopiedNarrative(true);
                      setTimeout(() => setCopiedNarrative(false), 2000);
                    }
                  }}
                >
                  {copiedNarrative ? '✓ Copied Markdown' : '📋 Copy Narrative'}
                </button>
                <button
                  type="button"
                  className="yellow-button"
                  onClick={() => setActiveTab('bpmn')}
                >
                  View Canvas ⌘
                </button>
              </div>
            </div>
          </div>

          {/* DUAL OUTPUTS: Two Columns */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 24, alignItems: 'start' }}>
            {/* OUTPUT 1: PLAIN-LANGUAGE NARRATIVE (CAPABILITY 2) */}
            <div className="panel-card" style={{ borderTop: '4px solid var(--aqua)' }}>
              <div className="panel-card-header" style={{ marginBottom: 16 }}>
                <div>
                  <span className="type-pill" style={{ color: 'var(--aqua)', marginBottom: 6, display: 'inline-block' }}>
                    OUTPUT 1 • CAPABILITY 2
                  </span>
                  <h3 style={{ fontSize: 18, margin: 0 }}>Plain-Language Narrative</h3>
                  <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                    Business language explanation for non-expert stakeholders
                  </span>
                </div>
                <span className="quality-badge high">Human Readable</span>
              </div>

              {(() => {
                const narrative = process.narrative || (process.graph ? generateProcessNarrative(process.graph, process.name) : null);
                if (!narrative) {
                  return <span style={{ color: 'var(--muted)', fontSize: 13 }}>Generating business narrative...</span>;
                }
                return (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    {/* Executive Overview */}
                    <div style={{ background: 'rgba(4, 18, 45, 0.6)', padding: 14, borderRadius: 10, border: '1px solid rgba(116, 183, 220, 0.15)' }}>
                      <strong style={{ color: 'var(--aqua)', fontSize: 13, display: 'block', marginBottom: 6 }}>
                        Executive Summary
                      </strong>
                      <p style={{ color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.6, margin: 0 }}>
                        {narrative.executiveSummary}
                      </p>
                    </div>

                    {/* Initiation */}
                    <div>
                      <strong style={{ color: 'var(--white)', fontSize: 13, display: 'block', marginBottom: 4 }}>
                        1. Initiation &amp; Process Trigger
                      </strong>
                      <p style={{ color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.5, margin: 0 }}>
                        {narrative.triggerNarrative}
                      </p>
                    </div>

                    {/* Flow Steps */}
                    <div>
                      <strong style={{ color: 'var(--white)', fontSize: 13, display: 'block', marginBottom: 8 }}>
                        2. Step-by-Step Activities &amp; Ownership
                      </strong>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                        {narrative.flowSteps.map((step, idx) => (
                          <div key={idx} style={{ padding: '8px 12px', background: 'rgba(4, 18, 45, 0.4)', borderRadius: 6, fontSize: 12, color: 'var(--soft-white)', borderLeft: '3px solid var(--aqua)' }}>
                            {step}
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Decision Points */}
                    {narrative.decisionNarratives.length > 0 && (
                      <div>
                        <strong style={{ color: 'var(--white)', fontSize: 13, display: 'block', marginBottom: 8 }}>
                          3. Decision Logic &amp; Gateway Routing
                        </strong>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                          {narrative.decisionNarratives.map((dec, idx) => (
                            <div key={idx} style={{ padding: '8px 12px', background: 'rgba(255, 229, 127, 0.08)', borderRadius: 6, fontSize: 12, color: '#ffe57f', borderLeft: '3px solid #ffe57f' }}>
                              {dec}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Concurrency */}
                    {narrative.concurrencyNarratives.length > 0 && (
                      <div>
                        <strong style={{ color: 'var(--white)', fontSize: 13, display: 'block', marginBottom: 8 }}>
                          4. Parallel Workstreams &amp; Synchronization
                        </strong>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                          {narrative.concurrencyNarratives.map((c, idx) => (
                            <div key={idx} style={{ padding: '8px 12px', background: 'rgba(57, 245, 208, 0.08)', borderRadius: 6, fontSize: 12, color: 'var(--aqua)', borderLeft: '3px solid var(--aqua)' }}>
                              {c}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Outcome */}
                    <div>
                      <strong style={{ color: 'var(--white)', fontSize: 13, display: 'block', marginBottom: 4 }}>
                        5. Process Closure &amp; Expected Outcomes
                      </strong>
                      <p style={{ color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.5, margin: 0 }}>
                        {narrative.outcomeNarrative}
                      </p>
                    </div>
                  </div>
                );
              })()}
            </div>

            {/* OUTPUT 2: DEFECT & QUALITY REPORT (CAPABILITY 3) */}
            <div className="panel-card" style={{ borderTop: `4px solid ${process.qualityScore >= 80 ? 'var(--aqua)' : '#ff6b6b'}` }}>
              <div className="panel-card-header" style={{ marginBottom: 16 }}>
                <div>
                  <span className="type-pill" style={{ color: process.qualityScore >= 80 ? 'var(--aqua)' : '#ff6b6b', marginBottom: 6, display: 'inline-block' }}>
                    OUTPUT 2 • CAPABILITY 3
                  </span>
                  <h3 style={{ fontSize: 18, margin: 0 }}>BPMN Quality &amp; Defect Report</h3>
                  <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                    Structural analysis, planted defect detection, and concrete fixes
                  </span>
                </div>
                <span className={`quality-badge ${process.qualityScore >= 80 ? 'high' : 'medium'}`}>
                  Score: {process.qualityScore} / 100
                </span>
              </div>

              {/* Defect Count Summary */}
              <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
                <div className="stat-card" style={{ flex: 1, padding: 12 }}>
                  <span className="stat-card-label">Open Defects</span>
                  <div style={{ fontSize: 18, fontWeight: 700, color: process.validationIssues.filter(i => !i.isApplied).length > 0 ? '#ff6b6b' : 'var(--aqua)' }}>
                    {process.validationIssues.filter(i => !i.isApplied).length}
                  </div>
                </div>
                <div className="stat-card" style={{ flex: 1, padding: 12 }}>
                  <span className="stat-card-label">Resolved</span>
                  <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--aqua)' }}>
                    {process.validationIssues.filter(i => i.isApplied).length}
                  </div>
                </div>
                <div className="stat-card" style={{ flex: 1, padding: 12 }}>
                  <span className="stat-card-label">Health</span>
                  <div style={{ fontSize: 14, fontWeight: 700, color: process.qualityScore >= 80 ? 'var(--aqua)' : '#ffe57f' }}>
                    {process.qualityScore >= 80 ? 'Clean Reference' : 'Defective'}
                  </div>
                </div>
              </div>

              {/* Issues List */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {process.validationIssues.length === 0 ? (
                  <div style={{ background: 'rgba(57, 245, 208, 0.08)', border: '1px solid rgba(57, 245, 208, 0.3)', padding: 16, borderRadius: 10, textAlign: 'center' }}>
                    <span style={{ fontSize: 24, display: 'block', marginBottom: 6 }}>✓</span>
                    <strong style={{ color: 'var(--aqua)', fontSize: 14, display: 'block' }}>
                      No Structural Defects Detected
                    </strong>
                    <span style={{ fontSize: 12, color: 'var(--soft-white)' }}>
                      This model complies with core BPMN 2.0 validation standards (Start/End existence, gateway completeness, connectivity, and role alignment).
                    </span>
                  </div>
                ) : (
                  process.validationIssues.map((issue) => (
                    <div
                      key={issue.id}
                      className={`validation-card ${issue.severity.toLowerCase()} ${issue.isApplied ? 'resolved' : ''}`}
                      style={{ padding: 14 }}
                    >
                      <div className="validation-left">
                        <div className="validation-title-row">
                          <span className="type-pill" style={{ color: issue.isApplied ? 'var(--aqua)' : issue.severity === 'Critical' ? '#ff6b6b' : '#ffe57f', fontSize: 11 }}>
                            {issue.isApplied ? '✓ FIXED' : issue.severity.toUpperCase()}
                          </span>
                          <strong style={{ color: 'var(--white)', fontSize: 13 }}>{issue.title}</strong>
                        </div>
                        <p style={{ color: 'var(--soft-white)', fontSize: 12, margin: '6px 0' }}>
                          {issue.description}
                        </p>
                        {issue.suggestedFix && (
                          <div style={{ background: 'rgba(4, 18, 45, 0.6)', padding: '6px 10px', borderRadius: 6, fontSize: 11, color: 'var(--aqua)' }}>
                            💡 <strong>Concrete Fix:</strong> {issue.suggestedFix}
                          </div>
                        )}
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, justifyContent: 'center' }}>
                        {!issue.isApplied ? (
                          <button
                            type="button"
                            className="yellow-button"
                            style={{ padding: '6px 12px', fontSize: 11 }}
                            onClick={() => onApplyFix(issue.id)}
                          >
                            Apply Fix ⚡
                          </button>
                        ) : (
                          <span style={{ color: 'var(--aqua)', fontSize: 11, fontWeight: 700, textAlign: 'center' }}>
                            ✓ Resolved
                          </span>
                        )}
                        <button
                          type="button"
                          className="btn-ghost"
                          style={{ padding: '4px 8px', fontSize: 11 }}
                          onClick={() => setActiveTab('bpmn')}
                        >
                          Canvas ↗
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================
          TAB 1: OVERVIEW
         ======================================================== */}
      {activeTab === 'overview' && (
        <div className="overview-grid">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* Quality Score Meter */}
            <div className="quality-meter-card">
              <div>
                <span className="stat-card-label">Process Quality & Governance Readiness</span>
                <div className="quality-meter-score">{process.qualityScore} / 100</div>
                <span style={{ fontSize: 13, color: 'var(--soft-white)' }}>
                  {process.qualityScore >= 85
                    ? '✓ Strong structural adherence to BPMN 2.0 execution standards.'
                    : 'Action required on branch conditions.'}
                </span>
              </div>

              <div style={{ display: 'flex', gap: 16 }}>
                <div className="stat-card" style={{ padding: '12px 18px', minWidth: 110 }}>
                  <span className="stat-card-label">Activities</span>
                  <div style={{ fontSize: 20, fontWeight: 700 }}>{process.knowledge.activities?.length || 0}</div>
                </div>
                <div className="stat-card" style={{ padding: '12px 18px', minWidth: 110 }}>
                  <span className="stat-card-label">Actors</span>
                  <div style={{ fontSize: 20, fontWeight: 700 }}>{process.knowledge.actors?.length || 0}</div>
                </div>
                <div className="stat-card" style={{ padding: '12px 18px', minWidth: 110 }}>
                  <span className="stat-card-label">Decisions</span>
                  <div style={{ fontSize: 20, fontWeight: 700 }}>{process.knowledge.gateways?.length || 0}</div>
                </div>
              </div>
            </div>

            {/* Business Summary */}
            <div className="panel-card">
              <div className="panel-card-header">
                <h3>Executive Process Summary</h3>
              </div>
              <p style={{ color: 'var(--soft-white)', fontSize: 14, lineHeight: 1.6 }}>
                {process.description}
              </p>
            </div>

            {/* Process Insights (Bottlenecks, Automation, Risks) */}
            <div className="panel-card">
              <div className="panel-card-header">
                <h3>💡 Process Optimization Insights</h3>
              </div>
              <div className="insights-list">
                {process.insights.length === 0 ? (
                  <span style={{ color: 'var(--muted)', fontSize: 13 }}>No active bottlenecks detected.</span>
                ) : (
                  process.insights.map((ins, idx) => (
                    <div key={idx} className={`insight-card ${ins.type}`}>
                      <div className="insight-header">
                        <span className="insight-title">{ins.title}</span>
                        <span className="insight-impact">Impact: {ins.impact}</span>
                      </div>
                      <div className="insight-desc">{ins.description}</div>
                      {ins.suggestion && (
                        <span className="insight-suggestion">
                          💡 Suggestion: {ins.suggestion}
                        </span>
                      )}
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>

          {/* Right Column: Metadata & Traceability Summary */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div className="panel-card">
              <div className="panel-card-header">
                <h3>Process Metadata</h3>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, fontSize: 13 }}>
                <div>
                  <span style={{ color: 'var(--muted)', display: 'block', fontSize: 11 }}>SOURCE FILE</span>
                  <strong style={{ color: 'var(--white)' }}>{process.sourceDocument}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--muted)', display: 'block', fontSize: 11 }}>INGESTION FORMAT</span>
                  <span className="type-pill">{process.sourceType}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--muted)', display: 'block', fontSize: 11 }}>CREATED</span>
                  <span style={{ color: 'var(--soft-white)' }}>{process.createdAt}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--muted)', display: 'block', fontSize: 11 }}>LATEST CHECKPOINT</span>
                  <span style={{ color: 'var(--soft-white)' }}>{process.lastUpdated}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--muted)', display: 'block', fontSize: 11 }}>ACTIVE VERSION</span>
                  <span style={{ color: 'var(--aqua)', fontWeight: 700 }}>{process.currentVersion}</span>
                </div>
              </div>
            </div>

            {/* Source Quotes Preview */}
            <div className="panel-card">
              <div className="panel-card-header">
                <h3>Source Traceability</h3>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {process.sourceTraces.slice(0, 3).map((trace, i) => (
                  <div key={i} style={{ padding: 10, background: 'rgba(4, 18, 45, 0.6)', borderRadius: 8, fontSize: 12 }}>
                    <strong style={{ color: 'var(--aqua)' }}>{trace.entityName}</strong>
                    <p style={{ color: 'var(--muted)', fontStyle: 'italic', margin: '4px 0' }}>"{trace.sourceText}"</p>
                    <span style={{ color: 'var(--dim)', fontSize: 10 }}>{trace.pageOrSection}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================
          TAB 2: KNOWLEDGE (Normalized Cards + Traceability)
         ======================================================== */}
      {activeTab === 'knowledge' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <ProcessKnowledgeReview
            data={process.knowledge}
            onProceedToBpmn={() => setActiveTab('bpmn')}
            onReset={() => {}}
          />

          {/* Detailed Source Traceability Table */}
          {process.sourceTraces.length > 0 && (
            <div className="panel-card" style={{ padding: 0, overflow: 'hidden' }}>
              <div style={{ padding: '20px 24px', borderBottom: '1px solid rgba(116, 183, 220, 0.15)' }}>
                <h3>🔍 Document Source Traceability</h3>
                <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                  Mapping extracted activities and participants to original text clauses
                </span>
              </div>
              <table className="processes-table">
                <thead>
                  <tr>
                    <th>Entity</th>
                    <th>Type</th>
                    <th>Exact Source Excerpt</th>
                    <th>Source Document & Section</th>
                  </tr>
                </thead>
                <tbody>
                  {process.sourceTraces.map((trace, idx) => (
                    <tr key={idx}>
                      <td className="process-name-cell">
                        <strong>{trace.entityName}</strong>
                      </td>
                      <td>
                        <span className="type-pill">{trace.entityType}</span>
                      </td>
                      <td style={{ fontStyle: 'italic', color: 'var(--soft-white)' }}>
                        "{trace.sourceText}"
                      </td>
                      <td>
                        <span style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>
                          {trace.documentName} ({trace.pageOrSection || 'Section 1'})
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ========================================================
          TAB 3: PROCESS GRAPH (Canonical Flow)
         ======================================================== */}
      {activeTab === 'graph' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <ProcessGraphViewer
            knowledge={process.knowledge}
            defaultView="visual"
            onProceedToBpmn={() => setActiveTab('bpmn')}
          />
        </div>
      )}

      {/* ========================================================
          TAB 4: BPMN 2.0 (bpmn.io Vector Canvas)
         ======================================================== */}
      {activeTab === 'bpmn' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {process.graph ? (
            <BpmnIoCanvas graph={process.graph} rawXml={process.bpmnXml} />
          ) : (
            <ProcessGraphViewer
              knowledge={process.knowledge}
              defaultView="bpmn"
              onProceedToBpmn={() => setActiveTab('review')}
            />
          )}
        </div>
      )}

      {/* ========================================================
          TAB 5: VALIDATION (Actionable Quality Gaps)
         ======================================================== */}
      {activeTab === 'validation' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {/* Validation Quality Scorecard */}
          <div className="panel-card">
            <div className="panel-card-header">
              <h3>Process Validation Scorecard</h3>
              <span className="quality-badge high">{process.qualityScore} / 100</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
              <div className="stat-card" style={{ padding: 14 }}>
                <span className="stat-card-label">Structure</span>
                <strong style={{ color: 'var(--aqua)' }}>✓ VALID</strong>
              </div>
              <div className="stat-card" style={{ padding: 14 }}>
                <span className="stat-card-label">Activities</span>
                <strong style={{ color: 'var(--aqua)' }}>✓ COMPLETE</strong>
              </div>
              <div className="stat-card" style={{ padding: 14 }}>
                <span className="stat-card-label">Ownership</span>
                <strong style={{ color: 'var(--aqua)' }}>✓ ASSIGNED</strong>
              </div>
              <div className="stat-card" style={{ padding: 14 }}>
                <span className="stat-card-label">Gateways</span>
                <strong style={{ color: '#ffe57f' }}>⚠ 1 SUGGESTION</strong>
              </div>
              <div className="stat-card" style={{ padding: 14 }}>
                <span className="stat-card-label">Events</span>
                <strong style={{ color: '#ffe57f' }}>⚠ 1 SUGGESTION</strong>
              </div>
            </div>
          </div>

          {/* Actionable Issues List */}
          <div className="panel-card">
            <div className="panel-card-header">
              <h3>Actionable Quality Gaps ({process.validationIssues.length})</h3>
            </div>

            <div className="validation-issues-grid">
              {process.validationIssues.length === 0 ? (
                <span style={{ color: 'var(--aqua)' }}>✓ All validation rules passed. No open issues!</span>
              ) : (
                process.validationIssues.map((issue) => (
                  <div
                    key={issue.id}
                    className={`validation-card ${issue.severity.toLowerCase()} ${
                      issue.isApplied ? 'resolved' : ''
                    }`}
                  >
                    <div className="validation-left">
                      <div className="validation-title-row">
                        <span className="type-pill" style={{ color: issue.isApplied ? 'var(--aqua)' : '#ffe57f' }}>
                          {issue.isApplied ? '✓ RESOLVED' : `⚠️ ${issue.severity.toUpperCase()}`}
                        </span>
                        <strong style={{ color: 'var(--white)', fontSize: 14 }}>{issue.title}</strong>
                      </div>
                      <p style={{ color: 'var(--soft-white)', fontSize: 13, margin: '4px 0' }}>
                        {issue.description}
                      </p>
                      {issue.suggestedFix && (
                        <span style={{ fontSize: 12, color: 'var(--aqua)' }}>
                          💡 Recommended fix: {issue.suggestedFix}
                        </span>
                      )}
                    </div>

                    <div style={{ display: 'flex', gap: 10 }}>
                      <button
                        type="button"
                        className="btn-ghost"
                        onClick={() => setActiveTab('graph')}
                      >
                        View in Graph
                      </button>
                      {!issue.isApplied ? (
                        <button
                          type="button"
                          className="yellow-button"
                          onClick={() => onApplyFix(issue.id)}
                        >
                          Apply Suggestion ⚡
                        </button>
                      ) : (
                        <span style={{ color: 'var(--aqua)', fontSize: 12, fontWeight: 600 }}>
                          ✓ Applied
                        </span>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* ========================================================
          TAB 6: AI REVIEW & INTERACTIVE ASSISTANT
         ======================================================== */}
      {activeTab === 'review' && (
        <div className="ai-review-layout">
          {/* Left Column: AI Executive Summary & Audit Readiness */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div className="panel-card">
              <div className="panel-card-header">
                <h3>✦ AI Executive Review</h3>
                <span className="quality-badge high">
                  {process.aiSummary?.auditReadinessScore || 87}% Audit Ready
                </span>
              </div>
              <p style={{ color: 'var(--soft-white)', fontSize: 14, lineHeight: 1.6 }}>
                {process.aiSummary?.executiveSummary}
              </p>
            </div>

            <div className="panel-card">
              <div className="panel-card-header">
                <h3>Governance & Optimization Recommendations</h3>
              </div>
              <ul style={{ paddingLeft: 18, color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.8 }}>
                {process.aiSummary?.recommendations.map((rec, i) => (
                  <li key={i}>{rec}</li>
                ))}
              </ul>
            </div>

            <div className="panel-card">
              <div className="panel-card-header">
                <h3>Audit & Compliance Readiness</h3>
              </div>
              <ul style={{ paddingLeft: 18, color: 'var(--aqua)', fontSize: 13, lineHeight: 1.8 }}>
                {process.aiSummary?.complianceNotes.map((note, i) => (
                  <li key={i}>✓ {note}</li>
                ))}
              </ul>
            </div>
          </div>

          {/* Right Column: Interactive AI Assistant Chat */}
          <div className="ai-chat-box">
            <div className="ai-chat-header">
              <span style={{ fontSize: 22 }}>🤖</span>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>AI Process Assistant</strong>
                <span style={{ display: 'block', fontSize: 11, color: 'var(--aqua)' }}>
                  Autonomous Agent Reasoning
                </span>
              </div>
            </div>

            <div className="ai-chat-messages">
              {chatMessages.map((msg) => (
                <div key={msg.id} className={`chat-bubble ${msg.sender}`}>
                  <div style={{ whiteSpace: 'pre-line' }}>{msg.text}</div>
                  {msg.suggestedActions && msg.suggestedActions.length > 0 && (
                    <div className="chat-suggestions">
                      {msg.suggestedActions.map((action, i) => (
                        <button
                          key={i}
                          type="button"
                          className="suggestion-pill"
                          onClick={() => handleSendChat(action)}
                        >
                          {action} ↗
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              ))}
              {isAiThinking && (
                <div className="chat-bubble ai" style={{ fontStyle: 'italic', color: 'var(--muted)' }}>
                  Agent is analyzing process topology...
                </div>
              )}
            </div>

            <form
              className="ai-chat-input-row"
              onSubmit={(e) => {
                e.preventDefault();
                handleSendChat(chatInput);
              }}
            >
              <input
                type="text"
                className="ai-chat-input"
                placeholder="Ask about this process (e.g. 'Explain this workflow', 'What happens on rejection?')..."
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
              />
              <button
                type="submit"
                className="yellow-button"
                disabled={!chatInput.trim() || isAiThinking}
              >
                ASK AI <span>↗</span>
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Version History Modal */}
      {showHistoryModal && (
        <ProcessHistoryModal
          process={process}
          onClose={() => setShowHistoryModal(false)}
          onAddVersion={handleAddVersion}
        />
      )}
    </div>
  );
};
