import React, { useRef, useState } from 'react';
import './ProcessEntry.css';

const MAX_FILE_SIZE_MB = 10;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

const ALLOWED_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
];

type ActiveTab = 'file' | 'text';

export default function ProcessEntry() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('file');
  const [files, setFiles] = useState<File[]>([]);
  const [text, setText] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [isExtracting, setIsExtracting] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const validateFiles = (newFiles: FileList | File[]): File[] => {
    setError(null);

    const validFiles: File[] = [];
    const currentFiles = files;

    Array.from(newFiles).forEach((file: File) => {
      if (!ALLOWED_TYPES.includes(file.type)) {
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
            existingFile.name === file.name &&
            existingFile.size === file.size
        ) ||
        validFiles.some(
          (existingFile) =>
            existingFile.name === file.name &&
            existingFile.size === file.size
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

    // Allows selecting the same file again after removing it.
    e.target.value = '';
  };

  const removeFile = (indexToRemove: number) => {
    setFiles((prev) =>
      prev.filter((_, index) => index !== indexToRemove)
    );
  };

  const handleSubmit = async () => {
    setError(null);

    if (activeTab === 'file' && files.length === 0) {
      setError('Please select or drop at least one file.');
      return;
    }

    if (activeTab === 'text' && text.trim().length < 50) {
      setError('Please enter a more detailed process description.');
      return;
    }

    setIsExtracting(true);

    try {
      let response: Response;

      if (activeTab === 'file') {
        const formData = new FormData();

        files.forEach((file) => {
          formData.append('files', file);
        });

        response = await fetch(
          'http://localhost:8080/api/v1/process/extract-file',
          {
            method: 'POST',
            body: formData,
          }
        );
      } else {
        response = await fetch(
          'http://localhost:8080/api/v1/process/extract-text',
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              content: text,
            }),
          }
        );
      }

      if (!response.ok) {
        throw new Error(
          `Server returned ${response.status}: Failed to extract knowledge.`
        );
      }

      const processIntelligence = await response.json();

      console.log(
        '✅ Multi-File Extraction Complete:',
        processIntelligence
      );

      if (
        processIntelligence.conflicts &&
        processIntelligence.conflicts.length > 0
      ) {
        alert(
          `Extraction successful! Warning: ${processIntelligence.conflicts.length} conflict(s) detected. Check console.`
        );
      } else {
        alert(
          'Extraction successful! Check the browser console to see the JSON structure.'
        );
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : 'An unknown connection error occurred.'
      );
    } finally {
      setIsExtracting(false);
    }
  };

  return (
    <div className="process-entry">
      {/* Tabs */}
      <div className="entry-tabs">
        <button
          type="button"
          className={`entry-tab ${
            activeTab === 'file' ? 'active' : ''
          }`}
          onClick={() => setActiveTab('file')}
        >
          DOCUMENT UPLOAD
        </button>

        <button
          type="button"
          className={`entry-tab ${
            activeTab === 'text' ? 'active' : ''
          }`}
          onClick={() => setActiveTab('text')}
        >
          RAW TEXT INPUT
        </button>
      </div>

      {/* Content */}
      <div className="entry-content">
        {activeTab === 'file' ? (
          <>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept=".pdf,.docx,.txt,.xlsx"
              onChange={handleFileSelect}
              style={{ display: 'none' }}
            />

            <div
              className={`drop-zone ${
                isDragging ? 'dragging' : ''
              } ${files.length > 0 ? 'has-file' : ''}`}
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
                        {files.length} Document
                        {files.length !== 1 ? 's' : ''} Ready
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
                            <span className="file-name">
                              {file.name}
                            </span>

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

                  <div className="upload-title">
                    Feed P.I.E. multiple documents.
                  </div>

                  <div className="upload-description">
                    PDF / DOCX / TXT / XLSX · Drop them here or click
                    to browse
                  </div>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="text-input-container">
            <textarea
              className="process-textarea"
              value={text}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) =>
                setText(e.target.value)
              }
              placeholder="Describe the process you want P.I.E. to analyze..."
              rows={12}
            />
          </div>
        )}
      </div>

      {/* Error */}
      {error && <div className="error-message">{error}</div>}

      {/* Actions */}
      <div className="entry-actions">
        <button
          className="yellow-button"
          type="button"
          onClick={handleSubmit}
          disabled={isExtracting}
          style={{
            opacity: isExtracting ? 0.7 : 1,
            cursor: isExtracting ? 'wait' : 'pointer',
          }}
        >
          {isExtracting
            ? 'EXTRACTING & COMPARING...'
            : 'INITIALIZE EXTRACTION'}

          {!isExtracting && <span>↗</span>}
        </button>
      </div>
    </div>
  );
}
