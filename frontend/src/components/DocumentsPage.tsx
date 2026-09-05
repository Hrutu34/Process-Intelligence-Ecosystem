import React, { useState } from 'react';
import type { DocumentItem } from '../services/types';
import './DocumentsPage.css';

interface Props {
  documents: DocumentItem[];
  onOpenProcess: (processId: string) => void;
  onOpenUploadModal: () => void;
}

export const DocumentsPage: React.FC<Props> = ({
  documents,
  onOpenProcess,
  onOpenUploadModal,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const filteredDocs = documents.filter((doc) => {
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchName = doc.name.toLowerCase().includes(q);
      const matchSnippet = doc.fileSnippet?.toLowerCase().includes(q) || false;
      const matchProcess = doc.linkedProcessName?.toLowerCase().includes(q) || false;
      if (!matchName && !matchSnippet && !matchProcess) return false;
    }
    if (typeFilter !== 'ALL' && doc.type.toUpperCase() !== typeFilter.toUpperCase()) {
      return false;
    }
    if (statusFilter !== 'ALL' && doc.status.toUpperCase() !== statusFilter.toUpperCase()) {
      return false;
    }
    return true;
  });

  return (
    <div className="documents-container">
      {/* Header */}
      <div className="dashboard-header-row">
        <div>
          <h2 style={{ fontSize: 26, fontWeight: 700, marginBottom: 4 }}>My Ingested Documents</h2>
          <p style={{ color: 'var(--muted)', fontSize: 14 }}>
            All business policies, SOPs, and manuals processed by the P.I.E. ecosystem.
          </p>
        </div>

        <button type="button" className="yellow-button" onClick={onOpenUploadModal}>
          + UPLOAD NEW DOCUMENT <span>↗</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="documents-filter-bar">
        <div className="documents-search-input">
          <span>🔍</span>
          <input
            type="text"
            placeholder="Filter documents by name or content..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div className="documents-filter-group">
          <select
            className="filter-select"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
          >
            <option value="ALL">All Formats (PDF, DOCX, TXT)</option>
            <option value="PDF">PDF Documents</option>
            <option value="DOCX">Word Documents (DOCX)</option>
            <option value="TXT">Text Files (TXT)</option>
          </select>

          <select
            className="filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">All Statuses</option>
            <option value="COMPLETED">Completed</option>
            <option value="PROCESSING">Processing</option>
          </select>
        </div>
      </div>

      {/* Documents Table */}
      <div className="panel-card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="processes-table">
          <thead>
            <tr>
              <th>Document Name</th>
              <th>Type & Size</th>
              <th>Ingested</th>
              <th>Status</th>
              <th>Extracted Entities</th>
              <th>Linked Process</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredDocs.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', padding: '36px', color: 'var(--muted)' }}>
                  No documents found matching filters.
                </td>
              </tr>
            ) : (
              filteredDocs.map((doc) => (
                <tr key={doc.id}>
                  <td className="process-name-cell">
                    <strong>{doc.name}</strong>
                    {doc.fileSnippet && (
                      <span style={{ fontSize: 12, color: 'var(--muted)', display: 'block', maxWidth: 400 }}>
                        {doc.fileSnippet}
                      </span>
                    )}
                  </td>
                  <td>
                    <span className="type-pill">{doc.type}</span>
                    <span style={{ fontSize: 12, color: 'var(--muted)', marginLeft: 8 }}>{doc.size}</span>
                  </td>
                  <td style={{ color: 'var(--soft-white)', fontSize: 12 }}>{doc.uploadedAt}</td>
                  <td>
                    <span className="status-badge-table validated">{doc.status}</span>
                  </td>
                  <td>
                    <span style={{ fontFamily: 'var(--mono)', color: 'var(--aqua)', fontWeight: 600 }}>
                      {doc.extractedEntitiesCount} entities
                    </span>
                  </td>
                  <td>
                    {doc.linkedProcessName ? (
                      <span style={{ color: 'var(--white)', fontWeight: 500 }}>
                        {doc.linkedProcessName}
                      </span>
                    ) : (
                      <span style={{ color: 'var(--muted)' }}>—</span>
                    )}
                  </td>
                  <td>
                    {doc.linkedProcessId ? (
                      <button
                        type="button"
                        className="btn-ghost"
                        style={{ padding: '6px 12px', fontSize: 11 }}
                        onClick={() => onOpenProcess(doc.linkedProcessId!)}
                      >
                        Open Process ↗
                      </button>
                    ) : (
                      <span style={{ color: 'var(--muted)', fontSize: 12 }}>Ready</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
