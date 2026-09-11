import React, { useState } from 'react';
import { processService } from '../services/processService';
import type { ProcessEntity } from '../services/types';
import { CORPUS_SAMPLES } from '../utils/bpmnCorpusSamples';
import { DESCRIPTION_BANK } from '../utils/descriptionBankSamples';
import './UploadModal.css';

interface Props {
  onClose: () => void;
  onProcessCreated: (process: ProcessEntity) => void;
}

export const UploadModal: React.FC<Props> = ({ onClose, onProcessCreated }) => {
  const [tab, setTab] = useState<'text' | 'bpmn' | 'upload'>('bpmn');
  const [files, setFiles] = useState<File[]>([]);
  const [selectedCorpusId, setSelectedCorpusId] = useState<string>('F01');
  const [bpmnXmlInput, setBpmnXmlInput] = useState<string>(CORPUS_SAMPLES[0]?.xml || '');
  const [selectedDescId, setSelectedDescId] = useState<string>('D01');
  const [rawText, setRawText] = useState<string>(DESCRIPTION_BANK[0]?.description || '');
  const [processTitle, setProcessTitle] = useState<string>('Purchase Requisition Approval');
  const [isProcessing, setIsProcessing] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);

  const handleSelectCorpusSample = (corpusId: string) => {
    setSelectedCorpusId(corpusId);
    const sample = CORPUS_SAMPLES.find((s) => s.id === corpusId);
    if (sample) {
      setBpmnXmlInput(sample.xml);
      setProcessTitle(sample.name.replace(/\.bpmn$/i, '').replace(/^[A-Z0-9]+_/, '').replace(/[-_]/g, ' '));
    }
  };

  const handleSelectDescSample = (descId: string) => {
    setSelectedDescId(descId);
    const sample = DESCRIPTION_BANK.find((d) => d.id === descId);
    if (sample) {
      setRawText(sample.description);
      setProcessTitle(sample.title);
    }
  };

  const handleBpmnFileUpload = async (uploadedFiles: FileList | null) => {
    if (!uploadedFiles || uploadedFiles.length === 0) return;
    const file = uploadedFiles[0];
    const text = await file.text();
    setBpmnXmlInput(text);
    setProcessTitle(file.name.replace(/\.bpmn$/i, '').replace(/[-_]/g, ' '));
    setSelectedCorpusId('');
  };

  const handleStartIngestion = async () => {
    setIsProcessing(true);
    setCurrentStep(1); // Ingestion & Parsing

    try {
      await new Promise((r) => setTimeout(r, 500));
      setCurrentStep(2); // Semantic Graph Extraction

      let createdProcess: ProcessEntity;

      if (tab === 'bpmn') {
        const sample = CORPUS_SAMPLES.find((s) => s.id === selectedCorpusId);
        const fileName = sample ? sample.name : `${processTitle || 'Model'}.bpmn`;

        await new Promise((r) => setTimeout(r, 500));
        setCurrentStep(3); // Business Narrative Generation (Capability 2)

        await new Promise((r) => setTimeout(r, 400));
        setCurrentStep(4); // Quality & Defect Report (Capability 3)

        createdProcess = await processService.importBpmnXml(bpmnXmlInput, fileName);
      } else if (tab === 'upload' && files.length > 0) {
        const sourceName = files[0].name;
        const sourceType = files[0].name.endsWith('.pdf')
          ? 'PDF'
          : files[0].name.endsWith('.docx')
          ? 'DOCX'
          : files[0].name.endsWith('.bpmn')
          ? 'BPMN'
          : 'TXT';

        if (sourceType === 'BPMN') {
          createdProcess = await processService.importBpmnFile(files[0]);
        } else {
          const knowledge = await processService.extractKnowledgeFromFiles(files);
          await new Promise((r) => setTimeout(r, 500));
          setCurrentStep(3);
          await new Promise((r) => setTimeout(r, 400));
          setCurrentStep(4);
          createdProcess = await processService.createProcessFromIngestion(
            processTitle || 'New Extracted Process',
            sourceName,
            sourceType,
            knowledge,
            files[0].name
          );
        }
      } else {
        // Raw Text Tab
        const knowledge = await processService.extractKnowledgeFromText(rawText);
        await new Promise((r) => setTimeout(r, 500));
        setCurrentStep(3);
        await new Promise((r) => setTimeout(r, 400));
        setCurrentStep(4);
        createdProcess = await processService.createProcessFromIngestion(
          processTitle || 'New Extracted Process',
          'Description_Bank_Prompt.txt',
          'FreeText',
          knowledge,
          rawText
        );
      }

      await new Promise((r) => setTimeout(r, 300));
      setCurrentStep(5); // Complete

      setTimeout(() => {
        onProcessCreated(createdProcess);
        onClose();
      }, 500);
    } catch (err) {
      console.error('Ingestion failed', err);
      alert('Ingestion error. Check connection or BPMN XML format.');
      setIsProcessing(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="upload-modal-card" style={{ maxWidth: 780 }} onClick={(e) => e.stopPropagation()}>
        <div className="history-modal-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 22 }}>🤖</span>
              <h3 style={{ margin: 0 }}>ProcessIQ Copilot Ingestion Pipeline</h3>
            </div>
            <span style={{ fontSize: 12, color: 'var(--muted)', marginTop: 4, display: 'block' }}>
              Text-to-BPMN Generation • BPMN-to-Text Narrative • Structural Quality & Defect Report
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
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>
                  {tab === 'bpmn' ? 'BPMN 2.0 XML Ingestion' : 'Document Ingestion & NLP Parsing'}
                </strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  {tab === 'bpmn' ? 'Parsing flow elements, sequence edges, and gateway branches...' : 'Parsing raw instructions and entity boundaries...'}
                </span>
              </div>
            </div>

            <div className={`stepper-item ${currentStep === 2 ? 'active' : currentStep > 2 ? 'done' : ''}`}>
              <div className={`step-indicator ${currentStep === 2 ? 'active' : currentStep > 2 ? 'done' : 'waiting'}`}>
                {currentStep > 2 ? '✓' : '2'}
              </div>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>
                  Canonical Graph & Role Mapping
                </strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  Constructing directed topological nodes, sequence flows, and swimlanes...
                </span>
              </div>
            </div>

            <div className={`stepper-item ${currentStep === 3 ? 'active' : currentStep > 3 ? 'done' : ''}`}>
              <div className={`step-indicator ${currentStep === 3 ? 'active' : currentStep > 3 ? 'done' : 'waiting'}`}>
                {currentStep > 3 ? '✓' : '3'}
              </div>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>
                  Plain-Language Business Narrative (Capability 2)
                </strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  Synthesizing business walkthrough for non-expert process stakeholders...
                </span>
              </div>
            </div>

            <div className={`stepper-item ${currentStep === 4 ? 'active' : currentStep > 4 ? 'done' : ''}`}>
              <div className={`step-indicator ${currentStep === 4 ? 'active' : currentStep > 4 ? 'done' : 'waiting'}`}>
                {currentStep > 4 ? '✓' : '4'}
              </div>
              <div>
                <strong style={{ color: 'var(--white)', fontSize: 14 }}>
                  Structural Quality & Defect Report (Capability 3)
                </strong>
                <span style={{ display: 'block', fontSize: 12, color: 'var(--muted)' }}>
                  Inspecting start/end events, gateway splits, joins, and actionability...
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
                placeholder="e.g. Purchase Requisition Approval"
              />
            </div>

            {/* Ingestion Mode Switcher */}
            <div className="upload-tabs">
              <button
                type="button"
                className={`upload-tab-btn ${tab === 'bpmn' ? 'active' : ''}`}
                onClick={() => setTab('bpmn')}
              >
                📐 BPMN 2.0 XML (Corpus Files)
              </button>
              <button
                type="button"
                className={`upload-tab-btn ${tab === 'text' ? 'active' : ''}`}
                onClick={() => setTab('text')}
              >
                ✍️ Text-to-BPMN (Description Bank)
              </button>
              <button
                type="button"
                className={`upload-tab-btn ${tab === 'upload' ? 'active' : ''}`}
                onClick={() => setTab('upload')}
              >
                📄 Upload Documents (PDF/DOCX/BPMN)
              </button>
            </div>

            {/* TAB 1: BPMN 2.0 XML & Corpus Samples */}
            {tab === 'bpmn' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div>
                  <span style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 6 }}>
                    Select a Hackathon Benchmark Corpus File to Test:
                  </span>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {CORPUS_SAMPLES.map((sample) => {
                      const isClean = sample.name.includes('clean');
                      const isSelected = selectedCorpusId === sample.id;
                      return (
                        <button
                          key={sample.id}
                          type="button"
                          onClick={() => handleSelectCorpusSample(sample.id)}
                          style={{
                            padding: '5px 10px',
                            borderRadius: 6,
                            fontSize: 11,
                            cursor: 'pointer',
                            fontWeight: 600,
                            border: isSelected ? '1px solid var(--aqua)' : '1px solid rgba(116, 183, 220, 0.2)',
                            background: isSelected
                              ? 'rgba(57, 245, 208, 0.2)'
                              : isClean
                              ? 'rgba(57, 245, 208, 0.05)'
                              : 'rgba(255, 107, 107, 0.08)',
                            color: isSelected ? 'var(--aqua)' : isClean ? 'var(--aqua)' : '#ff8787',
                          }}
                        >
                          {sample.id}: {sample.name.replace(/^[A-Z0-9]+_/, '').replace(/\.bpmn$/i, '').replace(/_/g, ' ')}
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                  <label
                    style={{
                      padding: '8px 14px',
                      borderRadius: 8,
                      background: 'rgba(57, 245, 208, 0.1)',
                      border: '1px dashed var(--aqua)',
                      color: 'var(--aqua)',
                      fontSize: 12,
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                    }}
                  >
                    <span>📁</span> Choose Custom .bpmn File
                    <input
                      type="file"
                      accept=".bpmn,.xml"
                      style={{ display: 'none' }}
                      onChange={(e) => handleBpmnFileUpload(e.target.files)}
                    />
                  </label>
                  <span style={{ fontSize: 11, color: 'var(--muted)' }}>
                    {selectedCorpusId ? `Active Preset: ${selectedCorpusId}` : 'Custom file loaded'}
                  </span>
                </div>

                <div className="login-field">
                  <label>BPMN 2.0 XML Content</label>
                  <textarea
                    className="login-input"
                    style={{ height: 120, resize: 'vertical', fontFamily: 'var(--mono)', fontSize: 11, lineHeight: 1.4 }}
                    value={bpmnXmlInput}
                    onChange={(e) => {
                      setBpmnXmlInput(e.target.value);
                      setSelectedCorpusId('');
                    }}
                    placeholder="<bpmn:definitions ...> ... </bpmn:definitions>"
                  />
                </div>
              </div>
            )}

            {/* TAB 2: Text-to-BPMN (Description Bank) */}
            {tab === 'text' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div>
                  <span style={{ fontSize: 12, color: 'var(--muted)', display: 'block', marginBottom: 6 }}>
                    Select an official Description Bank prompt to test Text-to-BPMN:
                  </span>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {DESCRIPTION_BANK.map((desc) => {
                      const isSelected = selectedDescId === desc.id;
                      return (
                        <button
                          key={desc.id}
                          type="button"
                          onClick={() => handleSelectDescSample(desc.id)}
                          style={{
                            padding: '5px 10px',
                            borderRadius: 6,
                            fontSize: 11,
                            cursor: 'pointer',
                            fontWeight: 600,
                            border: isSelected ? '1px solid var(--aqua)' : '1px solid rgba(116, 183, 220, 0.2)',
                            background: isSelected ? 'rgba(57, 245, 208, 0.2)' : 'rgba(4, 18, 45, 0.6)',
                            color: isSelected ? 'var(--aqua)' : 'var(--soft-white)',
                          }}
                        >
                          {desc.id}: {desc.title} ({desc.clarity})
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="login-field">
                  <label>Plain-Language Process Description</label>
                  <textarea
                    className="login-input"
                    style={{ height: 130, resize: 'vertical', lineHeight: 1.5 }}
                    value={rawText}
                    onChange={(e) => {
                      setRawText(e.target.value);
                      setSelectedDescId('');
                    }}
                    placeholder="Describe your process in free text..."
                  />
                </div>
              </div>
            )}

            {/* TAB 3: Document Upload */}
            {tab === 'upload' && (
              <div>
                <label className="dropzone-area" style={{ display: 'block' }}>
                  <input
                    type="file"
                    multiple
                    accept=".pdf,.docx,.txt,.bpmn"
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
                    Supports BPMN 2.0 XML (.bpmn), PDF SOPs, Word documents (.docx), and plain text
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

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 12 }}>
              <button type="button" className="btn-ghost" onClick={onClose}>
                Cancel
              </button>
              <button type="button" className="yellow-button" onClick={handleStartIngestion}>
                RUN PIPELINE <span>↗</span>
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
