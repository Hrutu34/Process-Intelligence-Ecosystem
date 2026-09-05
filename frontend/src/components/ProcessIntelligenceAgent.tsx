import React, { useState, useEffect } from 'react';
import type { ProcessKnowledgeDTO, ProcessQualityReportDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import './ProcessIntelligenceAgent.css';

interface Props {
  knowledge: ProcessKnowledgeDTO;
  onProceedToBpmn: () => void;
}

export const ProcessIntelligenceAgent: React.FC<Props> = ({ knowledge, onProceedToBpmn }) => {
  const [report, setReport] = useState<ProcessQualityReportDTO | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [filterSeverity, setFilterSeverity] = useState<string>('ALL');

  const fetchQualityReport = () => {
    setLoading(true);
    setError(null);

    fetch('http://localhost:8080/api/v1/process/validate-knowledge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(knowledge),
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}: Validation request failed`);
        return res.json();
      })
      .then((data: ProcessQualityReportDTO) => {
        setReport(data);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Process validation failed:', err);
        setError(err.message || 'Failed to analyze process gaps');
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchQualityReport();
  }, [knowledge]);

  if (loading) {
    return (
      <div className="pi-container">
        <div className="pi-loading-card">
          <div className="pi-spinner" />
          <h3>Agent 02: Analyzing Process Quality & Gaps...</h3>
          <p>
            Running BPMN 2.0 validation rules (Start Events, End Conditions, Gateway Correctness, Dead-Ends).
          </p>
        </div>
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="pi-container">
        <div className="pi-error-card">
          <h3>⚠️ Process Intelligence Analysis Unavailable</h3>
          <p>{error || 'Could not retrieve validation report.'}</p>
          <button type="button" className="btn-ghost" onClick={fetchQualityReport}>
            ↺ Retry Analysis
          </button>
        </div>
      </div>
    );
  }

  const issues = report.issues || [];
  const filteredIssues = filterSeverity === 'ALL'
    ? issues
    : issues.filter((i) => i.severity.toUpperCase() === filterSeverity);

  const highIssues = issues.filter((i) => i.severity.toUpperCase() === 'HIGH');
  const mediumIssues = issues.filter((i) => i.severity.toUpperCase() === 'MEDIUM');

  return (
    <div className="pi-container">
      {/* Top Header Card */}
      <div className="pi-header">
        <div className="pi-header-info">
          <div className="status-pill">
            <span className="status-dot" />
            AGENT 02 / PROCESS INTELLIGENCE
            <span className="status-separator">/</span>
            {issues.length === 0 ? 'CLEAN MODEL' : `${issues.length} GAPS DETECTED`}
          </div>
          <h2>Process Intelligence & Quality Assessment</h2>
          <p>Automated verification of process completeness, gateway correctness, and start/end boundaries.</p>
        </div>

        <div className="pi-header-actions">
          <button type="button" className="btn-ghost" onClick={fetchQualityReport}>
            ↺ Re-run Rules
          </button>
          <button type="button" className="yellow-button" onClick={onProceedToBpmn}>
            PROCEED TO BPMN MODELLING <span>↗</span>
          </button>
        </div>
      </div>

      {/* KPI Stats Bar */}
      <div className="pi-stats-grid">
        <div className="pi-stat-card score-card">
          <span className="pi-stat-label">Model Quality Score</span>
          <div className="pi-stat-value">
            <span className={`score-number ${report.qualityScore >= 80 ? 'good' : report.qualityScore >= 50 ? 'warn' : 'bad'}`}>
              {report.qualityScore}%
            </span>
          </div>
          <span className="pi-stat-sub">
            {report.valid ? '✓ Passed structural integrity checks' : '⚠️ Quality issues require attention'}
          </span>
        </div>

        <div className="pi-stat-card">
          <span className="pi-stat-label">High Severity Gaps</span>
          <div className="pi-stat-value text-red">{highIssues.length}</div>
          <span className="pi-stat-sub">Broken paths, missing start/end</span>
        </div>

        <div className="pi-stat-card">
          <span className="pi-stat-label">Medium Severity</span>
          <div className="pi-stat-value text-yellow">{mediumIssues.length}</div>
          <span className="pi-stat-sub">Ambiguous gateways, missing conditions</span>
        </div>

        <div className="pi-stat-card">
          <span className="pi-stat-label">Recommendations</span>
          <div className="pi-stat-value text-aqua">{report.recommendations?.length || 0}</div>
          <span className="pi-stat-sub">BPMN modeling suggestions</span>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="pi-section-header">
        <h3>Detected Gaps & Verification Rules</h3>
        <div className="pi-filters">
          <button
            type="button"
            className={`pi-filter-btn ${filterSeverity === 'ALL' ? 'active' : ''}`}
            onClick={() => setFilterSeverity('ALL')}
          >
            All ({issues.length})
          </button>
          <button
            type="button"
            className={`pi-filter-btn ${filterSeverity === 'HIGH' ? 'active' : ''}`}
            onClick={() => setFilterSeverity('HIGH')}
          >
            High ({highIssues.length})
          </button>
          <button
            type="button"
            className={`pi-filter-btn ${filterSeverity === 'MEDIUM' ? 'active' : ''}`}
            onClick={() => setFilterSeverity('MEDIUM')}
          >
            Medium ({mediumIssues.length})
          </button>
        </div>
      </div>

      {/* Issues List */}
      {filteredIssues.length === 0 ? (
        <div className="pi-clean-card">
          <span className="pi-clean-icon">✓</span>
          <h4>No Gaps Detected</h4>
          <p>Your process model satisfies all start event, end condition, and gateway validation rules.</p>
        </div>
      ) : (
        <div className="pi-issues-grid">
          {filteredIssues.map((issue, idx) => (
            <div className={`pi-issue-card severity-${issue.severity.toLowerCase()}`} key={idx}>
              <div className="pi-issue-top">
                <span className={`pi-severity-badge ${issue.severity.toLowerCase()}`}>
                  {issue.severity}
                </span>
                <span className="pi-rule-id">{issue.ruleId}</span>
                {issue.elementId && (
                  <span className="pi-element-tag">ID: {issue.elementId}</span>
                )}
              </div>

              <h4 className="pi-issue-title">{issue.issue}</h4>
              <p className="pi-issue-suggestion">
                <strong>💡 Suggestion:</strong> {issue.suggestion}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Recommendations Box */}
      {report.recommendations && report.recommendations.length > 0 && (
        <div className="pi-recommendations-card">
          <div className="pi-rec-header">
            <span>💡</span>
            <h4>Agent 02 Recommended Fixes</h4>
          </div>
          <ul className="pi-rec-list">
            {report.recommendations.map((rec, i) => (
              <li key={i}>{rec}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};
