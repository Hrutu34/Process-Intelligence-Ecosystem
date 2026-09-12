import React, { useEffect, useRef, useState, useCallback } from 'react';
// @ts-expect-error bpmn-js bundle imports (no bundled types)
import BpmnNavigatedViewer from 'bpmn-js/dist/bpmn-navigated-viewer.production.min.js';
// @ts-expect-error bpmn-js bundle imports (no bundled types)
import BpmnModeler from 'bpmn-js/dist/bpmn-modeler.production.min.js';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import type { CanonicalProcessGraph } from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { canonicalGraphToBpmnXml } from '../utils/bpmnXmlGenerator';
import './BpmnIoCanvas.css';

interface Props {
  graph: CanonicalProcessGraph;
  rawXml?: string;
  editable?: boolean;
  onXmlChange?: (xml: string) => void;
}

export const BpmnIoCanvas: React.FC<Props> = ({
  graph,
  rawXml,
  editable = false,
  onXmlChange,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const viewerRef = useRef<any>(null);
  const [copied, setCopied] = useState<boolean>(false);
  const [xmlString, setXmlString] = useState<string>('');
  const [renderError, setRenderError] = useState<string | null>(null);
  const [editMode, setEditMode] = useState<boolean>(editable);
  const [isDirty, setIsDirty] = useState<boolean>(false);
  const [canUndo, setCanUndo] = useState<boolean>(false);
  const [canRedo, setCanRedo] = useState<boolean>(false);

  const onXmlChangeRef = useRef(onXmlChange);
  const lastEmittedRef = useRef<string>('');
  const graphId = graph?.graphId;

  useEffect(() => {
    onXmlChangeRef.current = onXmlChange;
  }, [onXmlChange]);

  const emitXml = useCallback(async () => {
    if (!viewerRef.current) return;
    try {
      const result = await viewerRef.current.saveXML({ format: true });
      const xml = result?.xml as string;
      if (xml) {
        lastEmittedRef.current = xml;
        setXmlString(xml);
        onXmlChangeRef.current?.(xml);
      }
    } catch (err) {
      console.warn('saveXML failed', err);
    }
  }, []);

  // Re-import when parent pushes a new rawXml that we did NOT emit ourselves (e.g. chat-applied edit).
  useEffect(() => {
    if (!rawXml || !viewerRef.current) return;
    if (rawXml === lastEmittedRef.current) return;
    if (rawXml === xmlString) return;
    try {
      viewerRef.current.importXML(rawXml).then(() => {
        try {
          viewerRef.current.get('canvas').zoom('fit-viewport', 'auto');
        } catch {}
        setXmlString(rawXml);
        setIsDirty(false);
      }).catch((e: any) => console.warn('external xml import failed', e));
    } catch (e) {
      console.warn('external xml import error', e);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rawXml]);

  useEffect(() => {
    if (!containerRef.current) return;

    // Seed order: preserve in-memory edits across mode toggle; else parent rawXml; else regenerate.
    const seedXml =
      (viewerRef.current && xmlString) || rawXml || (graph ? canonicalGraphToBpmnXml(graph) : '');
    if (!seedXml) return;

    let isMounted = true;
    let instance: any = null;

    try {
      containerRef.current.innerHTML = '';

      instance = editMode
        ? new BpmnModeler({
            container: containerRef.current,
            keyboard: { bindTo: window },
          })
        : new BpmnNavigatedViewer({
            container: containerRef.current,
          });
      viewerRef.current = instance;

      setXmlString(seedXml);
      setRenderError(null);

      instance
        .importXML(seedXml)
        .then(() => {
          if (!isMounted) return;
          try {
            const canvas = instance.get('canvas');
            if (canvas) canvas.zoom('fit-viewport', 'auto');
          } catch (zoomErr) {
            console.warn('zoom warn', zoomErr);
          }

          if (editMode) {
            try {
              const eventBus = instance.get('eventBus');
              const commandStack = instance.get('commandStack');
              setCanUndo(commandStack.canUndo());
              setCanRedo(commandStack.canRedo());

              const onChanged = () => {
                if (!isMounted) return;
                setIsDirty(true);
                setCanUndo(commandStack.canUndo());
                setCanRedo(commandStack.canRedo());
                emitXml();
              };
              eventBus.on('commandStack.changed', onChanged);
              eventBus.on('elements.changed', onChanged);
            } catch (e) {
              console.warn('event wiring failed', e);
            }
          }
        })
        .catch((err: any) => {
          if (!isMounted) return;
          console.error('BPMN import failed', err);
          setRenderError(err.message || 'BPMN diagram rendering failed');
        });
    } catch (initErr: any) {
      if (isMounted) {
        console.error('Viewer init error', initErr);
        setRenderError(initErr.message || 'Failed to initialize BPMN viewer');
      }
    }

    return () => {
      isMounted = false;
      if (instance) {
        try {
          instance.destroy();
        } catch {}
      }
      viewerRef.current = null;
    };
    // Rebuild only on graph identity change or edit-mode toggle.
    // Parent-driven rawXml changes (from our own emit) MUST NOT re-init.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [graphId, editMode]);

  const handleZoomIn = () => {
    try {
      viewerRef.current?.get('zoomScroll').stepZoom(1);
    } catch {}
  };

  const handleZoomOut = () => {
    try {
      viewerRef.current?.get('zoomScroll').stepZoom(-1);
    } catch {}
  };

  const handleResetZoom = () => {
    try {
      viewerRef.current?.get('canvas').zoom('fit-viewport', 'auto');
    } catch {}
  };

  const handleUndo = () => {
    try {
      viewerRef.current?.get('commandStack').undo();
    } catch {}
  };

  const handleRedo = () => {
    try {
      viewerRef.current?.get('commandStack').redo();
    } catch {}
  };

  const handleDeleteSelected = () => {
    try {
      const selection = viewerRef.current?.get('selection').get();
      if (selection && selection.length) {
        viewerRef.current?.get('modeling').removeElements(selection);
      }
    } catch (e) {
      console.warn('delete failed', e);
    }
  };

  const handleSaveXml = async () => {
    if (!viewerRef.current) return;
    try {
      const result = await viewerRef.current.saveXML({ format: true });
      setXmlString(result?.xml || '');
      setIsDirty(false);
      onXmlChange?.(result?.xml || '');
    } catch (e) {
      console.warn('save failed', e);
    }
  };

  const handleDownloadXml = async () => {
    let xml = xmlString;
    if (viewerRef.current) {
      try {
        const result = await viewerRef.current.saveXML({ format: true });
        xml = result?.xml || xml;
      } catch {}
    }
    const blob = new Blob([xml], { type: 'application/xml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${graph.graphId || 'process'}.bpmn`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleDownloadSvg = async () => {
    if (!viewerRef.current) return;
    try {
      const result = await viewerRef.current.saveSVG();
      const svg = result?.svg;
      if (!svg) return;
      const blob = new Blob([svg], { type: 'image/svg+xml' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${graph.graphId || 'process'}.svg`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      console.warn('svg export failed', e);
    }
  };

  const handleCopyXml = async () => {
    let xml = xmlString;
    if (viewerRef.current) {
      try {
        const result = await viewerRef.current.saveXML({ format: true });
        xml = result?.xml || xml;
      } catch {}
    }
    await navigator.clipboard.writeText(xml);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const toggleEditMode = () => {
    setEditMode((v) => !v);
    setIsDirty(false);
  };

  return (
    <div className={`bpmn-io-wrapper ${editMode ? 'edit-mode' : 'view-mode'}`}>
      <div className="bpmn-io-toolbar">
        <div className="bpmn-io-info">
          <span className="bpmn-badge">
            bpmn.io / {editMode ? 'BPMN 2.0 Editor' : 'BPMN 2.0 Viewer'}
          </span>
          <span style={{ color: 'var(--soft-white)', fontSize: 13 }}>
            {editMode
              ? 'Drag from palette · Double-click to rename · Right-click for context menu · Ctrl+Z/Y undo/redo · Del to remove'
              : 'Standard BPMN 2.0 Vector Diagram (Pan & Zoomable)'}
          </span>
          {editMode && isDirty && <span className="bpmn-dirty-dot" title="Unsaved changes">●</span>}
        </div>

        <div className="bpmn-io-actions">
          <button
            type="button"
            className={editMode ? 'yellow-button' : 'btn-ghost'}
            onClick={toggleEditMode}
            title={editMode ? 'Switch to view mode' : 'Enable editing'}
          >
            {editMode ? '✎ EDITING' : '✎ EDIT'}
          </button>

          {editMode && (
            <>
              <button
                type="button"
                className="btn-ghost"
                onClick={handleUndo}
                disabled={!canUndo}
                title="Undo (Ctrl+Z)"
              >
                ↶ Undo
              </button>
              <button
                type="button"
                className="btn-ghost"
                onClick={handleRedo}
                disabled={!canRedo}
                title="Redo (Ctrl+Y)"
              >
                ↷ Redo
              </button>
              <button
                type="button"
                className="btn-ghost"
                onClick={handleDeleteSelected}
                title="Delete selected (Del)"
              >
                🗑 Delete
              </button>
              <button
                type="button"
                className="btn-ghost"
                onClick={handleSaveXml}
                disabled={!isDirty}
                title="Persist current edits"
              >
                💾 Save
              </button>
            </>
          )}

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
          <button type="button" className="btn-ghost" onClick={handleDownloadSvg}>
            ⬇ SVG
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
