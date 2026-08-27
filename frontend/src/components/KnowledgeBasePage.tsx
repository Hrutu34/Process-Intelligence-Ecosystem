import React, { useState } from 'react';
import { knowledgeService } from '../services/knowledgeService';
import './KnowledgeBasePage.css';

type KbTab = 'actors' | 'activities' | 'decisions';

interface Props {
  onOpenProcessByName?: (name: string) => void;
}

export const KnowledgeBasePage: React.FC<Props> = () => {
  const [activeTab, setActiveTab] = useState<KbTab>('actors');
  const stats = knowledgeService.getGlobalStats();
  const actors = knowledgeService.getActors();
  const activities = knowledgeService.getActivities();
  const decisions = knowledgeService.getDecisions();

  return (
    <div className="kb-container">
      {/* Header */}
      <div>
        <h2 style={{ fontSize: 26, fontWeight: 700, marginBottom: 4 }}>Global Process Knowledge Base</h2>
        <p style={{ color: 'var(--muted)', fontSize: 14 }}>
          Unified cross-document ontology of actors, tasks, decision gates, and systems extracted by P.I.E.
        </p>
      </div>

      {/* Top Entity Counter Bar */}
      <div className="kb-stats-bar">
        <div className="kb-stat-item">
          <span className="kb-stat-icon">👤</span>
          <div>
            <div className="kb-stat-number">{stats.actorsCount}</div>
            <div className="kb-stat-label">Actors & Roles</div>
          </div>
        </div>

        <div className="kb-stat-item">
          <span className="kb-stat-icon">⚡</span>
          <div>
            <div className="kb-stat-number">{stats.activitiesCount}</div>
            <div className="kb-stat-label">Activities</div>
          </div>
        </div>

        <div className="kb-stat-item">
          <span className="kb-stat-icon">🔀</span>
          <div>
            <div className="kb-stat-number">{stats.decisionsCount}</div>
            <div className="kb-stat-label">Decision Gates</div>
          </div>
        </div>

        <div className="kb-stat-item">
          <span className="kb-stat-icon">⏱️</span>
          <div>
            <div className="kb-stat-number">{stats.eventsCount}</div>
            <div className="kb-stat-label">Events & Triggers</div>
          </div>
        </div>

        <div className="kb-stat-item">
          <span className="kb-stat-icon">💻</span>
          <div>
            <div className="kb-stat-number">{stats.systemsCount}</div>
            <div className="kb-stat-label">Systems & Tools</div>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="kb-tabs-row">
        <button
          type="button"
          className={`kb-tab-btn ${activeTab === 'actors' ? 'active' : ''}`}
          onClick={() => setActiveTab('actors')}
        >
          <span>👤 Actors ({actors.length})</span>
        </button>
        <button
          type="button"
          className={`kb-tab-btn ${activeTab === 'activities' ? 'active' : ''}`}
          onClick={() => setActiveTab('activities')}
        >
          <span>⚡ Activities ({activities.length})</span>
        </button>
        <button
          type="button"
          className={`kb-tab-btn ${activeTab === 'decisions' ? 'active' : ''}`}
          onClick={() => setActiveTab('decisions')}
        >
          <span>🔀 Decisions & Gateways ({decisions.length})</span>
        </button>
      </div>

      {/* Tab 1: Actors Table */}
      {activeTab === 'actors' && (
        <div className="panel-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="processes-table">
            <thead>
              <tr>
                <th>Actor Name</th>
                <th>Functional Role</th>
                <th>Processes Involved</th>
                <th>Source Documents</th>
                <th>Assigned Tasks</th>
              </tr>
            </thead>
            <tbody>
              {actors.map((actor, idx) => (
                <tr key={idx}>
                  <td className="process-name-cell">
                    <strong>👤 {actor.name}</strong>
                  </td>
                  <td>
                    <span style={{ color: 'var(--soft-white)' }}>{actor.role}</span>
                  </td>
                  <td>
                    {actor.processes.map((p, i) => (
                      <span key={i} className="type-pill" style={{ marginRight: 6 }}>
                        {p}
                      </span>
                    ))}
                  </td>
                  <td>
                    {actor.documents.map((d, i) => (
                      <span key={i} style={{ display: 'block', fontSize: 12, color: 'var(--muted)', fontFamily: 'var(--mono)' }}>
                        {d}
                      </span>
                    ))}
                  </td>
                  <td>
                    <span style={{ fontFamily: 'var(--mono)', color: 'var(--aqua)', fontWeight: 600 }}>
                      {actor.activityCount} activities
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Tab 2: Activities Table */}
      {activeTab === 'activities' && (
        <div className="panel-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="processes-table">
            <thead>
              <tr>
                <th>Activity Name</th>
                <th>Assigned Role</th>
                <th>Process</th>
                <th>Source Document</th>
                <th>Execution Mode</th>
              </tr>
            </thead>
            <tbody>
              {activities.map((act, idx) => (
                <tr key={idx}>
                  <td className="process-name-cell">
                    <strong>⚡ {act.name}</strong>
                  </td>
                  <td>
                    <span className="type-pill">👤 {act.assignedRole}</span>
                  </td>
                  <td style={{ color: 'var(--soft-white)', fontWeight: 500 }}>{act.processName}</td>
                  <td style={{ fontFamily: 'var(--mono)', fontSize: 12, color: 'var(--muted)' }}>{act.documentName}</td>
                  <td>
                    <span
                      style={{
                        padding: '3px 8px',
                        borderRadius: 6,
                        fontSize: 11,
                        background: act.isAutomated ? 'rgba(198, 255, 0, 0.15)' : 'rgba(57, 245, 208, 0.12)',
                        color: act.isAutomated ? 'var(--electric)' : 'var(--aqua)',
                        fontWeight: 600,
                      }}
                    >
                      {act.isAutomated ? 'Automated Service' : 'Human User Task'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Tab 3: Decisions Table */}
      {activeTab === 'decisions' && (
        <div className="panel-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="processes-table">
            <thead>
              <tr>
                <th>Gateway Name</th>
                <th>Decision Type</th>
                <th>Associated Process</th>
                <th>Branches Evaluated</th>
              </tr>
            </thead>
            <tbody>
              {decisions.map((dec, idx) => (
                <tr key={idx}>
                  <td className="process-name-cell">
                    <strong>🔀 {dec.name}</strong>
                  </td>
                  <td>
                    <span className="type-pill">{dec.decisionType} Gateway</span>
                  </td>
                  <td style={{ color: 'var(--soft-white)', fontWeight: 500 }}>{dec.processName}</td>
                  <td>
                    <span style={{ fontFamily: 'var(--mono)', color: '#ffe57f' }}>
                      {dec.conditionCount} branch conditions
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
