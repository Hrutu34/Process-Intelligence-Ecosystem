import React, { useRef, useState } from 'react';
import type { ProcessKnowledgeDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import './ProcessEntry.css';

const MAX_FILE_SIZE_MB = 10;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

const ALLOWED_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/xml',
  'text/xml',
  // Empty string covers browsers that fail to detect BPMN MIME type; extension check below handles it.
  '',
];

const BPMN_EXTENSIONS = ['.bpmn', '.bpmn20', '.bpmn20.xml', '.xml'];

function isBpmnFile(file: File): boolean {
  const name = file.name.toLowerCase();
  return BPMN_EXTENSIONS.some((ext) => name.endsWith(ext));
}

type ActiveTab = 'file' | 'text';

export interface BpmnImportPayload {
  knowledge: ProcessKnowledgeDTO;
  bpmnXml: string;
  processName?: string;
  processId?: string;
  qualityReport?: unknown;
}

interface Props {
  onStart: (fileCount: number) => void;
  onSuccess: (data: ProcessKnowledgeDTO) => void;
  onBpmnImported?: (payload: BpmnImportPayload) => void;
  onError?: (err: string) => void;
  initialTab?: ActiveTab;
}

export default function ProcessEntry({ onStart, onSuccess, onBpmnImported, onError, initialTab }: Props) {
  const [activeTab, setActiveTab] = useState<ActiveTab>(initialTab ?? 'file');
  React.useEffect(() => {
    if (initialTab) setActiveTab(initialTab);
  }, [initialTab]);
  const [files, setFiles] = useState<File[]>([]);
  const [text, setText] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const validateFiles = (newFiles: FileList | File[]): File[] => {
    setError(null);
    const validFiles: File[] = [];
    const currentFiles = files;

    Array.from(newFiles).forEach((file: File) => {
      const acceptedByType = ALLOWED_TYPES.includes(file.type);
      const acceptedByExtension = isBpmnFile(file);
      if (!acceptedByType && !acceptedByExtension) {
        setError(`Skipped ${file.name}: Invalid file type.`);
        return;
      }

      if (file.size > MAX_FILE_SIZE_BYTES) {
        setError(`Skipped ${file.name}: Exceeds ${MAX_FILE_SIZE_MB}MB limit.`);
        return;
      }

      const alreadyExists =
        currentFiles.some(
          (existingFile) =>
            existingFile.name === file.name && existingFile.size === file.size
        ) ||
        validFiles.some(
          (existingFile) =>
            existingFile.name === file.name && existingFile.size === file.size
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
      // BPMN direct-import path: if the user uploaded a single .bpmn/.xml file, parse
      // it on the backend and skip document extraction entirely.
      if (activeTab === 'file' && files.length === 1 && isBpmnFile(files[0]) && onBpmnImported) {
        const bpmnFile = files[0];
        const bpmnForm = new FormData();
        bpmnForm.append('file', bpmnFile);

        const importRes = await fetch('http://localhost:8080/api/v1/process/import-bpmn-file', {
          method: 'POST',
          body: bpmnForm,
        });
        if (!importRes.ok) {
          throw new Error(`BPMN import failed with HTTP ${importRes.status}.`);
        }
        const importData = await importRes.json();
        const xmlText = await bpmnFile.text();
        onBpmnImported({
          knowledge: importData.knowledge,
          bpmnXml: xmlText,
          processName: importData.processName,
          processId: importData.processId,
          qualityReport: importData.qualityReport,
        });
        return;
      }

      let response: Response;

      if (activeTab === 'file') {
        const formData = new FormData();
        files.forEach((file) => {
          formData.append('files', file);
        });

        response = await fetch('http://localhost:8080/api/v1/process/extract-file', {
          method: 'POST',
          body: formData,
        });
      } else {
        response = await fetch('http://localhost:8080/api/v1/process/extract-text', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            content: text,
          }),
        });
      }

      if (!response.ok) {
        throw new Error(`Server returned ${response.status}: Failed to extract knowledge.`);
      }

      const processIntelligence: ProcessKnowledgeDTO = await response.json();
      onSuccess(processIntelligence);
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
          DOCUMENT UPLOAD
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
              accept=".pdf,.docx,.txt,.xlsx,.bpmn,.bpmn20,.xml"
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
                          <span className="file-icon">📄</span>
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
                  <div className="upload-title">Feed Pie multiple documents.</div>
                  <div className="upload-description">
                    PDF / DOCX / TXT / XLSX / BPMN · Drop them here or click to browse
                  </div>
                </div>
              )}
            </div>
          </>
          ) : (
            <div className="text-input-container">
              <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', flexWrap: 'wrap' }}>
                <button type="button" className="btn-outline" style={{ padding: '6px 12px', fontSize: '12px' }} onClick={() => setText('The employee submits a travel request. The manager reviews the request. If the manager approves it, the request goes to finance for budget validation. If finance approves, the travel desk books the tickets. If the request is rejected at any step, the employee is notified.')}>
                  Demo: Travel Request
                </button>
                <button type="button" className="btn-outline" style={{ padding: '6px 12px', fontSize: '12px' }} onClick={() => setText('An employee submits a leave application through the HR portal. The manager receives the application and reviews the requested dates. If the manager approves the leave, the system updates the employee\'s leave balance.')}>
                  Demo: Leave (Incomplete)
                </button>
                <button type="button" className="btn-outline" style={{ padding: '6px 12px', fontSize: '12px' }} onClick={() => setText('The requester submits a purchase requisition. The department head reviews it. If the amount is under $5000, the department head approves it and it goes directly to purchasing. If the amount is $5000 or greater, it requires additional approval from the CFO. Once all required approvals are met, the purchasing team generates a purchase order. If any approver rejects the requisition, it is sent back to the requester for revision.')}>
                  Demo: Purchase Request
                </button>
              </div>
              <textarea
                className="process-textarea"
                value={text}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setText(e.target.value)}
                placeholder="Describe the process you want Pie to analyze..."
                rows={12}
              />
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