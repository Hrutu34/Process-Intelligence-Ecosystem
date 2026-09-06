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
  const [copied, setCopied] = useState(false);
  const [xmlString, setXmlString] = useState('');
  const [renderError, setRenderError] = useState<string | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    if (!containerRef.current) return;
    let isMounted = true;
    const modeler = new BpmnModeler({ container: containerRef.current });
    viewerRef.current = modeler;
    const xml = canonicalGraphToBpmnXml(graph);
    setXmlString(xml);
    modeler.importXML(xml).then(() => {
      const commandStack = modeler.get('commandStack');
      commandStack?.on('changed', async () => {
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
  const handleResetZoom = () => viewerRef.current?.get('canvas').zoom('fit-viewport', 'auto');

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
          <button type="button" className="btn-ghost" onClick={handleZoomIn}>Zoom +</button>
          <button type="button" className="btn-ghost" onClick={handleZoomOut}>Zoom -</button>
          <button type="button" className="btn-ghost" onClick={handleResetZoom}>Fit View</button>
          <label className="btn-ghost bpmn-file-button">Import BPMN<input type="file" accept=".bpmn,.xml,application/xml,text/xml" onChange={handleImportXml} /></label>
          <button type="button" className="btn-ghost" onClick={() => setIsFullscreen((current) => !current)}>
            {isFullscreen ? 'Exit Fullscreen' : 'Fullscreen'}
          </button>
          <button type="button" className="btn-ghost" onClick={handleCopyXml}>{copied ? 'Copied XML' : 'Copy XML'}</button>
          <button type="button" className="yellow-button" onClick={handleDownloadXml}>DOWNLOAD .BPMN <span>↓</span></button>
        </div>
      </div>
      {renderError && <div className="bpmn-render-error">BPMN notice: {renderError}</div>}
      <div ref={containerRef} className="bpmn-canvas-area" />
    </div>
  );
};
