import React, { useRef, useState } from 'react';
import type {
  CanonicalProcessGraph,
  ProcessKnowledgeDTO,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { processService } from '../services/processService';
import { CORPUS_SAMPLES, type CorpusSample } from '../utils/bpmnCorpusSamples';
import { DESCRIPTION_BANK } from '../utils/descriptionBankSamples';
import { parseBpmnXmlClient } from '../utils/bpmnXmlParser';
import { generateProcessNarrative, type ProcessNarrative } from '../services/bpmnNarrativeGenerator';
import './ProcessEntry.css';

const MAX_FILE_SIZE_MB = 10;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

type ActiveTab = 'file' | 'text';

interface Props {
  onStart: (fileCount: number) => void;
  onSuccess: (
    data: ProcessKnowledgeDTO,
    bpmnXml?: string,
    graph?: CanonicalProcessGraph,
    narrative?: ProcessNarrative,
    directTab?: '01_KNOWLEDGE' | '02_INTELLIGENCE' | '03_BPMN' | '04_REVIEW'
  ) => void;
  onError?: (err: string) => void;
}

export default function ProcessEntry({ onStart, onSuccess, onError }: Props) {
  const [activeTab, setActiveTab] = useState<ActiveTab>('file');
  const [files, setFiles] = useState<File[]>([]);
  const [text, setText] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const isAllowedFile = (file: File): boolean => {
    const name = file.name.toLowerCase();
    const allowedExtensions = ['.pdf', '.docx', '.txt', '.xlsx', '.bpmn', '.xml'];
    if (allowedExtensions.some((ext) => name.endsWith(ext))) {
      return true;
    }
    const allowedTypes = [
      'application/pdf',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'text/plain',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/xml',
      'text/xml',
      'application/bpmn+xml',
      'application/octet-stream',
    ];
    return allowedTypes.includes(file.type);
  };

  const validateFiles = (newFiles: FileList | File[]): File[] => {
    setError(null);
    const validFiles: File[] = [];
    const currentFiles = files;

    Array.from(newFiles).forEach((file: File) => {
      if (!isAllowedFile(file)) {
        setError(
          `Skipped ${file.name}: Invalid file type. Supported types: .bpmn, .xml, .pdf, .docx, .txt, .xlsx.`
        );
        return;
      }

      if (file.size > MAX_FILE_SIZE_BYTES) {
        setError(`Skipped ${file.name}: Exceeds ${MAX_FILE_SIZE_MB}MB limit.`);
        return;
      }

      const alreadyExists =
        currentFiles.some(
          (existingFile) => existingFile.name === file.name && existingFile.size === file.size
        ) ||
        validFiles.some(
          (existingFile) => existingFile.name === file.name && existingFile.size === file.size
        );

      if (!alreadyExists) {
        validFiles.push(file);
      }
    });

    return validFiles;
  };

  const handleFileDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const valid = validateFiles(e.dataTransfer.files);
      if (valid.length > 0) {
        setFiles((prev) => [...prev, ...valid]);
      }
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const valid = validateFiles(e.target.files);
      if (valid.length > 0) {
        setFiles((prev) => [...prev, ...valid]);
      }
    }
    e.target.value = '';
  };

  const removeFile = (indexToRemove: number) => {
    setFiles((prev) => prev.filter((_, index) => index !== indexToRemove));
  };

  // One-click loader for Hackathon benchmark corpus (F01–F08)
  const handleLoadSample = async (sample: CorpusSample) => {
    setError(null);
    onStart(1);
    try {
      const process = await processService.importBpmnXml(sample.xml, sample.name);
      onSuccess(
        process.knowledge,
        process.bpmnXml,
        process.graph,
        process.narrative,
        '04_REVIEW'
      );
    } catch (err) {
      console.warn('Sample load fallback to client parser:', err);
      const clientRes = parseBpmnXmlClient(sample.xml, sample.name);
      const narrative = generateProcessNarrative(clientRes.graph, clientRes.processName);
      onSuccess(
        clientRes.knowledge,
        clientRes.xml,
        clientRes.graph,
        narrative,
        '04_REVIEW'
      );
    }
  };

  const handleSubmit = async () => {
    setError(null);

    if (activeTab === 'file' && files.length === 0) {
      setError('Please select or drop at least one file.');
      return;
    }

    if (activeTab === 'text' && text.trim().length < 10) {
      setError('Please enter a more detailed process description.');
      return;
    }

    onStart(activeTab === 'file' ? files.length : 1);

    try {
      if (activeTab === 'file') {
        // Check if any uploaded file is a BPMN 2.0 XML file
        const bpmnFile = files.find((f) => {
          const n = f.name.toLowerCase();
          return n.endsWith('.bpmn') || n.endsWith('.xml');
        });

        if (bpmnFile) {
          try {
            const process = await processService.importBpmnFile(bpmnFile);
            onSuccess(
              process.knowledge,
              process.bpmnXml,
              process.graph,
              process.narrative,
              '04_REVIEW'
            );
            return;
          } catch (bpmnErr) {
            console.warn('BPMN Service Ingestion fallback to client parser:', bpmnErr);
            const xmlText = await bpmnFile.text();
            const clientRes = parseBpmnXmlClient(xmlText, bpmnFile.name);
            const narrative = generateProcessNarrative(clientRes.graph, clientRes.processName);
            onSuccess(
              clientRes.knowledge,
              clientRes.xml,
              clientRes.graph,
              narrative,
              '04_REVIEW'
            );
            return;
          }
        }

        // Otherwise handle document upload (PDF, DOCX, TXT, XLSX)
        const formData = new FormData();
        files.forEach((file) => {
          formData.append('files', file);
        });

        const response = await fetch('http://localhost:8080/api/v1/process/extract-file', {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) {
          throw new Error(`Server returned ${response.status}: Failed to extract knowledge.`);
        }

        const processIntelligence: ProcessKnowledgeDTO = await response.json();
        onSuccess(processIntelligence, undefined, undefined, undefined, '01_KNOWLEDGE');
      } else {
        // Raw text input
        const response = await fetch('http://localhost:8080/api/v1/process/extract-text', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            content: text,
          }),
        });

        if (!response.ok) {
          throw new Error(`Server returned ${response.status}: Failed to extract knowledge.`);
        }

        const processIntelligence: ProcessKnowledgeDTO = await response.json();
        onSuccess(processIntelligence, undefined, undefined, undefined, '01_KNOWLEDGE');
      }
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'An unknown connection error occurred.';
      setError(errMsg);
      if (onError) onError(errMsg);
    }
  };

  return (
    <div className="process-entry">
      <div className="entry-tabs">
        <button
          type="button"
          className={`entry-tab ${activeTab === 'file' ? 'active' : ''}`}
          onClick={() => setActiveTab('file')}
        >
          DOCUMENT & BPMN UPLOAD
        </button>

        <button
          type="button"
          className={`entry-tab ${activeTab === 'text' ? 'active' : ''}`}
          onClick={() => setActiveTab('text')}
        >
          RAW TEXT INPUT
        </button>
      </div>

      <div className="entry-content">
        {activeTab === 'file' ? (
          <>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept=".pdf,.docx,.txt,.xlsx,.bpmn,.xml"
              onChange={handleFileSelect}
              style={{ display: 'none' }}
            />

            <div
              className={`drop-zone ${isDragging ? 'dragging' : ''} ${
                files.length > 0 ? 'has-file' : ''
              }`}
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={(e) => {
                e.preventDefault();
                setIsDragging(false);
              }}
              onDrop={handleFileDrop}
              onClick={() => fileInputRef.current?.click()}
            >
              {files.length > 0 ? (
                <div
                  className="file-list-container"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="file-list-header">
                    <div>
                      <strong>
                        {files.length} Document{files.length !== 1 ? 's' : ''} Ready
                      </strong>
                    </div>

                    <button
                      type="button"
                      className="add-more-button"
                      onClick={() => fileInputRef.current?.click()}
                    >
                      + Add More
                    </button>
                  </div>

                  <div className="file-list">
                    {files.map((file, idx) => (
                      <div className="file-item" key={`${file.name}-${idx}`}>
                        <div className="file-info">
                          <span className="file-icon">
                            {file.name.toLowerCase().endsWith('.bpmn') ? '⌘' : '📄'}
                          </span>
                          <div className="file-details">
                            <span className="file-name">{file.name}</span>
                            <span className="file-size">
                              {(file.size / 1024 / 1024).toFixed(2)} MB
                            </span>
                          </div>
                        </div>

                        <button
                          type="button"
                          className="remove-file-button"
                          onClick={() => removeFile(idx)}
                          aria-label={`Remove ${file.name}`}
                        >
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="empty-drop-zone">
                  <div className="upload-icon">+</div>
                  <div className="upload-title">Feed P.I.E. documents or BPMN 2.0 files.</div>
                  <div className="upload-description">
                    BPMN / XML / PDF / DOCX / TXT / XLSX · Drop them here or click to browse
                  </div>
                </div>
              )}
            </div>

            {/* Benchmark Presets Toolbar right on landing hero */}
            <div className="preset-corpus-strip">
              <span className="preset-strip-label">⚡ QUICK BENCHMARK CORPUS:</span>
              <div className="preset-chips">
                {CORPUS_SAMPLES.slice(0, 6).map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    className="preset-chip-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleLoadSample(s);
                    }}
                    title={`Load benchmark ${s.name}`}
                  >
                    <span className="preset-chip-id">{s.id}</span>
                    <span>
                      {s.name
                        .replace(/^[A-Z0-9]+_/, '')
                        .replace(/\.bpmn$/i, '')
                        .replace(/[-_]/g, ' ')}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          </>
        ) : (
          <div className="text-input-container">
            <textarea
              className="process-textarea"
              value={text}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setText(e.target.value)}
              placeholder="Describe the process you want P.I.E. to analyze..."
              rows={10}
            />

            {/* Quick Example Descriptions Strip */}
            <div className="preset-corpus-strip" style={{ marginTop: '14px' }}>
              <span className="preset-strip-label">⚡ LOAD EXAMPLE TEXT:</span>
              <div className="preset-chips">
                {DESCRIPTION_BANK.slice(0, 5).map((d) => (
                  <button
                    key={d.id}
                    type="button"
                    className="preset-chip-btn"
                    onClick={() => setText(d.description)}
                    title={d.description}
                  >
                    <span className="preset-chip-id">{d.id}</span>
                    <span>{d.title}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="entry-actions">
        <button className="yellow-button" type="button" onClick={handleSubmit}>
          INITIALIZE EXTRACTION <span>↗</span>
        </button>
      </div>
    </div>
  );
}