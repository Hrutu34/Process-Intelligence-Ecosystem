import React, { useEffect, useRef, useState } from 'react';
// @ts-expect-error bpmn-js bundle import
import BpmnModeler from 'bpmn-js/dist/bpmn-modeler.production.min.js';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import type { ProcessGraphDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import './BpmnIoCanvas.css';

interface Props {
  graph: ProcessGraphDTO;
}

export const BpmnIoCanvas: React.FC<Props> = ({ graph }) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const viewerRef = useRef<any>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [copied, setCopied] = useState(false);
  const [xmlString, setXmlString] = useState('');
  const [renderError, setRenderError] = useState<string | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showImportConfirm, setShowImportConfirm] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!containerRef.current) return;
    let isMounted = true;
    
    // Initialize Modeler
    const modeler = new BpmnModeler({ container: containerRef.current });
    viewerRef.current = modeler;

    const loadDiagramFromBackend = async () => {
      try {
        setIsLoading(true);
        setRenderError(null);

        // Fetch XML natively from the Java Backend Engine
        const response = await fetch('http://localhost:8080/api/v1/process/bpmn/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(graph)
        });

        if (!response.ok) {
          throw new Error(`Backend Error ${response.status}: Failed to generate BPMN XML`);
        }

        const xml = await response.text();
        if (!isMounted) return;

        setXmlString(xml);

        // Import generated XML into the canvas
        await modeler.importXML(xml);
        
        const eventBus = modeler.get('eventBus');
        eventBus?.on('commandStack.changed', async () => {
          const saved = await modeler.saveXML({ format: true });
          if (isMounted && saved.xml) setXmlString(saved.xml);
        });
        
        modeler.get('canvas').zoom('fit-viewport', 'auto');
      } catch (error: any) {
        if (isMounted) setRenderError(error.message || 'BPMN diagram retrieval or rendering failed');
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };

    loadDiagramFromBackend();

    return () => {
      isMounted = false;
      modeler.destroy();
      viewerRef.current = null;
    };
  }, [graph]);

  const handleZoomIn = () => viewerRef.current?.get('zoomScroll').stepZoom(1);
  const handleZoomOut = () => viewerRef.current?.get('zoomScroll').stepZoom(-1);

  const handleConfirmImport = () => {
    setShowImportConfirm(false);
    fileInputRef.current?.click();
  };

  const handleDownloadXml = () => {
    const url = URL.createObjectURL(new Blob([xmlString], { type: 'application/xml' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `${graph.graphId || 'process'}.bpmn20.xml`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleCopyXml = () => {
    navigator.clipboard.writeText(xmlString);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleImportXml = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !viewerRef.current) return;
    const reader = new FileReader();
    reader.onload = () => {
      const importedXml = typeof reader.result === 'string' ? reader.result : '';
      if (!importedXml.includes('<bpmn:definitions') && !importedXml.includes('<definitions')) {
        setRenderError('Invalid BPMN XML: definitions element not found.');
        return;
      }
      viewerRef.current.importXML(importedXml).then(async () => {
        const saved = await viewerRef.current.saveXML({ format: true });
        setXmlString(saved.xml || importedXml);
        viewerRef.current.get('canvas').zoom('fit-viewport', 'auto');
        setRenderError(null);
      }).catch((error: Error) => setRenderError(error.message || 'BPMN XML import failed'));
    };
    reader.readAsText(file);
    event.target.value = '';
  };

  return (
    <div className={`bpmn-io-wrapper ${isFullscreen ? 'bpmn-fullscreen' : ''}`}>
      <div className="bpmn-io-toolbar">
        <div className="bpmn-io-info">
          <span className="bpmn-badge">bpmn.io / BPMN 2.0 Modeler</span>
          <span style={{ color: 'var(--soft-white)', fontSize: 13 }}>Editable BPMN diagram</span>
        </div>
        <div className="bpmn-io-actions">
          <button type="button" className="btn-ghost bpmn-zoom-button" onClick={handleZoomIn} title="Zoom in" aria-label="Zoom in">
            <span className="bpmn-zoom-sign">+</span>
          </button>
          <button type="button" className="btn-ghost bpmn-zoom-button" onClick={handleZoomOut} title="Zoom out" aria-label="Zoom out">
            <span className="bpmn-zoom-sign">-</span>
          </button>
          <button type="button" className="btn-ghost bpmn-fullscreen-button" onClick={() => setIsFullscreen((current) => !current)}>
            {isFullscreen ? 'Exit Fullscreen' : 'Fullscreen'}
          </button>
          <button type="button" className="btn-ghost" onClick={handleCopyXml}>{copied ? 'Copied XML' : 'Copy XML'}</button>
          <button type="button" className="yellow-button" onClick={handleDownloadXml} disabled={isLoading || !!renderError}>DOWNLOAD .BPMN <span>↓</span></button>
        </div>
      </div>
      
      {isLoading && (
        <div style={{ padding: '40px', textAlign: 'center', color: 'var(--aqua)', fontFamily: 'var(--mono)' }}>
          Contacting backend generator...
        </div>
      )}
      
      {renderError && <div className="bpmn-render-error">BPMN notice: {renderError}</div>}
      
      <div ref={containerRef} className="bpmn-canvas-area" style={{ opacity: isLoading ? 0.3 : 1 }} />
      
      <div className="bpmn-bottom-actions">
        <button type="button" className="btn-ghost bpmn-file-button" onClick={() => setShowImportConfirm(true)}>
          Import BPMN XML
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept=".bpmn,.xml,application/xml,text/xml"
          onChange={handleImportXml}
          style={{ display: 'none' }}
        />
      </div>
      
      {showImportConfirm && (
        <div className="bpmn-confirm-overlay" onClick={() => setShowImportConfirm(false)}>
          <div className="bpmn-confirm-card" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h3>Import BPMN XML?</h3>
            <p>
              Importing a BPMN file will <strong>override the currently generated diagram</strong>.
              This action cannot be undone and the generated diagram will not be recoverable.
            </p>
            <div className="bpmn-confirm-actions">
              <button type="button" className="btn-ghost" onClick={() => setShowImportConfirm(false)}>
                No, Cancel
              </button>
              <button type="button" className="yellow-button" onClick={handleConfirmImport}>
                Yes, Import
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};