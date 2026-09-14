import React, { useState } from 'react';
import './ProcessReviewAgent.css';

import { AgentLoadingScreen } from './AgentLoadingScreen';

interface Props {
  onHighlightIssue?: (elementId: string | null) => void;
  bpmnXml: string | null;
}

interface QualityIssue {
  ruleId: string;
  severity: string;
  elementId?: string | null;
  issue: string;
  suggestion?: string | null;
}

interface QualityReport {
  valid: boolean;
  qualityScore: number;
  issues: QualityIssue[];
  recommendations: string[];
}

interface ProcessStep {
  sequence: number;
  name: string;
  description: string;
}

interface ProcessDecision {
  name: string;
  description: string;
}

interface ReviewReport {
  processName: string;
  summary: string;
  participants: string[];
  steps: ProcessStep[];
  decisions: ProcessDecision[];
}

const reportCache = new Map<string, ReviewReport>();
const qualityCache = new Map<string, QualityReport>();

export const ProcessReviewAgent: React.FC<Props> = ({ bpmnXml, onHighlightIssue }) => {
  const [report, setReport] = useState<ReviewReport | null>(() => {
    return bpmnXml ? reportCache.get(bpmnXml) || null : null;
  });
  const [quality, setQuality] = useState<QualityReport | null>(() => {
    return bpmnXml ? qualityCache.get(bpmnXml) || null : null;
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasFetched = React.useRef(false);

  React.useEffect(() => {
    if (bpmnXml) {
      if (reportCache.has(bpmnXml)) {
        setReport(reportCache.get(bpmnXml)!);
      } else if (!report && !loading && !error && !hasFetched.current) {
        hasFetched.current = true;
        handleGenerateSummary();
      }
      // Always try to load defects — cheap deterministic call.
      loadQuality(bpmnXml);
    }
  }, [bpmnXml]);

  const loadQuality = async (xml: string) => {
    if (qualityCache.has(xml)) {
      setQuality(qualityCache.get(xml)!);
      return;
    }
    try {
      const res = await fetch('http://localhost:8080/api/v1/process/import-bpmn', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ xml }),
      });
      if (!res.ok) return;
      const data = await res.json();
      if (data?.qualityReport) {
        qualityCache.set(xml, data.qualityReport);
        setQuality(data.qualityReport);
      }
    } catch { /* swallow — defects panel just won't render */ }
  };

  const handleGenerateSummary = async () => {
    if (!bpmnXml) {
      setError("No BPMN XML available. Please generate or import a BPMN diagram first.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await fetch('http://localhost:8080/api/v1/process/review/summary', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: bpmnXml }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `Failed to generate review: ${response.statusText}`);
      }

      const data = await response.json();
      if (bpmnXml) {
        reportCache.set(bpmnXml, data);
      }
      setReport(data);
    } catch (err: any) {
      setError(err.message || "An error occurred during review generation.");
    } finally {
      setLoading(false);
    }
  };



  const handleRegenerateSummary = () => {
    if (bpmnXml) {
      reportCache.delete(bpmnXml);
    }
    hasFetched.current = false;
    setReport(null);
    handleGenerateSummary();
  };

  if (loading) {
    return (
      <AgentLoadingScreen
        title="Process Review Agent"
        mascot="👀"
        steps={[
          { title: 'Parsing BPMN', desc: 'Analyzing diagram structure and gateways...', weight: 2000 },
          { title: 'Semantic Translation', desc: 'Translating BPMN nodes to business language...', weight: 4000 },
          { title: 'Synthesizing Summary', desc: 'Generating detailed executive overview...', weight: 3000 },
        ]}
      />
    );
  }

  return (
    <div className="process-review-agent">
      {!report && (
        <div className="agent-placeholder-card">
          <span className="agent-icon">👀</span>
          <h3>Agent 04: Process Review & Translation Agent</h3>
          <p>
            Translates technical BPMN XML back into clear executive summaries and audit reports.
          </p>
          {error && (
            <div style={{ marginTop: '20px' }}>
              <p className="error-text">{error}</p>
              <button className="yellow-button" onClick={() => { hasFetched.current = false; handleGenerateSummary(); }}>
                RETRY GENERATION
              </button>
            </div>
          )}
        </div>
      )}

      {report && (
        <div className="review-report-card">
          <div className="report-header">
            <div>
              <p style={{ color: 'var(--muted)', fontSize: 13, margin: '0 0 4px', textTransform: 'uppercase', letterSpacing: 1 }}>Process Name</p>
              <h3>{report.processName || 'Executive Process Summary'}</h3>
            </div>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="btn-ghost" onClick={handleRegenerateSummary} style={{ color: 'var(--aqua)', borderColor: 'rgba(57, 245, 208, 0.3)' }}>
                REGENERATE RESPONSE <span>↺</span>
              </button>
              <button className="btn-ghost" onClick={() => setReport(null)}>
                Reset
              </button>
            </div>
          </div>
          <div className="report-content">
            <div className="summary-section">
              <h4>Process Summary</h4>
              <p>{report.summary}</p>
            </div>
            {report.participants && report.participants.length > 0 && (
              <div className="issues-section">
                <h4>Participants</h4>
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  {report.participants.map((p, idx) => (
                    <span key={idx} style={{ background: 'rgba(88, 170, 205, 0.1)', border: '1px solid rgba(88, 170, 205, 0.3)', padding: '4px 12px', borderRadius: '16px', fontSize: '13px', color: 'var(--aqua)' }}>{p}</span>
                  ))}
                </div>
              </div>
            )}
            {report.steps && report.steps.length > 0 && (
              <div className="recommendations-section">
                <h4>Process Steps</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {report.steps.map((step) => (
                    <div key={step.sequence} style={{ background: 'rgba(57, 245, 208, 0.05)', padding: '12px 16px', borderRadius: '8px', borderLeft: '3px solid var(--aqua)' }}>
                      <strong style={{ color: 'var(--aqua)' }}>{step.sequence}. {step.name}</strong>
                      <p style={{ margin: '4px 0 0', color: 'var(--muted)', fontSize: '14px' }}>{step.description}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {report.decisions && report.decisions.length > 0 && (
              <div className="recommendations-section">
                <h4>Key Decisions & Approvals</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {report.decisions.map((dec, idx) => (
                    <div key={idx} style={{ background: 'rgba(198, 255, 0, 0.08)', padding: '12px 16px', borderRadius: '8px', borderLeft: '3px solid var(--electric)' }}>
                      <strong style={{ color: 'var(--electric)' }}>{dec.name}</strong>
                      <p style={{ margin: '4px 0 0', color: 'var(--muted)', fontSize: '14px' }}>{dec.description}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {quality && (
              <>
                <div className="recommendations-section">
                  <h4 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    Process Quality & Audit Readiness
                    <span style={{
                      background: quality.qualityScore >= 80 ? 'rgba(57, 245, 208, 0.15)' :
                                  quality.qualityScore >= 50 ? 'rgba(251, 212, 55, 0.15)' :
                                  'rgba(255, 107, 107, 0.15)',
                      color: quality.qualityScore >= 80 ? 'var(--aqua)' :
                             quality.qualityScore >= 50 ? '#fbd437' : '#ff6b6b',
                      padding: '4px 12px', borderRadius: '999px', fontFamily: 'var(--mono)', fontSize: '12px',
                    }}>
                      Score {quality.qualityScore}/100 • {quality.issues?.length || 0} finding{(quality.issues?.length || 0) === 1 ? '' : 's'}
                    </span>
                  </h4>
                  <p style={{ margin: '4px 0 12px', fontSize: '13px', color: 'var(--muted)' }}>
                    {quality.qualityScore >= 80 ? 'Process model is highly structured and audit-ready.' :
                     quality.qualityScore >= 50 ? 'Process model is functional but has structural risks and bottlenecks.' :
                     'Process model has severe validation defects and requires immediate fixing.'}
                  </p>
                </div>

                {/* Validation Findings */}
                {quality.issues?.filter(i => (i.severity || '').toUpperCase() === 'HIGH').length > 0 && (
                  <div className="recommendations-section">
                    <h4 style={{ color: '#ff6b6b' }}>Validation Findings (High Priority)</h4>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      {quality.issues.filter(i => (i.severity || '').toUpperCase() === 'HIGH').map((iss, idx) => (
                        <div key={idx} className="issue-card" onClick={() => onHighlightIssue && onHighlightIssue(iss.elementId || null)} style={{ cursor: onHighlightIssue && iss.elementId ? 'pointer' : 'default',  background: 'rgba(255, 107, 107, 0.08)', padding: '12px 14px', borderRadius: '8px', borderLeft: '3px solid #ff6b6b' }}>
                          <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 4 }}>
                            <strong style={{ color: 'var(--white)', fontSize: 13 }}>{iss.ruleId}</strong>
                            {iss.elementId && (
                              <span style={{ color: 'var(--muted)', fontFamily: 'var(--mono)', fontSize: 11 }}>• {iss.elementId}</span>
                            )}
                          </div>
                          <p style={{ margin: 0, color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.5 }}>{iss.issue}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Risks & Bottlenecks */}
                {quality.issues?.filter(i => (i.severity || '').toUpperCase() === 'MEDIUM' || (i.severity || '').toUpperCase() === 'LOW').length > 0 && (
                  <div className="recommendations-section">
                    <h4 style={{ color: '#fbd437' }}>Risks & Bottlenecks</h4>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      {quality.issues.filter(i => (i.severity || '').toUpperCase() === 'MEDIUM' || (i.severity || '').toUpperCase() === 'LOW').map((iss, idx) => (
                        <div key={idx} className="issue-card" onClick={() => onHighlightIssue && onHighlightIssue(iss.elementId || null)} style={{ cursor: onHighlightIssue && iss.elementId ? 'pointer' : 'default',  background: 'rgba(251, 212, 55, 0.08)', padding: '12px 14px', borderRadius: '8px', borderLeft: '3px solid #fbd437' }}>
                          <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 4 }}>
                            <strong style={{ color: 'var(--white)', fontSize: 13 }}>{iss.ruleId}</strong>
                            {iss.elementId && (
                              <span style={{ color: 'var(--muted)', fontFamily: 'var(--mono)', fontSize: 11 }}>• {iss.elementId}</span>
                            )}
                          </div>
                          <p style={{ margin: 0, color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.5 }}>{iss.issue}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Improvement Suggestions */}
                {quality.issues?.some(i => i.suggestion) && (
                  <div className="recommendations-section">
                    <h4 style={{ color: 'var(--aqua)' }}>Improvement Suggestions</h4>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      {quality.issues.filter(i => i.suggestion).map((iss, idx) => (
                        <div key={idx} className="issue-card" onClick={() => onHighlightIssue && onHighlightIssue(iss.elementId || null)} style={{ cursor: onHighlightIssue && iss.elementId ? 'pointer' : 'default',  background: 'rgba(57, 245, 208, 0.05)', padding: '12px 14px', borderRadius: '8px', borderLeft: '3px solid var(--aqua)' }}>
                          <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 4 }}>
                            <strong style={{ color: 'var(--aqua)', fontSize: 13 }}>Suggested Fix</strong>
                            {iss.elementId && (
                              <span style={{ color: 'var(--muted)', fontFamily: 'var(--mono)', fontSize: 11 }}>for {iss.elementId}</span>
                            )}
                          </div>
                          <p style={{ margin: 0, color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.5 }}>{iss.suggestion}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
