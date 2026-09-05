import React, { useEffect, useRef, useState } from 'react';
// @ts-expect-error bpmn-js bundle import
import BpmnNavigatedViewer from 'bpmn-js/dist/bpmn-navigated-viewer.production.min.js';
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
  const [copied, setCopied] = useState<boolean>(false);
  const [xmlString, setXmlString] = useState<string>('');
  const [renderError, setRenderError] = useState<string | null>(null);

  useEffect(() => {
    if (!containerRef.current || !graph) return;

    let isMounted = true;
    let viewerInstance: any = null;

    try {
      // Clear container DOM
      containerRef.current.innerHTML = '';

      // Initialize NavigatedViewer without deprecated keyboard.bindTo
      viewerInstance = new BpmnNavigatedViewer({
        container: containerRef.current,
      });
      viewerRef.current = viewerInstance;

      const xml = canonicalGraphToBpmnXml(graph);
      setXmlString(xml);
      setRenderError(null);

      viewerInstance
        .importXML(xml)
        .then(() => {
          if (!isMounted) return;
          try {
            const canvas = viewerInstance.get('canvas');
            if (canvas) {
              canvas.zoom('fit-viewport', 'auto');
            }
          } catch (zoomErr) {
            console.warn('Canvas zoom adjustment warning:', zoomErr);
          }
        })
        .catch((err: any) => {
          if (!isMounted) return;
          console.error('Failed to render BPMN XML with bpmn-js:', err);
          setRenderError(err.message || 'BPMN diagram rendering failed');
        });
    } catch (initErr: any) {
      if (isMounted) {
        console.error('Viewer initialization error:', initErr);
        setRenderError(initErr.message || 'Failed to initialize BPMN viewer');
      }
    }

    return () => {
      isMounted = false;
      if (viewerInstance) {
        try {
          viewerInstance.destroy();
        } catch (e) {
          // Ignore destroy errors during component unmount
        }
      }
      viewerRef.current = null;
    };
  }, [graph]);

  const handleZoomIn = () => {
    if (viewerRef.current) {
      try {
        viewerRef.current.get('zoomScroll').stepZoom(1);
      } catch (e) {
        console.warn('Zoom error', e);
      }
    }
  };

  const handleZoomOut = () => {
    if (viewerRef.current) {
      try {
        viewerRef.current.get('zoomScroll').stepZoom(-1);
      } catch (e) {
        console.warn('Zoom error', e);
      }
    }
  };

  const handleResetZoom = () => {
    if (viewerRef.current) {
      try {
        viewerRef.current.get('canvas').zoom('fit-viewport', 'auto');
      } catch (e) {
        console.warn('Reset zoom error', e);
      }
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

      {renderError && (
        <div style={{ padding: '12px 16px', background: '#3b1818', color: '#ff8888', fontSize: 13, borderBottom: '1px solid #752a2a' }}>
          ⚠️ Render Notice: {renderError}
        </div>
      )}

      <div ref={containerRef} className="bpmn-canvas-area" />
    </div>
  );
};
