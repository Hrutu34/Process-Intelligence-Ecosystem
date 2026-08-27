import React, { useState } from 'react';
import { processService } from '../services/processService';
import type { ProcessEntity } from '../services/types';
import './UploadModal.css';

interface Props {
  onClose: () => void;
  onProcessCreated: (process: ProcessEntity) => void;
}

export const UploadModal: React.FC<Props> = ({ onClose, onProcessCreated }) => {
  const [tab, setTab] = useState<'upload' | 'text'>('text');
  const [files, setFiles] = useState<File[]>([]);
  const [rawText, setRawText] = useState(
    `Customer places an order.
Sales reviews the order.
If approved, Inventory checks stock.
If in stock, Logistics ships the product.
If rejected or out of stock, Customer is notified.`
  );
  const [processTitle, setProcessTitle] = useState('Order Fulfillment Process');
  const [isProcessing, setIsProcessing] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);

  const handleStartIngestion = async () => {
    setIsProcessing(true);
    setCurrentStep(1); // Document Ingestion

    try {
      await new Promise((r) => setTimeout(r, 600));
      setCurrentStep(2); // Knowledge Extraction

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

      await new Promise((r) => setTimeout(r, 600));
      setCurrentStep(3); // Canonical Process Graph Generation

      const createdProcess = await processService.createProcessFromIngestion(
        processTitle || 'New Extracted Process',
        sourceName,
        sourceType,
        knowledge,
        rawText
      );

      await new Promise((r) => setTimeout(r, 600));
      setCurrentStep(4); // BPMN 2.0 & Validation

      await new Promise((r) => setTimeout(r, 400));
      setCurrentStep(5); // Complete

      setTimeout(() => {
        onProcessCreated(createdProcess);
        onClose();
      }, 500);
    } catch (err) {
      console.error('Ingestion failed', err);
      alert('Ingestion error. Check backend connection.');
      setIsProcessing(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="upload-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="history-modal-header">
          <div>
            <h3>Autonomous Process Ingestion</h3>
            <span style={{ fontSize: 12, color: 'var(--muted)' }}>
              Transform documents or text into a Canonical Process Graph & BPMN 2.0
            </span>
          </div>
          {!isProcessing && (
            <button type="button" className="btn-ghost" onClick={onClose}>
              ✕
            </button>
          )}
        </div>

        {/* Stepper view while processing */}
        {isProcessing ? (
          <div className="stepper-container">
            <div className={`stepper-item ${currentStep === 1 ? 'active' : currentStep > 1 ? 'done' : ''}`}>
              <div className={`step-indicator ${currentStep === 1 ? 'active' : currentStep > 1 ? 'done' : 'waiting'}`}>
                {currentStep > 1 ? '✓' : '1'}
              </div>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>Document Classification & NLP</strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  Parsing text entities, clauses, and semantic boundaries...
                </span>
              </div>
            </div>

            <div className={`stepper-item ${currentStep === 2 ? 'active' : currentStep > 2 ? 'done' : ''}`}>
              <div className={`step-indicator ${currentStep === 2 ? 'active' : currentStep > 2 ? 'done' : 'waiting'}`}>
                {currentStep > 2 ? '✓' : '2'}
              </div>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>AI Knowledge Extraction</strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  Extracting activities, actors, decision gates, and business rules...
                </span>
              </div>
            </div>

            <div className={`stepper-item ${currentStep === 3 ? 'active' : currentStep > 3 ? 'done' : ''}`}>
              <div className={`step-indicator ${currentStep === 3 ? 'active' : currentStep > 3 ? 'done' : 'waiting'}`}>
                {currentStep > 3 ? '✓' : '3'}
              </div>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>Canonical Process Graph Construction</strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  Building deterministic sequence nodes, edge conditions, and role swimlanes...
                </span>
              </div>
            </div>

            <div className={`stepper-item ${currentStep === 4 ? 'active' : currentStep > 4 ? 'done' : ''}`}>
              <div className={`step-indicator ${currentStep === 4 ? 'active' : currentStep > 4 ? 'done' : 'waiting'}`}>
                {currentStep > 4 ? '✓' : '4'}
              </div>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>BPMN 2.0 & Quality Validation</strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  Synthesizing vector coordinates and quality score...
                </span>
              </div>
            </div>
          </div>
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
