import React, { useState } from 'react';
import type { ProcessKnowledgeDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import './ProcessKnowledgeReview.css';

interface Props {
  data: ProcessKnowledgeDTO;
  onProceedToBpmn?: () => void;
  onReset: () => void;
}

type KnowledgeKey = keyof ProcessKnowledgeDTO & string;

interface SectionConfig {
  key: KnowledgeKey;
  title: string;
  icon: string;
  description: string;
  critical?: boolean;
}

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
  { key: 'risks', title: 'Risks & Bottlenecks', icon: '⚠️', description: 'Known failure points or delays' },
  { key: 'conflicts', title: 'Cross-Doc Discrepancies', icon: '🚨', description: 'Contradictions between uploaded files', critical: true },
];

export const ProcessKnowledgeReview: React.FC<Props> = ({
  data,
  onProceedToBpmn,
  onReset,
}) => {
  const [collapsedSections, setCollapsedSections] = useState<Record<string, boolean>>({});

  const toggleSection = (key: string) => {
    setCollapsedSections((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const toggleAll = (collapse: boolean) => {
    const nextState: Record<string, boolean> = {};
    SECTIONS.forEach((s) => {
      nextState[s.key] = collapse;
    });
    setCollapsedSections(nextState);
  };

  const totalEntities = SECTIONS.reduce((acc, curr) => {
    const list = data[curr.key];
    return acc + (Array.isArray(list) ? list.length : 0);
  }, 0);

  const conflictCount = data.conflicts?.length || 0;

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
          <button type="button" className="btn-ghost" onClick={() => toggleAll(false)}>
            Expand All
          </button>
          <button type="button" className="btn-ghost" onClick={() => toggleAll(true)}>
            Collapse All
          </button>
          <button type="button" className="btn-ghost" onClick={onReset}>
            ↺ New Upload
          </button>
          <button
            type="button"
            className="yellow-button"
            onClick={onProceedToBpmn || (() => alert('Proceeding to Agent 03: BPMN Modelling...'))}
          >
            PROCEED TO BPMN <span>↗</span>
          </button>
        </div>
      </div>

      {/* Conflict Alert Banner (if conflicts exist) */}
      {conflictCount > 0 && (
        <div className="conflict-banner">
          <span className="conflict-icon">🚨</span>
          <div className="conflict-details">
            <strong>{conflictCount} Cross-Document Conflict{conflictCount > 1 ? 's' : ''} Detected</strong>
            <span>Contradictions found between versions have been captured below for review.</span>
          </div>
        </div>
      )}

      {/* Grid of Entity Cards */}
      <div className="review-grid">
        {SECTIONS.map((section) => {
          const items = (data[section.key] as string[]) || [];
          const isCollapsed = !collapsedSections[section.key];
          const isCritical = section.critical && items.length > 0;

          return (
            <div
              key={section.key}
              className={`review-card ${isCritical ? 'critical-card' : ''}`}
            >
              <div
                className="review-card-header"
                onClick={() => toggleSection(section.key)}
              >
                <div className="card-header-left">
                  <span className="card-icon">{section.icon}</span>
                  <div>
                    <h4>{section.title}</h4>
                    <span className="card-desc">{section.description}</span>
                  </div>
                </div>

                <div className="card-header-right">
                  <span className={`badge ${items.length === 0 ? 'badge-zero' : ''}`}>
                    {items.length}
                  </span>
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
                        <li key={idx} className={isCritical ? 'conflict-text' : ''}>
                          {item}
                        </li>
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