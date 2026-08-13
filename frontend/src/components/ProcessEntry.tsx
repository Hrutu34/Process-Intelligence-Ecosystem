import { useState, useRef } from 'react';
import type { DragEvent, ChangeEvent } from 'react';
import './ProcessEntry.css';

const MAX_FILE_SIZE_MB = 10;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

const ALLOWED_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
];

export default function ProcessEntry() {
  const [activeTab, setActiveTab] = useState<'file' | 'text'>('file');
  const [file, setFile] = useState<File | null>(null);
  const [text, setText] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [isExtracting, setIsExtracting] = useState<boolean>(false);
  
  const fileInputRef = useRef<HTMLInputElement>(null);

  const validateFile = (selectedFile: File): boolean => {
    setError(null);
    if (!ALLOWED_TYPES.includes(selectedFile.type)) {
      setError('Invalid file type. Please upload a PDF, DOCX, TXT, or XLSX.');
      return false;
    }
    if (selectedFile.size > MAX_FILE_SIZE_BYTES) {
      setError(`File size exceeds the ${MAX_FILE_SIZE_MB}MB limit.`);
      return false;
    }
    return true;
  };

  const handleFileDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const droppedFile = e.dataTransfer.files[0];
      if (validateFile(droppedFile)) setFile(droppedFile);
    }
  };

  const handleFileSelect = (e: ChangeEvent<HTMLInputElement>) => {
    const target = e.target as HTMLInputElement;
    if (target.files && target.files.length > 0) {
      const selectedFile = target.files[0];
      if (validateFile(selectedFile)) setFile(selectedFile);
    }
  };

  const handleSubmit = async () => {
    setError(null);
    if (activeTab === 'file' && !file) {
      setError('Please select or drop a file first.');
      return;
    }
    if (activeTab === 'text' && text.trim().length < 20) {
      setError('Please enter a more detailed process description (min 20 characters).');
      return;
    }

    setIsExtracting(true);

    try {
      let response: Response;

      if (activeTab === 'file' && file) {
        const formData = new FormData();
        formData.append('file', file);

        response = await fetch('http://localhost:8080/api/v1/process/extract-file', {
          method: 'POST',
          body: formData,
        });
      } else {
        response = await fetch('http://localhost:8080/api/v1/process/extract-text', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ content: text }),
        });
      }

      if (!response.ok) {
        throw new Error(`Server returned ${response.status}: Failed to extract knowledge.`);
      }

      const processIntelligence = await response.json();
      console.log('✅ Extraction Complete:', processIntelligence);
      alert("Extraction successful! Check the browser console to see the JSON structure.");
      
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An unknown connection error occurred.');
    } finally {
      setIsExtracting(false);
    }
  };

  return (
    <div className="process-entry">
      <div className="entry-tabs">
        <button
          type="button"
          className={activeTab === 'file' ? 'active' : ''}
          onClick={() => setActiveTab('file')}
        >
          DOCUMENT UPLOAD
        </button>
        <button
          type="button"
          className={activeTab === 'text' ? 'active' : ''}
          onClick={() => setActiveTab('text')}
        >
          RAW TEXT INPUT
        </button>
      </div>

      <div className="entry-content">
        {activeTab === 'file' ? (
          <div
            className={`drop-zone ${isDragging ? 'dragging' : ''} ${file ? 'has-file' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleFileDrop}
            onClick={() => !file && fileInputRef.current?.click()}
          >
            <input
              type="file"
              ref={fileInputRef}
              style={{ display: 'none' }}
              accept=".pdf,.docx,.txt,.xlsx"
              onChange={handleFileSelect}
            />

            {file ? (
              <div className="file-info">
                <span className="file-icon">📄</span>
                <span className="file-name">{file.name}</span>
                <span className="file-size">{(file.size / 1024 / 1024).toFixed(2)} MB</span>
                <button
                  type="button"
                  className="remove-file"
                  onClick={(e) => { e.stopPropagation(); setFile(null); }}
                >
                  ×
                </button>
              </div>
            ) : (
              <div className="drop-placeholder">
                <div className="document-symbol"><span>+</span></div>
                <div className="upload-title">Feed P.I.E. some knowledge.</div>
                <div className="upload-subtitle">
                  PDF / DOCX / TXT / XLSX · Drop it here or click to browse
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="text-zone">
            <textarea
              placeholder="Describe your business process here..."
              value={text}
              onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setText(e.target.value)}
            />
          </div>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="entry-actions">
        <button 
          className="yellow-button" 
          type="button" 
          onClick={handleSubmit}
          disabled={isExtracting}
          style={{ 
            opacity: isExtracting ? 0.7 : 1, 
            cursor: isExtracting ? 'wait' : 'pointer' 
          }}
        >
          {isExtracting ? 'EXTRACTING KNOWLEDGE...' : 'INITIALIZE EXTRACTION'}
          {!isExtracting && <span>↗</span>}
        </button>
      </div>
    </div>
  );
}