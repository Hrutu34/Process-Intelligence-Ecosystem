import React, { useEffect, useRef, useState } from 'react';
import BpmnViewerLib from 'bpmn-js/lib/NavigatedViewer';
import {
  ZoomIn,
  ZoomOut,
  Maximize2,
  RotateCcw,
  AlertTriangle,
  Layers,
  Play,
  Pause
} from 'lucide-react';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import type { ProcessTopology, SimulationParameters, SimulationResult } from '../../services/simulationTypes';

interface BpmnViewerProps {
  xml?: string | null;
  processName?: string;
  processCategory?: string;
  onSelectNode: (node: { id: string; name: string; type: string } | null) => void;
  selectedNodeId?: string;
  simulationResult?: SimulationResult | null;
  parameters?: SimulationParameters | null;
  parsedBpmn?: ProcessTopology | null;
}

export const BpmnViewer: React.FC<BpmnViewerProps> = ({
  xml,
  processName,
  processCategory,
  onSelectNode,
  simulationResult,
  parameters,
  parsedBpmn
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const viewerRef = useRef<any>(null);
  const [, setZoomLevel] = useState<number>(1);
  const [renderError, setRenderError] = useState<string | null>(null);

  // Animated Flow Controls
  const [isPlayingFlow, setIsPlayingFlow] = useState<boolean>(false);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(2); // 1x, 2x, 5x
  const [activeStepInfo, setActiveStepInfo] = useState<{
    nodeId?: string;
    name: string;
    durationMin?: number;
    completed?: boolean;
  } | null>(null);
  const animationTimerRef = useRef<any>(null);

  // Initialize and render BPMN viewer
  useEffect(() => {
    if (!containerRef.current || !xml) return;

    // Stop any active animation
    setIsPlayingFlow(false);
    if (animationTimerRef.current) clearTimeout(animationTimerRef.current);

    // Clean up previous instance
    if (viewerRef.current) {
      viewerRef.current.destroy();
      viewerRef.current = null;
    }

    setRenderError(null);

    const viewer = new BpmnViewerLib({
      container: containerRef.current,
      keyboard: { bindTo: document }
    });

    viewerRef.current = viewer;

    viewer
      .importXML(xml)
      .then(() => {
        const canvas: any = viewer.get('canvas');
        canvas.zoom('fit-viewport');
        const autoZoom = canvas.zoom();
        // If diagram was scaled down to microscopic size, ensure readable minimum scale
        if (autoZoom < 0.8) {
          canvas.zoom(Math.max(autoZoom, 0.85));
        }
        setZoomLevel(canvas.zoom());

        // Event: Element Click
        const eventBus: any = viewer.get('eventBus');
        eventBus.on('element.click', (event: any) => {
          const element = event.element;
          if (element && element.id && element.id !== '__implicitroot') {
            const businessObject = element.businessObject;
            onSelectNode({
              id: element.id,
              name: businessObject.name || element.id,
              type: businessObject.$type
            });
          }
        });
      })
      .catch((err: any) => {
        console.error('Error rendering BPMN XML:', err);
        setRenderError(err?.message || 'Failed to render BPMN XML');
      });

    // ResizeObserver to keep canvas sized accurately when layout dimensions change
    let resizeObserver: ResizeObserver | null = null;
    if (window.ResizeObserver && containerRef.current) {
      resizeObserver = new ResizeObserver(() => {
        if (viewerRef.current) {
          try {
            const canvas = viewerRef.current.get('canvas');
            canvas.resized();
          } catch (_) {}
        }
      });
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      if (resizeObserver) {
        resizeObserver.disconnect();
      }
      if (viewerRef.current) {
        viewerRef.current.destroy();
        viewerRef.current = null;
      }
      if (animationTimerRef.current) clearTimeout(animationTimerRef.current);
    };
  }, [xml, onSelectNode]);

  // Apply Speed-Proportional Animated Flow Arrows on Sequence Flows & Overlays
  useEffect(() => {
    if (!viewerRef.current || !parameters) return;
    const viewer = viewerRef.current;

    try {
      const overlays = viewer.get('overlays');
      const canvas = viewer.get('canvas');
      const elementRegistry = viewer.get('elementRegistry');

      // 1. Clear existing overlays & bottleneck markers
      overlays.clear();
      elementRegistry.forEach((el: any) => {
        canvas.removeMarker(el.id, 'highlight-bottleneck');
      });

      // 2. Map Sequence Flow Animation Speeds according to source task duration
      if (parsedBpmn && parsedBpmn.sequenceFlows) {
        Object.values(parsedBpmn.sequenceFlows).forEach((flow) => {
          const flowEl = elementRegistry.get(flow.id);
          if (!flowEl) return;

          canvas.removeMarker(flow.id, 'flow-speed-fast');
          canvas.removeMarker(flow.id, 'flow-speed-medium');
          canvas.removeMarker(flow.id, 'flow-speed-slow');
          canvas.removeMarker(flow.id, 'flow-speed-bottleneck');

          const sourceTaskParam = parameters.tasks ? parameters.tasks[flow.sourceRef] : null;
          const duration = sourceTaskParam ? Number(sourceTaskParam.meanDuration) || 20 : 15;

          if (duration <= 10) {
            canvas.addMarker(flow.id, 'flow-speed-fast');
          } else if (duration <= 35) {
            canvas.addMarker(flow.id, 'flow-speed-medium');
          } else if (duration <= 60) {
            canvas.addMarker(flow.id, 'flow-speed-slow');
          } else {
            canvas.addMarker(flow.id, 'flow-speed-bottleneck');
          }
        });
      }

      // 3. Render Metric Overlay Badges if simulation results exist
      if (simulationResult && simulationResult.activityBottlenecks) {
        simulationResult.activityBottlenecks.forEach((activity) => {
          const element = elementRegistry.get(activity.id);
          if (!element) return;

          let badgeClass = 'badge-safe';
          if (activity.bottleneckScore >= 70 || activity.avgWaitMinutes > 25) {
            badgeClass = 'badge-critical';
            canvas.addMarker(activity.id, 'highlight-bottleneck');
          } else if (activity.bottleneckScore >= 35 || activity.avgWaitMinutes > 5) {
            badgeClass = 'badge-warning';
          }

          const badgeEl = document.createElement('div');
          badgeEl.className = `bpmn-node-badge ${badgeClass}`;
          badgeEl.innerHTML = `
            <span>Wait: ${activity.avgWaitMinutes}m</span>
            <span style="opacity: 0.65;">•</span>
            <span>Load: ${activity.utilizationPercent}%</span>
          `;

          overlays.add(activity.id, {
            position: { top: 0, left: element.width / 2 },
            html: badgeEl
          });
        });
      }
    } catch (err) {
      console.warn('Flow animation update warning:', err);
    }
  }, [simulationResult, parameters, parsedBpmn]);

  // =========================================================================
  // Interactive Live Token Simulation Engine (Step-by-Step Traversal)
  // =========================================================================
  useEffect(() => {
    if (!isPlayingFlow || !viewerRef.current || !parsedBpmn) {
      if (animationTimerRef.current) clearTimeout(animationTimerRef.current);
      return;
    }

    const canvas = viewerRef.current.get('canvas');
    const elementRegistry = viewerRef.current.get('elementRegistry');

    let currentNodeId = parsedBpmn.startNodeIds[0] || Object.keys(parsedBpmn.nodes)[0];
    let previousFlowId: string | null = null;

    const executeStep = () => {
      if (!isPlayingFlow) return;

      if (previousFlowId) canvas.removeMarker(previousFlowId, 'flow-active-highlight');
      elementRegistry.forEach((el: any) => canvas.removeMarker(el.id, 'node-active-simulating'));

      const node = parsedBpmn.nodes[currentNodeId];
      if (!node) {
        setIsPlayingFlow(false);
        setActiveStepInfo(null);
        return;
      }

      canvas.addMarker(currentNodeId, 'node-active-simulating');

      const taskParam = parameters?.tasks?.[currentNodeId];
      const durationMin = taskParam
        ? Number(taskParam.meanDuration) || 20
        : node.type === 'task'
        ? 25
        : 5;

      setActiveStepInfo({
        nodeId: currentNodeId,
        name: node.name || currentNodeId,
        durationMin
      });

      const baseDwellMs = Math.max(350, Math.min(3000, (durationMin / 20) * 800));
      const effectiveDwellMs = baseDwellMs / playbackSpeed;

      if (node.type === 'endEvent' || !node.outgoing || node.outgoing.length === 0) {
        animationTimerRef.current = setTimeout(() => {
          canvas.removeMarker(currentNodeId, 'node-active-simulating');
          setActiveStepInfo({ name: 'Process Instance Completed', durationMin: 0, completed: true });
          animationTimerRef.current = setTimeout(() => {
            currentNodeId = parsedBpmn.startNodeIds[0] || Object.keys(parsedBpmn.nodes)[0];
            executeStep();
          }, 1200 / playbackSpeed);
        }, effectiveDwellMs);
        return;
      }

      let nextFlowId = node.outgoing[0];
      if (node.type === 'exclusiveGateway' && node.outgoing.length > 1) {
        const gwParam = parameters?.gateways?.[node.id];
        if (gwParam && gwParam.branches && gwParam.branches.length > 0) {
          const rand = Math.random();
          let cum = 0;
          for (const b of gwParam.branches) {
            cum += b.probability;
            if (rand <= cum) {
              nextFlowId = b.flowId;
              break;
            }
          }
        }
      }

      animationTimerRef.current = setTimeout(() => {
        canvas.removeMarker(currentNodeId, 'node-active-simulating');

        const flow = parsedBpmn.sequenceFlows[nextFlowId];
        if (flow) {
          canvas.addMarker(flow.id, 'flow-active-highlight');
          previousFlowId = flow.id;
          currentNodeId = flow.targetRef;

          const flowTravelMs = 400 / playbackSpeed;
          animationTimerRef.current = setTimeout(executeStep, flowTravelMs);
        } else {
          setIsPlayingFlow(false);
        }
      }, effectiveDwellMs);
    };

    executeStep();

    return () => {
      if (animationTimerRef.current) clearTimeout(animationTimerRef.current);
    };
  }, [isPlayingFlow, playbackSpeed, parsedBpmn, parameters]);

  const handleToggleFlow = () => {
    if (isPlayingFlow) {
      setIsPlayingFlow(false);
      setActiveStepInfo(null);
      if (animationTimerRef.current) clearTimeout(animationTimerRef.current);
      if (viewerRef.current) {
        const canvas = viewerRef.current.get('canvas');
        const elementRegistry = viewerRef.current.get('elementRegistry');
        elementRegistry.forEach((el: any) => {
          canvas.removeMarker(el.id, 'node-active-simulating');
          canvas.removeMarker(el.id, 'flow-active-highlight');
        });
      }
    } else {
      setIsPlayingFlow(true);
    }
  };

  const handleZoom = (delta: number) => {
    if (!viewerRef.current) return;
    const canvas = viewerRef.current.get('canvas');
    const current = canvas.zoom();
    const next = Math.max(0.2, Math.min(4, current + delta));
    canvas.zoom(next);
    setZoomLevel(next);
  };

  const handleFitViewport = () => {
    if (!viewerRef.current) return;
    const canvas = viewerRef.current.get('canvas');
    canvas.zoom('fit-viewport');
    const autoZoom = canvas.zoom();
    if (autoZoom < 0.8) {
      canvas.zoom(Math.max(autoZoom, 0.85));
    }
    setZoomLevel(canvas.zoom());
  };

  const handleResetZoom = () => {
    if (!viewerRef.current) return;
    const canvas = viewerRef.current.get('canvas');
    canvas.zoom(1.0);
    setZoomLevel(1.0);
  };

  return (
    <div className="bpmn-area">
      {/* Top Diagram Bar */}
      <div className="bpmn-header-bar">
        <div className="process-meta-info">
          <Layers size={16} color="#818cf8" />
          <span className="process-meta-title">{processName || 'Process Flow'}</span>
          {processCategory && <span className="process-meta-category">{processCategory}</span>}
          {simulationResult && (
            <span style={{ fontSize: '0.75rem', color: '#94a3b8', marginLeft: '8px' }}>
              • Top Bottleneck:{' '}
              <strong style={{ color: '#f43f5e' }}>{simulationResult.summary.primaryBottleneck}</strong>
            </span>
          )}
        </div>

        {/* Live Token Status Ticker */}
        {activeStepInfo && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              fontSize: '0.75rem',
              padding: '2px 10px',
              borderRadius: '12px',
              background: 'rgba(56, 189, 248, 0.15)',
              border: '1px solid rgba(56, 189, 248, 0.35)',
              color: '#38bdf8'
            }}
          >
            <span
              style={{
                width: '6px',
                height: '6px',
                borderRadius: '50%',
                background: '#38bdf8',
                display: 'inline-block'
              }}
            />
            <span>
              Active: <strong>{activeStepInfo.name}</strong>
            </span>
            {(activeStepInfo.durationMin ?? 0) > 0 && (
              <span style={{ color: '#94a3b8' }}>({activeStepInfo.durationMin} min duration)</span>
            )}
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.75rem', color: '#64748b' }}>
          <span>💡 Click any task to edit • Arrows flow by task time</span>
        </div>
      </div>

      {/* BPMN Canvas Container */}
      <div className="bpmn-canvas-container" ref={containerRef}>
        {renderError && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#f43f5e',
              gap: '12px'
            }}
          >
            <AlertTriangle size={36} />
            <div style={{ fontWeight: 600 }}>Diagram Rendering Issue</div>
            <div style={{ fontSize: '0.85rem', color: '#94a3b8', maxWidth: '400px', textAlign: 'center' }}>
              {renderError}
            </div>
          </div>
        )}

        {/* Floating Toolbar: Zoom Controls & Animated Token Player */}
        <div className="canvas-toolbar">
          <button
            className={`toolbar-btn ${isPlayingFlow ? 'active' : ''}`}
            onClick={handleToggleFlow}
            title={isPlayingFlow ? 'Pause Token Flow Animation' : 'Start Animated Flow Along Diagram'}
          >
            {isPlayingFlow ? <Pause size={16} color="#38bdf8" /> : <Play size={16} fill="currentColor" />}
          </button>

          {isPlayingFlow && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '2px',
                background: 'rgba(30, 41, 59, 0.6)',
                borderRadius: '6px',
                padding: '2px'
              }}
            >
              {[1, 2, 5].map((s) => (
                <button
                  key={s}
                  style={{
                    border: 'none',
                    background: playbackSpeed === s ? 'rgba(99, 102, 241, 0.4)' : 'transparent',
                    color: playbackSpeed === s ? '#ffffff' : '#94a3b8',
                    fontSize: '0.68rem',
                    fontWeight: 700,
                    borderRadius: '4px',
                    padding: '2px 6px',
                    cursor: 'pointer'
                  }}
                  onClick={() => setPlaybackSpeed(s)}
                >
                  {s}x
                </button>
              ))}
            </div>
          )}

          <div className="toolbar-divider" />

          <button className="toolbar-btn" onClick={() => handleZoom(0.2)} title="Zoom In">
            <ZoomIn size={16} />
          </button>
          <button className="toolbar-btn" onClick={() => handleZoom(-0.2)} title="Zoom Out">
            <ZoomOut size={16} />
          </button>
          <button className="toolbar-btn" onClick={handleFitViewport} title="Fit to Screen">
            <Maximize2 size={15} />
          </button>
          <button className="toolbar-btn" onClick={handleResetZoom} title="Reset Zoom (100%)">
            <RotateCcw size={15} />
          </button>
        </div>
      </div>
    </div>
  );
};
