import React, { useState } from 'react';
import type { ProcessEntity } from '../services/types';
import './ProcessHistoryModal.css';

interface Props {
  process: ProcessEntity;
  onClose: () => void;
  onAddVersion: (summary: string) => void;
}

export const ProcessHistoryModal: React.FC<Props> = ({
  process,
  onClose,
  onAddVersion,
}) => {
  const [newSummary, setNewSummary] = useState('');

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSummary.trim()) return;
    onAddVersion(newSummary.trim());
    setNewSummary('');
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="history-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="history-modal-header">
          <div>
            <h3>Version History & Audit Log</h3>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>{process.name}</span>
          </div>
          <button type="button" className="btn-ghost" onClick={onClose}>
            ✕
          </button>
        </div>

        {/* Create new checkpoint */}
        <form onSubmit={handleCreate} style={{ display: 'flex', gap: 10 }}>
          <input
            type="text"
            className="login-input"
            placeholder="Tag a new version checkpoint (e.g. Added SLA timers)..."
            value={newSummary}
            onChange={(e) => setNewSummary(e.target.value)}
          />
          <button type="submit" className="yellow-button" style={{ whiteSpace: 'nowrap' }}>
            + TAG VERSION
          </button>
        </form>

        {/* Timeline list */}
        <div className="version-timeline">
          {process.versions.map((ver, idx) => (
            <div key={idx} className="version-item">
              <div className="version-tag">{ver.version}</div>
              <div className="version-info">
                <strong style={{ color: 'var(--white)', fontSize: 13 }}>{ver.summary}</strong>
                <div className="version-meta">
                  {ver.timestamp} • Author: {ver.author} • Quality Score: {ver.qualityScore}%
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
