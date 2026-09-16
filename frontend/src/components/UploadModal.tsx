import React, { useState } from 'react';
import { processService } from '../services/processService';
import type { ProcessEntity } from '../services/types';
import { AgentExecutionTracker, type TrackerState } from './AgentExecutionTracker';
import './UploadModal.css';

interface Props {
  onClose: () => void;
  onProcessCreated: (process: ProcessEntity) => void;
}

export const UploadModal: React.FC<Props> = ({ onClose, onProcessCreated }) => {
  const [tab, setTab] = useState<'upload' | 'text'>('text');
  const [files, setFiles] = useState<File[]>([]);
  const [rawText, setRawText] = useState(
    `Customer places an order.\nSales reviews the order.\nIf approved, Inventory checks stock.\nIf in stock, Logistics ships the product.\nIf rejected or out of stock, Customer is notified.`
  );
  const [processTitle, setProcessTitle] = useState('Order Fulfillment Process');
  const [isProcessing, setIsProcessing] = useState(false);
  const [trackerState, setTrackerState] = useState<TrackerState>({
    currentStage: 'INPUT_RECEIVED',
    completedStages: [],
    failedStage: null,
    statusMessage: 'Ready to process',
    progressPercentage: 0
  });

  const updateState = (updates: Partial<TrackerState>) => {
    setTrackerState(prev => ({ ...prev, ...updates }));
  };

  const handleStartIngestion = async () => {
    setIsProcessing(true);
    updateState({
      currentStage: 'INPUT_RECEIVED',
      statusMessage: 'Reading input and validating format...',
      progressPercentage: 10
    });

    try {
      await new Promise((r) => setTimeout(r, 600));
      updateState({
        currentStage: 'KNOWLEDGE_EXTRACTION',
        completedStages: ['INPUT_RECEIVED'],
        statusMessage: 'Knowledge Extraction Agent is extracting activities, actors, decisions, and events.',
        progressPercentage: 30
      });

      let knowledge;
      let sourceName = 'Direct_Text_Input.txt';
      let sourceType: 'PDF' | 'DOCX' | 'TXT' | 'FreeText' = 'FreeText';

      if (tab === 'upload' && files.length > 0) {
        sourceName = files[0].name;
        sourceType = files[0].name.endsWith('.pdf')
          ? 'PDF'
          : files[0].name.endsWith('.docx')
          ? 'DOCX'
          : 'TXT';
        knowledge = await processService.extractKnowledgeFromFiles(files);
      } else {
        knowledge = await processService.extractKnowledgeFromText(rawText);
      }

      await new Promise((r) => setTimeout(r, 800));
      updateState({
        currentStage: 'PROCESS_INTELLIGENCE',
        completedStages: ['INPUT_RECEIVED', 'KNOWLEDGE_EXTRACTION'],
        statusMessage: 'Process Intelligence Agent is checking for missing events, unclear gateways, and ownership gaps.',
        progressPercentage: 50
      });
      
      await new Promise((r) => setTimeout(r, 800));
      updateState({
        currentStage: 'BPMN_MODELLING',
        completedStages: ['INPUT_RECEIVED', 'KNOWLEDGE_EXTRACTION', 'PROCESS_INTELLIGENCE'],
        statusMessage: 'BPMN Modelling Agent is generating BPMN 2.0 XML.',
        progressPercentage: 70
      });

      const createdProcess = await processService.createProcessFromIngestion(
        processTitle || 'New Extracted Process',
        sourceName,
        sourceType,
        knowledge,
        rawText
      );

      await new Promise((r) => setTimeout(r, 800));
      updateState({
        currentStage: 'PROCESS_REVIEW',
        completedStages: ['INPUT_RECEIVED', 'KNOWLEDGE_EXTRACTION', 'PROCESS_INTELLIGENCE', 'BPMN_MODELLING'],
        statusMessage: 'Process Review Agent is preparing business summary and improvement suggestions.',
        progressPercentage: 90
      });

      await new Promise((r) => setTimeout(r, 600));
      updateState({
        currentStage: 'FINAL_OUTPUT',
        completedStages: ['INPUT_RECEIVED', 'KNOWLEDGE_EXTRACTION', 'PROCESS_INTELLIGENCE', 'BPMN_MODELLING', 'PROCESS_REVIEW', 'FINAL_OUTPUT'],
        statusMessage: 'Pipeline complete. Preparing UI.',
        progressPercentage: 100
      });

      setTimeout(() => {
        onProcessCreated(createdProcess);
        onClose();
      }, 800);
    } catch (err: any) {
      console.error('Ingestion failed', err);
      updateState({
        failedStage: trackerState.currentStage,
        statusMessage: err.message || 'Execution failed due to an unexpected error.',
        progressPercentage: 0
      });
    }
  };

  const handleRetry = () => {
    setTrackerState({
      currentStage: 'INPUT_RECEIVED',
      completedStages: [],
      failedStage: null,
      statusMessage: 'Ready to process',
      progressPercentage: 0
    });
    handleStartIngestion();
  };

  return (
    <div className={isProcessing ? "tracker-footer-overlay" : "modal-overlay"} onClick={!isProcessing ? onClose : undefined}>
      <div className={isProcessing ? "tracker-footer-card" : "upload-modal-card"} onClick={(e) => e.stopPropagation()}>
        {!isProcessing && (
          <div className="history-modal-header">
            <div>
              <h3>Autonomous Process Ingestion</h3>
              <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                Transform documents or text into a Canonical Process Graph & BPMN 2.0
              </span>
            </div>
            <button type="button" className="btn-ghost" onClick={onClose}>
              ✕
            </button>
          </div>
        )}

        {isProcessing ? (
          <AgentExecutionTracker state={trackerState} onRetry={handleRetry} />
        ) : (
          <>
            {/* Process Title Input */}
            <div className="login-field">
              <label>Process Name</label>
              <input
                type="text"
                className="login-input"
                value={processTitle}
                onChange={(e) => setProcessTitle(e.target.value)}
                placeholder="e.g. Order Fulfillment Workflow"
              />
            </div>

            {/* Ingestion Mode Switcher */}
            <div className="upload-tabs">
              <button
                type="button"
                className={`upload-tab-btn ${tab === 'text' ? 'active' : ''}`}
                onClick={() => setTab('text')}
              >
                ✍️ Raw Text Input
              </button>
              <button
                type="button"
                className={`upload-tab-btn ${tab === 'upload' ? 'active' : ''}`}
                onClick={() => setTab('upload')}
              >
                📄 Upload Documents (PDF / DOCX / TXT)
              </button>
            </div>

            {tab === 'text' ? (
              <div className="login-field">
                <label>Process Description or Policy Clauses</label>
                <textarea
                  className="login-input"
                  style={{ height: 160, resize: 'vertical', lineHeight: 1.5 }}
                  value={rawText}
                  onChange={(e) => setRawText(e.target.value)}
                  placeholder="Type or paste process instructions..."
                />
              </div>
            ) : (
              <div>
                <label className="dropzone-area" style={{ display: 'block' }}>
                  <input
                    type="file"
                    multiple
                    accept=".pdf,.docx,.txt"
                    style={{ display: 'none' }}
                    onChange={(e) => {
                      if (e.target.files) {
                        setFiles(Array.from(e.target.files));
                      }
                    }}
                  />
                  <span style={{ fontSize: 32, display: 'block', marginBottom: 8 }}>📁</span>
                  <strong style={{ color: 'var(--white)', display: 'block', marginBottom: 4 }}>
                    Drop files here or click to browse
                  </strong>
                  <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                    Supports PDF SOPs, Word documents (DOCX), and plain text
                  </span>
                </label>

                {files.length > 0 && (
                  <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {files.map((f, i) => (
                      <span key={i} className="type-pill" style={{ color: 'var(--aqua)' }}>
                        ✓ {f.name} ({(f.size / 1024).toFixed(1)} KB)
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 8 }}>
              <button type="button" className="btn-ghost" onClick={onClose}>
                Cancel
              </button>
              <button type="button" className="yellow-button" onClick={handleStartIngestion}>
                START INGESTION PIPELINE <span>↗</span>
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
