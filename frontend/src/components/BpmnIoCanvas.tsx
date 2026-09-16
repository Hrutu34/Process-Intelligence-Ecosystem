import React, { useEffect, useRef, useState } from 'react';
// @ts-expect-error bpmn-js bundle import
import BpmnModeler from 'bpmn-js/dist/bpmn-modeler.production.min.js';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import type { ProcessGraphDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import type { ProcessKnowledgeDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { trackerStore } from '../services/trackerStore';
import './BpmnIoCanvas.css';

interface Props {
  graph: ProcessGraphDTO;
  knowledge?: ProcessKnowledgeDTO;
  externalXml?: string | null;
  onXmlChange?: (xml: string) => void;
  onReviewClick?: () => void;
  highlightedNodeId?: string | null;
  highlightColor?: string;
}

export const BpmnIoCanvas: React.FC<Props> = ({ graph, knowledge, externalXml, onXmlChange, onReviewClick, highlightedNodeId, highlightColor = '#ff6b6b' }) => {
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
    if (onXmlChange && xmlString) {
      onXmlChange(xmlString);
    }
  }, [xmlString, onXmlChange]);

  const lastEmittedRef = useRef<string>('');

  useEffect(() => {
    if (!containerRef.current) return;
    let isMounted = true;

    // Initialize Modeler
    const modeler = new BpmnModeler({ container: containerRef.current });
    viewerRef.current = modeler;

    const wireChangeListener = () => {
      const eventBus = modeler.get('eventBus');
      eventBus?.on('commandStack.changed', async () => {
        const saved = await modeler.saveXML({ format: true });
        if (isMounted && saved.xml) {
          lastEmittedRef.current = saved.xml;
          setXmlString(saved.xml);
        }
      });
    };

    const importReadyXml = async (xml: string) => {
      lastEmittedRef.current = xml;
      setXmlString(xml);
      await modeler.importXML(xml);
      wireChangeListener();
      modeler.get('canvas').zoom('fit-viewport', 'auto');
    };

    const loadDiagram = async () => {
      try {
        setIsLoading(true);
        setRenderError(null);

        // If parent already has a canonical XML (e.g. chat-applied edit or /import-bpmn upload),
        // skip the backend generator and render the provided XML directly.
        if (externalXml && externalXml.trim().length > 0) {
          await importReadyXml(externalXml);
          return;
        }

        trackerStore.setStageActive('BPMN_MODELLING', 'Generating BPMN XML from graph...');
        // Fetch XML natively from the Java Backend Engine
        const response = await fetch('http://localhost:8080/api/v1/process/bpmn/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ graph, knowledge })
        });

        if (!response.ok) {
          throw new Error(`Backend Error ${response.status}: Failed to generate BPMN XML`);
        }

        const xml = await response.text();
        if (!isMounted) return;

        await importReadyXml(xml);
        trackerStore.setStageComplete('BPMN_MODELLING', 'BPMN modelling complete.');
      } catch (error: any) {
        if (isMounted) setRenderError(error.message || 'BPMN diagram retrieval or rendering failed');
        trackerStore.updateState({ failedStage: 'BPMN_MODELLING', statusMessage: 'Failed to generate BPMN.' });
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };

    loadDiagram();

    return () => {
      isMounted = false;
      modeler.destroy();
      viewerRef.current = null;
    };
  }, [graph]);

  // Re-import when parent pushes a new externalXml that we did NOT emit ourselves
  // (e.g. chat applied an edit while the canvas is already mounted).
  useEffect(() => {
    if (!externalXml || !viewerRef.current) return;
    if (externalXml === lastEmittedRef.current) return;
    if (externalXml === xmlString) return;
    viewerRef.current
      .importXML(externalXml)
      .then(() => {
        lastEmittedRef.current = externalXml;
        setXmlString(externalXml);
        try {
          viewerRef.current.get('canvas').zoom('fit-viewport', 'auto');
        } catch { /* ignore */ }
      })
      .catch((e: any) => setRenderError(e.message || 'External BPMN import failed'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [externalXml]);

  
  useEffect(() => {
    if (!viewerRef.current || !highlightedNodeId) return;
    const canvas = viewerRef.current.get('canvas');
    const registry = viewerRef.current.get('elementRegistry');
    
    // Attempt to clear previous markers
    const elements = registry.getAll();
    elements.forEach((e: any) => {
      try {
        canvas.removeMarker(e.id, 'highlight-defect');
      } catch (e) {}
    });

    if (highlightedNodeId && registry.get(highlightedNodeId)) {
      try {
        // We add a dynamic style tag for the highlight color if it changes
        let styleEl = document.getElementById('bpmn-dynamic-highlight');
        if (!styleEl) {
          styleEl = document.createElement('style');
          styleEl.id = 'bpmn-dynamic-highlight';
          document.head.appendChild(styleEl);
        }
        styleEl.innerHTML = `
          .highlight-defect:not(.djs-connection) .djs-visual > :nth-child(1) {
            stroke: ${highlightColor} !important;
            stroke-width: 3px !important;
            fill: ${highlightColor}33 !important;
          }
          .highlight-defect.djs-connection .djs-visual > :nth-child(1) {
            stroke: ${highlightColor} !important;
            stroke-width: 3px !important;
          }
        `;
        canvas.addMarker(highlightedNodeId, 'highlight-defect');
        
        // Scroll into view
        const gfx = registry.get(highlightedNodeId);
        if (gfx) {
           const viewbox = canvas.viewbox();
           // Center on element
           canvas.viewbox({
             x: gfx.x - viewbox.width / 2 + gfx.width / 2,
             y: gfx.y - viewbox.height / 2 + gfx.height / 2,
             width: viewbox.width,
             height: viewbox.height
           });
        }
      } catch (e) {
        console.warn('Failed to highlight element', e);
      }
    }
  }, [highlightedNodeId, highlightColor, xmlString]);

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
          <button type="button" className="btn-ghost" onClick={handleDownloadXml} disabled={isLoading || !!renderError}>DOWNLOAD .BPMN <span>↓</span></button>
          {onReviewClick && (
            <button type="button" className="yellow-button" onClick={onReviewClick} disabled={isLoading || !!renderError}>REVIEW BPMN <span>↗</span></button>
          )}
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