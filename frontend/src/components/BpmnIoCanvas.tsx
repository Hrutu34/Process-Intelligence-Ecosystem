import React, { useEffect, useRef, useState } from 'react';
// @ts-expect-error bpmn-js bundle import
import BpmnModeler from 'bpmn-js/dist/bpmn-modeler.production.min.js';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import type { ProcessGraphDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { canonicalGraphToBpmnXml } from '../utils/bpmnXmlGenerator';
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

  useEffect(() => {
    if (!containerRef.current) return;
    let isMounted = true;
    const modeler = new BpmnModeler({ container: containerRef.current });
    viewerRef.current = modeler;
    const xml = canonicalGraphToBpmnXml(graph);
    setXmlString(xml);
    modeler.importXML(xml).then(() => {
      const eventBus = modeler.get('eventBus');
      eventBus?.on('commandStack.changed', async () => {
        const saved = await modeler.saveXML({ format: true });
        if (isMounted && saved.xml) setXmlString(saved.xml);
      });
      modeler.get('canvas').zoom('fit-viewport', 'auto');
      setRenderError(null);
    }).catch((error: Error) => {
      if (isMounted) setRenderError(error.message || 'BPMN diagram rendering failed');
    });
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
            <svg className="bpmn-zoom-glyph" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <line x1="21" y1="21" x2="16.2" y2="16.2" />
            </svg>
          </button>
          <button type="button" className="btn-ghost bpmn-zoom-button" onClick={handleZoomOut} title="Zoom out" aria-label="Zoom out">
            <span className="bpmn-zoom-sign">-</span>
            <svg className="bpmn-zoom-glyph" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <line x1="21" y1="21" x2="16.2" y2="16.2" />
            </svg>
          </button>
          <button type="button" className="btn-ghost bpmn-fullscreen-button" onClick={() => setIsFullscreen((current) => !current)} aria-label={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'} title={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'}>
            {isFullscreen ? 'Exit Fullscreen' : 'Fullscreen'}
          </button>
          <button type="button" className="btn-ghost" onClick={handleCopyXml}>{copied ? 'Copied XML' : 'Copy XML'}</button>
          <button type="button" className="yellow-button" onClick={handleDownloadXml}>DOWNLOAD .BPMN <span>↓</span></button>
        </div>
      </div>
      {renderError && <div className="bpmn-render-error">BPMN notice: {renderError}</div>}
      <div ref={containerRef} className="bpmn-canvas-area" />
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
