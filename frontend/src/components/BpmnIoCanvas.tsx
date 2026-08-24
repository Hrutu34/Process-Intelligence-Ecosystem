import React, { useEffect, useRef, useState } from 'react';
// @ts-expect-error bpmn-js bundle import
import BpmnNavigatedViewer from 'bpmn-js/dist/bpmn-navigated-viewer.production.min.js';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import type { CanonicalProcessGraph } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { canonicalGraphToBpmnXml } from '../utils/bpmnXmlGenerator';
import './BpmnIoCanvas.css';

interface Props {
  graph: CanonicalProcessGraph;
}

export const BpmnIoCanvas: React.FC<Props> = ({ graph }) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const viewerRef = useRef<any>(null);
  const [copied, setCopied] = useState<boolean>(false);
  const [xmlString, setXmlString] = useState<string>('');

  useEffect(() => {
    if (!containerRef.current) return;

    // Clear previous canvas elements if any
    containerRef.current.innerHTML = '';

    const viewer = new BpmnNavigatedViewer({
      container: containerRef.current,
      keyboard: {
        bindTo: window,
      },
    });
    viewerRef.current = viewer;

    const xml = canonicalGraphToBpmnXml(graph);
    setXmlString(xml);

    viewer
      .importXML(xml)
      .then(() => {
        const canvas = viewer.get('canvas');
        canvas.zoom('fit-viewport', 'auto');
      })
      .catch((err: any) => {
        console.error('Failed to render BPMN XML with bpmn-js:', err);
      });

    return () => {
      if (viewerRef.current) {
        viewerRef.current.destroy();
      }
    };
  }, [graph]);

  const handleZoomIn = () => {
    if (viewerRef.current) {
      viewerRef.current.get('zoomScroll').stepZoom(1);
    }
  };

  const handleZoomOut = () => {
    if (viewerRef.current) {
      viewerRef.current.get('zoomScroll').stepZoom(-1);
    }
  };

  const handleResetZoom = () => {
    if (viewerRef.current) {
      viewerRef.current.get('canvas').zoom('fit-viewport', 'auto');
    }
  };

  const handleDownloadXml = () => {
    const blob = new Blob([xmlString], { type: 'application/xml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${graph.graphId || 'process'}.bpmn20.xml`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleCopyXml = () => {
    navigator.clipboard.writeText(xmlString);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="bpmn-io-wrapper">
      <div className="bpmn-io-toolbar">
        <div className="bpmn-io-info">
          <span className="bpmn-badge">bpmn.io / BPMN 2.0 Engine</span>
          <span style={{ color: 'var(--soft-white)', fontSize: 13 }}>
            Standard BPMN 2.0 Vector Diagram (Pan & Zoomable)
          </span>
        </div>

        <div className="bpmn-io-actions">
          <button type="button" className="btn-ghost" onClick={handleZoomIn} title="Zoom In">
            🔍 +
          </button>
          <button type="button" className="btn-ghost" onClick={handleZoomOut} title="Zoom Out">
            🔍 −
          </button>
          <button type="button" className="btn-ghost" onClick={handleResetZoom}>
            Fit View
          </button>
          <button type="button" className="btn-ghost" onClick={handleCopyXml}>
            {copied ? '✓ Copied XML' : '📋 Copy XML'}
          </button>
          <button type="button" className="yellow-button" onClick={handleDownloadXml}>
            DOWNLOAD .BPMN <span>↓</span>
          </button>
        </div>
      </div>

      <div ref={containerRef} className="bpmn-canvas-area" />
    </div>
  );
};
