import React, { useState } from 'react';
import type { ProcessKnowledgeDTO, ProcessDocumentDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import './ProcessKnowledgeReview.css';

interface Props {
  data: ProcessKnowledgeDTO;
  documents?: ProcessDocumentDTO[];
  onProceedToIntelligence?: () => void;
  onReset: () => void;
}

type KnowledgeKey = keyof ProcessKnowledgeDTO & string;

interface SectionConfig {
  key: KnowledgeKey;
  title: string;
  icon: string;
  description: string;
}

// 🚨 'conflicts' is intentionally absent here so it doesn't render in the bottom grid
const SECTIONS: SectionConfig[] = [
  { key: 'activities', title: 'Activities', icon: '⚡', description: 'Step-by-step tasks and actions' },
  { key: 'actors', title: 'Actors', icon: '👤', description: 'Departments and individuals' },
  { key: 'roles', title: 'Roles', icon: '🏷️', description: 'Functional responsibilities' },
  { key: 'gateways', title: 'Decision Points', icon: '🔀', description: 'Branching logic and validation gates' },
  { key: 'systems', title: 'Systems & Tools', icon: '💻', description: 'Databases, portals, and platforms' },
  { key: 'events', title: 'Events & Triggers', icon: '⏱️', description: 'Schedules, start triggers, and deadlines' },
  { key: 'inputs', title: 'Inputs', icon: '📥', description: 'Consumed documents and data sources' },
  { key: 'outputs', title: 'Outputs', icon: '📤', description: 'Deliverables and resulting states' },
  { key: 'businessRules', title: 'Business Rules', icon: '📜', description: 'Mandates and policy constraints' },
  { key: 'risks', title: 'Risks & Bottlenecks', icon: '⚠️', description: 'Known failure points or delays' }
];

export const ProcessKnowledgeReview: React.FC<Props> = ({
  data,
  documents = [],
  onProceedToIntelligence,
  onReset,
}) => {
  const [collapsedSections, setCollapsedSections] = useState<Record<string, boolean>>({});

  const toggleSection = (key: string) => {
    setCollapsedSections((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const toggleAll = (collapse: boolean) => {
    const nextState: Record<string, boolean> = {
      documents: collapse,
      conflicts: collapse
    };
    SECTIONS.forEach((s) => {
      nextState[s.key] = collapse;
    });
    setCollapsedSections(nextState);
  };

  const getConfColor = (conf: number) => {
    if (conf >= 85) return 'high';
    if (conf >= 60) return 'med';
    return 'low';
  };

  const totalEntities = SECTIONS.reduce((acc, curr) => {
    const list = data[curr.key];
    return acc + (Array.isArray(list) ? list.length : 0);
  }, 0);

  const conflictCount = data.conflicts?.length || 0;
  const displayDocs = documents.length > 0 ? documents : (data.documents || []);

  const isDocsCollapsed = Boolean(collapsedSections['documents']);
  const isConflictsCollapsed = Boolean(collapsedSections['conflicts']);

  const renderItemWithChip = (text: string) => {
    const match = text.match(/^(.*?)\s*\[([a-zA-Z_]+)\]$/);
    if (match) {
      const label = match[1];
      const tag = match[2].toUpperCase();
      const isGateway = ['EXCLUSIVE', 'PARALLEL', 'INCLUSIVE'].includes(tag);
      return (
        <li className="entity-li-flex">
          <span>{label}</span>
          <span className={`ai-chip ${isGateway ? 'chip-gateway' : 'chip-task'}`}>
            {tag.replace('_', ' ')}
          </span>
        </li>
      );
    }
    return <li className="entity-li-flex"><span>{text}</span></li>;
  };

  return (
    <div className="review-wrapper">
      {/* Review Control Bar */}
      <div className="review-top-bar">
        <div className="review-headline">
          <div className="status-pill">
            <span className="status-dot" />
            EXTRACTION VALIDATED
            <span className="status-separator">/</span>
            {totalEntities} ENTITIES DETECTED
          </div>
          <h3>Normalized Process Knowledge</h3>
          <p>Review the extracted blueprint before starting BPMN 2.0 generation.</p>
        </div>

        <div className="review-controls">
          <button type="button" className="btn-ghost" onClick={() => toggleAll(false)}>Expand All</button>
          <button type="button" className="btn-ghost" onClick={() => toggleAll(true)}>Collapse All</button>
          <button type="button" className="btn-ghost" onClick={onReset}>↺ New Upload</button>
          {onProceedToIntelligence && (
            <button type="button" className="yellow-button" onClick={onProceedToIntelligence}>
              PROCEED TO SEMANTICS <span>↗</span>
            </button>
          )}
        </div>
      </div>

      {/* --- UPPER SECTION: Documents & Discrepancies --- */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
        
        {/* 1. Document Classification Table (Collapsible) */}
        {displayDocs.length > 0 && (
          <div className="classification-table-container" style={{ margin: 0 }}>
            <div 
              className="classification-header" 
              onClick={() => toggleSection('documents')}
              style={{ cursor: 'pointer', display: 'flex', justifyContent: 'space-between', userSelect: 'none' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '20px' }}>📄</span>
                <h3 style={{ margin: 0 }}>Agent 01: Classified Documents</h3>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span className="badge">{displayDocs.length}</span>
                <span className="collapse-arrow" style={{ fontSize: '18px' }}>{isDocsCollapsed ? '+' : '−'}</span>
              </div>
            </div>

            {!isDocsCollapsed && (
              <table className="class-table">
                <thead>
                  <tr>
                    <th>Document File</th>
                    <th>AI Classification Category</th>
                    <th>Confidence Score</th>
                  </tr>
                </thead>
                <tbody>
                  {displayDocs.map((doc, idx) => (
                    <tr key={doc.documentId || idx}>
                      <td className="doc-name">{doc.name || `Document ${idx + 1}`}</td>
                      <td><span className="cat-badge">{doc.category || 'Process Description'}</span></td>
                      <td>
                        <div className="conf-bar-bg">
                          <div 
                            className={`conf-bar-fill ${getConfColor(doc.confidence || 90)}`} 
                            style={{ width: `${doc.confidence || 90}%` }} 
                          />
                        </div>
                        <span style={{ fontFamily: 'var(--mono)', fontSize: '11px', color: 'var(--white)' }}>
                          {doc.confidence || 90}%
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {/* 2. Cross-Document Discrepancies (Collapsible, ALWAYS VISIBLE) */}
        <div className={`review-card ${conflictCount > 0 ? 'critical-card' : ''}`} style={{ margin: 0 }}>
          <div className="review-card-header" onClick={() => toggleSection('conflicts')}>
            <div className="card-header-left">
              <span className="card-icon">{conflictCount > 0 ? '🚨' : '✅'}</span>
              <div>
                <h4 style={{ color: conflictCount > 0 ? '#ff6b81' : 'var(--aqua)', margin: 0, fontSize: '16px' }}>
                  Cross-Doc Discrepancies
                </h4>
                <span className="card-desc" style={{ color: conflictCount > 0 ? 'rgba(255,107,129,0.7)' : 'var(--muted)' }}>
                  {conflictCount > 0 
                    ? 'Contradictions between uploaded files deteted. Please review the list below.' 
                    : 'Documents are aligned. No conflicts detected.'}
                </span>
              </div>
            </div>
            <div className="card-header-right">
              <span className={`badge ${conflictCount === 0 ? 'badge-zero' : ''}`}>{conflictCount}</span>
              <span className="collapse-arrow">{isConflictsCollapsed ? '+' : '−'}</span>
            </div>
          </div>

          {!isConflictsCollapsed && (
            <div className="review-card-body">
              {conflictCount === 0 ? (
                <span className="empty-state" style={{ color: 'var(--aqua)', fontStyle: 'normal' }}>
                  ✓ Perfect alignment detected across source materials.
                </span>
              ) : (
                <ul className="entity-list">
                  {data.conflicts.map((conflict, idx) => (
                    <li key={idx} className="conflict-text">
                      {conflict}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      </div>

      {/* --- LOWER SECTION: Grid of Entity Cards --- */}
      <div className="review-grid">
        {SECTIONS.map((section) => {
          const items = (data[section.key] as string[]) || [];
          const isCollapsed = Boolean(collapsedSections[section.key]);

          return (
            <div key={section.key} className="review-card">
              <div className="review-card-header" onClick={() => toggleSection(section.key)}>
                <div className="card-header-left">
                  <span className="card-icon">{section.icon}</span>
                  <div>
                    <h4>{section.title}</h4>
                    <span className="card-desc">{section.description}</span>
                  </div>
                </div>
                <div className="card-header-right">
                  <span className={`badge ${items.length === 0 ? 'badge-zero' : ''}`}>{items.length}</span>
                  <span className="collapse-arrow">{isCollapsed ? '+' : '−'}</span>
                </div>
              </div>

              {!isCollapsed && (
                <div className="review-card-body">
                  {items.length === 0 ? (
                    <span className="empty-state">None detected in source material</span>
                  ) : (
                    <ul className="entity-list">
                      {items.map((item: string, idx: number) => (
                        <React.Fragment key={idx}>
                          {renderItemWithChip(item)}
                        </React.Fragment>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};