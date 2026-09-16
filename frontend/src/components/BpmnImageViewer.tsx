import { useEffect, useRef, useState } from "react";
// @ts-expect-error bpmn-js bundle
import BpmnNavigatedViewer from "bpmn-js/dist/bpmn-navigated-viewer.production.min.js";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn.css";
import "./BpmnImageViewer.css";

interface Props {
  bpmnXml: string | null;
  height?: number;
}

export default function BpmnImageViewer({ bpmnXml, height = 320 }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const viewerRef = useRef<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [zoom, setZoom] = useState<number>(1);

  useEffect(() => {
    if (!containerRef.current) return;
    const viewer = new BpmnNavigatedViewer({ container: containerRef.current });
    viewerRef.current = viewer;
    return () => {
      try { viewer.destroy(); } catch {}
    };
  }, []);

  useEffect(() => {
    const viewer = viewerRef.current;
    if (!viewer || !bpmnXml) return;
    (async () => {
      try {
        await viewer.importXML(bpmnXml);
        const canvas = viewer.get("canvas");
        canvas.zoom("fit-viewport", "auto");
        const z = canvas.zoom() as number;
        setZoom(typeof z === "number" ? z : 1);
        setError(null);
      } catch (err: any) {
        setError(err?.message || "Failed to render diagram.");
      }
    })();
  }, [bpmnXml]);

  const applyZoom = (nextRaw: number) => {
    const viewer = viewerRef.current;
    if (!viewer) return;
    const next = Math.max(0.2, Math.min(4, nextRaw));
    const canvas = viewer.get("canvas");
    canvas.zoom(next);
    setZoom(next);
  };
  const zoomIn  = () => applyZoom(zoom + 0.2);
  const zoomOut = () => applyZoom(zoom - 0.2);
  const zoomFit = () => {
    const viewer = viewerRef.current;
    if (!viewer) return;
    const canvas = viewer.get("canvas");
    canvas.zoom("fit-viewport", "auto");
    const z = canvas.zoom() as number;
    setZoom(typeof z === "number" ? z : 1);
  };

  return (
    <div className="bpmn-image-viewer" style={{ height }}>
      <div ref={containerRef} className="bpmn-image-viewer-canvas" />
      {!bpmnXml && <div className="bpmn-image-viewer-placeholder">No BPMN loaded.</div>}
      {error && <div className="bpmn-image-viewer-error">{error}</div>}
      <div className="bpmn-image-viewer-toolbar">
        <button type="button" className="zoom-btn" onClick={zoomOut} title="Zoom out">−</button>
        <button type="button" className="zoom-btn zoom-fit" onClick={zoomFit} title="Fit to viewport">
          {Math.round(zoom * 100)}%
        </button>
        <button type="button" className="zoom-btn" onClick={zoomIn} title="Zoom in">+</button>
      </div>
    </div>
  );
}
