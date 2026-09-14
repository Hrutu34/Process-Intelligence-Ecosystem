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
  const [showWalkthroughModal, setShowWalkthroughModal] = useState(false);
  const [expandedFindings, setExpandedFindings] = useState(true);
  const [expandedRisks, setExpandedRisks] = useState(false);
  const [expandedSuggestions, setExpandedSuggestions] = useState(false);

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
            
            <div style={{ padding: '20px 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
              <button 
                className="yellow-button" 
                style={{ width: '100%', padding: '14px', borderRadius: '8px', fontSize: '14px', letterSpacing: '1px', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px' }}
                onClick={() => setShowWalkthroughModal(true)}
              >
                <span>🚀 VIEW PROCESS WALKTHROUGH</span>
              </button>
            </div>

            {/* Walkthrough Modal Overlay */}
            {showWalkthroughModal && (
              <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(10px)', zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <div style={{ background: 'var(--card-bg)', width: '90vw', maxWidth: '1000px', height: '85vh', borderRadius: '16px', border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', overflow: 'hidden', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)' }}>
                  
                  {/* Modal Header */}
                  <div style={{ padding: '24px 32px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(0,0,0,0.2)' }}>
                    <div>
                      <p style={{ margin: '0 0 4px', color: 'var(--aqua)', fontSize: '12px', letterSpacing: '2px', textTransform: 'uppercase' }}>Interactive Journey</p>
                      <h2 style={{ margin: 0, color: 'var(--white)' }}>Process Walkthrough</h2>
                    </div>
                    <button 
                      onClick={() => setShowWalkthroughModal(false)}
                      style={{ background: 'transparent', border: 'none', color: 'var(--muted)', fontSize: '24px', cursor: 'pointer', padding: '8px' }}
                    >
                      ✕
                    </button>
                  </div>

                  {/* Modal Body - Creative Flow */}
                  <div style={{ flex: 1, overflowY: 'auto', padding: '40px' }}>
                    
                    {/* Participants Row */}
                    {report.participants && report.participants.length > 0 && (
                      <div style={{ marginBottom: '40px', textAlign: 'center' }}>
                        <h4 style={{ color: 'var(--muted)', letterSpacing: '1px', marginBottom: '16px' }}>KEY PARTICIPANTS</h4>
                        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', justifyContent: 'center' }}>
                          {report.participants.map((p, idx) => (
                            <span key={idx} style={{ background: 'rgba(88, 170, 205, 0.1)', border: '1px solid rgba(88, 170, 205, 0.3)', padding: '6px 16px', borderRadius: '24px', fontSize: '14px', color: 'var(--aqua)', boxShadow: '0 4px 12px rgba(88, 170, 205, 0.1)' }}>
                              👤 {p}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Timeline Flow */}
                    <div style={{ position: 'relative', maxWidth: '800px', margin: '0 auto', padding: '20px 0' }}>
                      <div style={{ position: 'absolute', left: '50%', top: 0, bottom: 0, width: '2px', background: 'linear-gradient(to bottom, var(--aqua) 0%, var(--electric) 100%)', transform: 'translateX(-50%)', opacity: 0.3 }} />
                      
                      {report.steps?.map((step, idx) => {
                        const isLeft = idx % 2 === 0;
                        return (
                          <div key={idx} style={{ display: 'flex', justifyContent: isLeft ? 'flex-start' : 'flex-end', width: '100%', marginBottom: '40px', position: 'relative' }}>
                            <div style={{ position: 'absolute', left: '50%', top: '24px', width: '16px', height: '16px', borderRadius: '50%', background: 'var(--bg-color)', border: '4px solid var(--aqua)', transform: 'translate(-50%, -50%)', zIndex: 2 }} />
                            
                            <div style={{ width: '45%', display: 'flex', justifyContent: isLeft ? 'flex-end' : 'flex-start' }}>
                              <div style={{ background: 'rgba(57, 245, 208, 0.03)', border: '1px solid rgba(57, 245, 208, 0.2)', padding: '24px', borderRadius: '16px', width: '100%', position: 'relative', textAlign: isLeft ? 'right' : 'left' }}>
                                <div style={{ position: 'absolute', top: '-14px', [isLeft ? 'right' : 'left']: '24px', background: 'var(--aqua)', color: '#000', padding: '4px 12px', borderRadius: '12px', fontSize: '12px', fontWeight: 'bold' }}>
                                  STEP {step.sequence}
                                </div>
                                <h3 style={{ margin: '12px 0 8px', color: 'var(--white)', fontSize: '18px' }}>{step.name}</h3>
                                <p style={{ margin: 0, color: 'var(--soft-white)', fontSize: '14px', lineHeight: 1.6 }}>{step.description}</p>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>

                    {/* Decisions Section at bottom */}
                    {report.decisions && report.decisions.length > 0 && (
                      <div style={{ marginTop: '60px', paddingTop: '40px', borderTop: '1px dashed rgba(255,255,255,0.1)' }}>
                        <h4 style={{ color: 'var(--electric)', letterSpacing: '1px', marginBottom: '24px', textAlign: 'center' }}>CRITICAL DECISION GATES</h4>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
                          {report.decisions.map((dec, idx) => (
                            <div key={idx} style={{ background: 'rgba(198, 255, 0, 0.05)', padding: '24px', borderRadius: '12px', border: '1px solid rgba(198, 255, 0, 0.2)', position: 'relative' }}>
                              <div style={{ position: 'absolute', top: -12, left: '50%', transform: 'translateX(-50%)' }}>
                                <div style={{ width: 24, height: 24, background: 'var(--electric)', transform: 'rotate(45deg)', borderRadius: '4px' }} />
                              </div>
                              <h4 style={{ margin: '16px 0 8px', color: 'var(--electric)', textAlign: 'center', fontSize: '16px' }}>{dec.name}</h4>
                              <p style={{ margin: 0, color: 'var(--muted)', fontSize: '14px', textAlign: 'center', lineHeight: 1.5 }}>{dec.description}</p>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
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
                      <h4 
                        style={{ color: '#ff6b6b', display: 'flex', justifyContent: 'space-between', cursor: 'pointer', margin: 0, padding: '12px 0', borderBottom: '1px solid rgba(255, 107, 107, 0.2)' }}
                        onClick={() => setExpandedFindings(!expandedFindings)}
                      >
                        <span>Validation Findings (High Priority)</span>
                        <span style={{ fontSize: '12px', transform: expandedFindings ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>▼</span>
                      </h4>
                      {expandedFindings && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '16px' }}>
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
                      )}
                    </div>
                  )}

                {/* Risks & Bottlenecks */}
                  {quality.issues?.filter(i => (i.severity || '').toUpperCase() === 'MEDIUM' || (i.severity || '').toUpperCase() === 'LOW').length > 0 && (
                    <div className="recommendations-section">
                      <h4 
                        style={{ color: '#fbd437', display: 'flex', justifyContent: 'space-between', cursor: 'pointer', margin: 0, padding: '12px 0', borderBottom: '1px solid rgba(251, 212, 55, 0.2)' }}
                        onClick={() => setExpandedRisks(!expandedRisks)}
                      >
                        <span>Risks & Bottlenecks</span>
                        <span style={{ fontSize: '12px', transform: expandedRisks ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>▼</span>
                      </h4>
                      {expandedRisks && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '16px' }}>
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
                      )}
                    </div>
                  )}

                {/* Improvement Suggestions */}
                  {quality.issues?.some(i => i.suggestion) && (
                    <div className="recommendations-section">
                      <h4 
                        style={{ color: 'var(--aqua)', display: 'flex', justifyContent: 'space-between', cursor: 'pointer', margin: 0, padding: '12px 0', borderBottom: '1px solid rgba(57, 245, 208, 0.2)' }}
                        onClick={() => setExpandedSuggestions(!expandedSuggestions)}
                      >
                        <span>Improvement Suggestions</span>
                        <span style={{ fontSize: '12px', transform: expandedSuggestions ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>▼</span>
                      </h4>
                      {expandedSuggestions && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '16px' }}>
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
                      )}
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
