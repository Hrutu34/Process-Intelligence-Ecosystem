import React from 'react';
import type { ProcessEntity, UserSession } from '../services/types';
import './DashboardPage.css';

interface Props {
  user: UserSession | null;
  processes: ProcessEntity[];
  onOpenProcess: (id: string) => void;
  onOpenUploadModal: () => void;
  onNavigateToKnowledge: () => void;
  onNavigateToDocuments: () => void;
}

export const DashboardPage: React.FC<Props> = ({
  user,
  processes,
  onOpenProcess,
  onOpenUploadModal,
  onNavigateToKnowledge,
  onNavigateToDocuments,
}) => {
  // Aggregate statistics
  const totalProcesses = processes.length;
  const totalDocs = processes.length;
  const totalEntities = processes.reduce((acc, p) => {
    return acc + (p.knowledge.activities?.length || 0) + (p.knowledge.actors?.length || 0) + (p.knowledge.gateways?.length || 0);
  }, 0);
  const avgQuality = Math.round(
    processes.reduce((acc, p) => acc + p.qualityScore, 0) / (processes.length || 1)
  );
  const totalIssues = processes.reduce((acc, p) => acc + p.validationIssues.filter((i) => !i.isApplied).length, 0);

  return (
    <div className="dashboard-container">
      {/* Top Greeting */}
      <div className="dashboard-header-row">
        <div className="dashboard-greeting">
          <h2>Good evening, {user?.name || 'Process Architect'} 👋</h2>
          <p>Here is an overview of your autonomous process intelligence workspace.</p>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <button type="button" className="btn-ghost" onClick={onNavigateToDocuments}>
            📁 My Documents
          </button>
          <button type="button" className="yellow-button" onClick={onOpenUploadModal}>
            + UPLOAD DOCUMENT <span>↗</span>
          </button>
        </div>
      </div>

      {/* 5 Stats Cards */}
      <div className="stats-grid">
        <div className="stat-card">
          <span className="stat-card-label">Total Processes</span>
          <div className="stat-card-value">{totalProcesses}</div>
          <span className="stat-card-subtitle">Active process workflows</span>
        </div>

        <div className="stat-card">
          <span className="stat-card-label">Total Documents</span>
          <div className="stat-card-value">{totalDocs}</div>
          <span className="stat-card-subtitle">Ingested SOPs & policies</span>
        </div>

        <div className="stat-card">
          <span className="stat-card-label">Extracted Entities</span>
          <div className="stat-card-value highlight-aqua">{totalEntities}</div>
          <span className="stat-card-subtitle">Tasks, actors, & gateways</span>
        </div>

        <div className="stat-card">
          <span className="stat-card-label">Avg Quality Score</span>
          <div className="stat-card-value highlight-electric">{avgQuality}%</div>
          <span className="stat-card-subtitle">BPMN 2.0 readiness score</span>
        </div>

        <div className="stat-card">
          <span className="stat-card-label">Open Quality Gaps</span>
          <div className="stat-card-value" style={{ color: totalIssues > 0 ? '#ff6b81' : 'var(--aqua)' }}>
            {totalIssues}
          </div>
          <span className="stat-card-subtitle">Actionable validation items</span>
        </div>
      </div>

      {/* Main Grid: Processes Table (Left) + Quick Actions (Right) */}
      <div className="dashboard-main-grid">
        {/* Left: Recent Processes */}
        <div className="panel-card">
          <div className="panel-card-header">
            <h3>⚡ Process Portfolio</h3>
            <span style={{ fontSize: 12, color: 'var(--muted)', fontFamily: 'var(--mono)' }}>
              {processes.length} Processes
            </span>
          </div>

          <table className="processes-table">
            <thead>
              <tr>
                <th>Process Name</th>
                <th>Source Document</th>
                <th>Quality Score</th>
                <th>Status</th>
                <th>Last Updated</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {processes.map((proc) => (
                <tr key={proc.id}>
                  <td className="process-name-cell">
                    <strong>{proc.name}</strong>
                    <span>{proc.currentVersion} • {proc.knowledge.activities?.length || 0} activities</span>
                  </td>
                  <td>
                    <span style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>{proc.sourceDocument}</span>
                  </td>
                  <td>
                    <span className={`quality-badge ${proc.qualityScore >= 85 ? 'high' : 'medium'}`}>
                      {proc.qualityScore}%
                    </span>
                  </td>
                  <td>
                    <span
                      className={`status-badge-table ${
                        proc.status === 'Validated' ? 'validated' : 'needs-review'
                      }`}
                    >
                      {proc.status}
                    </span>
                  </td>
                  <td style={{ color: 'var(--muted)', fontSize: 12 }}>{proc.lastUpdated}</td>
                  <td>
                    <button
                      type="button"
                      className="btn-ghost"
                      style={{ padding: '6px 12px', fontSize: 11 }}
                      onClick={() => onOpenProcess(proc.id)}
                    >
                      Open Workspace ↗
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Right: Quick Actions & Activity */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div className="panel-card">
            <div className="panel-card-header">
              <h3>⚡ Quick Actions</h3>
            </div>

            <div className="quick-actions-grid">
              <div className="quick-action-item" onClick={onOpenUploadModal}>
                <div className="quick-action-left">
                  <span className="quick-action-icon">📄</span>
                  <div>
                    <div className="quick-action-title">Upload SOP Document</div>
                    <div className="quick-action-desc">Ingest PDF, Word, or TXT</div>
                  </div>
                </div>
                <span>→</span>
              </div>

              <div className="quick-action-item" onClick={onOpenUploadModal}>
                <div className="quick-action-left">
                  <span className="quick-action-icon">✍️</span>
                  <div>
                    <div className="quick-action-title">Create Process from Free Text</div>
                    <div className="quick-action-desc">Instant natural language input</div>
                  </div>
                </div>
                <span>→</span>
              </div>

              <div className="quick-action-item" onClick={onNavigateToKnowledge}>
                <div className="quick-action-left">
                  <span className="quick-action-icon">🧠</span>
                  <div>
                    <div className="quick-action-title">Explore Knowledge Base</div>
                    <div className="quick-action-desc">Cross-process entity matrix</div>
                  </div>
                </div>
                <span>→</span>
              </div>

              <div className="quick-action-item" onClick={() => onOpenProcess('proc_travel_request')}>
                <div className="quick-action-left">
                  <span className="quick-action-icon">⌘</span>
                  <div>
                    <div className="quick-action-title">Open Travel Request Demo</div>
                    <div className="quick-action-desc">Primary hackathon demo process</div>
                  </div>
                </div>
                <span>→</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
