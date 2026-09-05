import React, { useState, useEffect } from 'react';
import type { ProcessKnowledgeDTO, ProcessQualityReportDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { AgentLoadingScreen } from './AgentLoadingScreen';
import './ProcessIntelligenceAgent.css';

interface Props {
  knowledge: ProcessKnowledgeDTO;
  onProceedToBpmn: () => void;
}

const qualityReportCache = new Map<string, ProcessQualityReportDTO>();

interface MetricCardProps {
  label: string;
  value: string | number;
  sublabel: string;
  progress: number;
  tone: 'aqua' | 'yellow' | 'red';
  visual?: 'ring' | 'bar';
}

const MetricCard: React.FC<MetricCardProps> = ({
  label,
  value,
  sublabel,
  progress,
  tone,
  visual = 'ring',
}) => {
  const boundedProgress = Math.max(0, Math.min(100, progress));
  return (
    <div className="pi-stat-card">
      <span className="pi-stat-label">{label}</span>
      <div className="pi-stat-main">
        <div className={`pi-metric-visual ${visual} tone-${tone}`} style={{ '--metric-progress': `${boundedProgress}%` } as React.CSSProperties}>
          {visual === 'ring' ? <span>{value}</span> : <span className="pi-metric-bar" />}
        </div>
        <strong className={`pi-stat-value text-${tone}`}>{value}</strong>
      </div>
      <span className="pi-stat-sub">{sublabel}</span>
    </div>
  );
};

export const ProcessIntelligenceAgent: React.FC<Props> = ({ knowledge, onProceedToBpmn }) => {
  const knowledgeKey = JSON.stringify(knowledge);
  const [report, setReport] = useState<ProcessQualityReportDTO | null>(
    () => qualityReportCache.get(knowledgeKey) || null,
  );
  const [loading, setLoading] = useState<boolean>(() => !qualityReportCache.has(knowledgeKey));
  const [error, setError] = useState<string | null>(null);
  const [filterSeverity, setFilterSeverity] = useState<string>('ALL');

  const fetchQualityReport = (forceRefresh = false) => {
    if (!forceRefresh) {
      const cachedReport = qualityReportCache.get(knowledgeKey);
      if (cachedReport) {
        setReport(cachedReport);
        setLoading(false);
        return;
      }
    }

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
        qualityReportCache.set(knowledgeKey, data);
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
  }, [knowledgeKey]);

  if (loading) {
    return (
      <AgentLoadingScreen
        title="Process Intelligence Agent in Progress"
        mascot="◉"
        steps={[
          { title: 'Building Graph', desc: 'Agent 02: Building the process graph...', weight: 1800 },
          { title: 'Validating Rules', desc: 'Agent 02: Checking boundaries, gateways & dead ends...', weight: 3000 },
          { title: 'Analyzing Semantics', desc: 'Agent 02: Reviewing process quality and evidence...', weight: 5000 },
          { title: 'Generating Report', desc: 'Agent 02: Preparing findings and recommendations...', weight: 2200 },
        ]}
      />
    );
  }

  if (error || !report) {
    return (
      <div className="pi-container">
        <div className="pi-error-card">
          <h3>⚠️ Process Intelligence Analysis Unavailable</h3>
          <p>{error || 'Could not retrieve validation report.'}</p>
          <button type="button" className="btn-ghost" onClick={() => fetchQualityReport(true)}>
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
  const activitiesCount = knowledge.activities?.length || 0;
  const actorsCount = knowledge.actors?.length || 0;
  const gatewaysCount = knowledge.gateways?.length || 0;
  const maxEntityCount = Math.max(activitiesCount, actorsCount, gatewaysCount, 1);
  const totalIssues = issues.length || 1;

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
          <button type="button" className="btn-ghost" onClick={() => fetchQualityReport(true)}>
            ↺ Re-run Rules
          </button>
          <button type="button" className="yellow-button" onClick={onProceedToBpmn}>
            PROCEED TO BPMN MODELLING <span>↗</span>
          </button>
        </div>
      </div>

      {/* KPI Stats Bar */}
      <div className="pi-stats-grid">
        <MetricCard label="Activities" value={activitiesCount} progress={activitiesCount / maxEntityCount * 100} tone="aqua" sublabel="Extracted process actions" />
        <MetricCard label="Actors" value={actorsCount} progress={actorsCount / maxEntityCount * 100} tone="aqua" sublabel="People and departments" />
        <MetricCard label="Gateways" value={gatewaysCount} progress={gatewaysCount / maxEntityCount * 100} tone="yellow" sublabel="Decision points detected" />
        <MetricCard label="Model Quality" value={`${report.qualityScore}%`} progress={report.qualityScore} tone={report.qualityScore >= 80 ? 'aqua' : report.qualityScore >= 50 ? 'yellow' : 'red'} sublabel={report.valid ? 'Structural checks passed' : 'Quality issues require attention'} />
        <MetricCard label="High Severity" value={highIssues.length} progress={highIssues.length / totalIssues * 100} tone="red" visual="bar" sublabel="Broken paths and boundaries" />
        <MetricCard label="Medium Severity" value={mediumIssues.length} progress={mediumIssues.length / totalIssues * 100} tone="yellow" visual="bar" sublabel="Ambiguous or incomplete logic" />
        <MetricCard label="Recommendations" value={report.recommendations?.length || 0} progress={(report.recommendations?.length || 0) / Math.max(issues.length, 1) * 100} tone="aqua" visual="bar" sublabel="Actionable refinement ideas" />
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
                {issue.confidence != null && (
                  <span className="pi-confidence"> Confidence {Math.round(issue.confidence * 100)}%</span>
                )}
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
