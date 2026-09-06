import React, { useState } from 'react';
import './SettingsPage.css';

interface Props {
  onResetDemoData: () => void;
}

export const SettingsPage: React.FC<Props> = ({ onResetDemoData }) => {
  const [model, setModel] = useState('pie-canonical-v1');
  const [autoValidate, setAutoValidate] = useState(true);
  const [toast, setToast] = useState(false);

  const handleSave = () => {
    setToast(true);
    setTimeout(() => setToast(false), 2000);
  };

  return (
    <div className="settings-container">
      <div>
        <h2 style={{ fontSize: 26, fontWeight: 700, marginBottom: 4 }}>Workspace Settings</h2>
        <p style={{ color: 'var(--muted)', fontSize: 14 }}>
          Manage backend endpoints, autonomous agent configurations, and export parameters.
        </p>
      </div>

      {/* Backend Health Section */}
      <div className="settings-section">
        <div className="settings-section-header">
          <h3>Backend Microservice Connection</h3>
          <span style={{ fontSize: 12, color: 'var(--muted)' }}>Spring Boot 3.3.0 Engine</span>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-label">API Gateway URL</div>
            <div className="settings-sublabel">Endpoint for extraction and graph builder services</div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="type-pill" style={{ color: 'var(--aqua)' }}>
              ● http://localhost:8080 (Active)
            </span>
          </div>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-label">In-Memory H2 Database</div>
            <div className="settings-sublabel">Embedded session storage (jdbc:h2:mem:pie_db)</div>
          </div>
          <span className="type-pill">Online</span>
        </div>
      </div>

      {/* Extraction Agent Configuration */}
      <div className="settings-section">
        <div className="settings-section-header">
          <h3>Extraction & Reasoning Agent Settings</h3>
          <span style={{ fontSize: 12, color: 'var(--muted)' }}>NLP and Canonical Graph Builder parameters</span>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-label">Active Discovery Engine</div>
            <div className="settings-sublabel">Deterministic semantic parser & slug generator</div>
          </div>
          <select
            className="filter-select"
            value={model}
            onChange={(e) => setModel(e.target.value)}
          >
            <option value="pie-canonical-v1">P.I.E. Canonical Graph Builder v1.0 (Local)</option>
            <option value="gemini-2.5-flash">Google Gemini 2.5 Flash Agent (Cloud)</option>
            <option value="deepseek-r1">DeepSeek-R1 Process Reasoner</option>
          </select>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-label">Automatic BPMN Validation</div>
            <div className="settings-sublabel">Run quality gap detection automatically upon upload</div>
          </div>
          <input
            type="checkbox"
            checked={autoValidate}
            onChange={(e) => setAutoValidate(e.target.checked)}
            style={{ width: 18, height: 18, accentColor: 'var(--aqua)', cursor: 'pointer' }}
          />
        </div>
      </div>

      {/* Demo Reset Section */}
      <div className="settings-section">
        <div className="settings-section-header">
          <h3>Reset Demo Environment</h3>
          <span style={{ fontSize: 12, color: 'var(--muted)' }}>Restore default enterprise demo processes</span>
        </div>

        <div className="settings-row">
          <div>
            <div className="settings-label">Restore Demo Data</div>
            <div className="settings-sublabel">Reload Travel Request, Onboarding, and Purchase Approval datasets</div>
          </div>
          <button
            type="button"
            className="btn-ghost"
            style={{ color: '#ff6b81', borderColor: 'rgba(255, 71, 87, 0.4)' }}
            onClick={() => {
              if (confirm('Reset workspace to initial demo state?')) {
                onResetDemoData();
              }
            }}
          >
            ↺ Reset Workspace Data
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
        <button type="button" className="yellow-button" onClick={handleSave}>
          {toast ? '✓ SETTINGS SAVED' : 'SAVE SETTINGS'} <span>↗</span>
        </button>
      </div>
    </div>
  );
};
