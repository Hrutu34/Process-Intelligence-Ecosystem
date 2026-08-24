import React, { useState, useEffect } from 'react';
import type {
  ProcessKnowledgeDTO,
  CanonicalProcessGraph,
  GraphNode,
  GraphEdge
} from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { BpmnIoCanvas } from './BpmnIoCanvas';
import './ProcessGraphViewer.css';

interface Props {
  knowledge: ProcessKnowledgeDTO;
  onProceedToBpmn?: () => void;
  defaultView?: 'visual' | 'bpmn' | 'topology' | 'json';
}

type ViewMode = 'visual' | 'bpmn' | 'topology' | 'json';

export const ProcessGraphViewer: React.FC<Props> = ({ knowledge, onProceedToBpmn, defaultView = 'visual' }) => {
  const [graph, setGraph] = useState<CanonicalProcessGraph | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [viewMode, setViewMode] = useState<ViewMode>(defaultView);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null);
  const [copied, setCopied] = useState<boolean>(false);

  // Fetch or construct CanonicalProcessGraph from backend API
  useEffect(() => {
    let isMounted = true;
    setLoading(true);

    fetch('http://localhost:8080/api/v1/process/graph', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(knowledge),
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP error: ${res.status}`);
        return res.json();
      })
      .then((data: CanonicalProcessGraph) => {
        if (isMounted) {
          setGraph(data);
          setLoading(false);
          if (data.nodes && data.nodes.length > 0) {
            setSelectedNodeId(data.nodes[0].id);
          }
        }
      })
      .catch((err) => {
        console.warn('Backend /graph fetch failed, constructing local fallback:', err);
        // Fallback local canonical construction for immediate preview
        const fallback = buildLocalFallbackGraph(knowledge);
        if (isMounted) {
          setGraph(fallback);
          setLoading(false);
          if (fallback.nodes.length > 0) {
            setSelectedNodeId(fallback.nodes[0].id);
          }
        }
      });

    return () => {
      isMounted = false;
    };
  }, [knowledge]);

  const handleCopyJson = () => {
    if (!graph) return;
    navigator.clipboard.writeText(JSON.stringify(graph, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (loading) {
    return (
      <div className="graph-viewer-container">
        <div className="graph-header-bar">
          <div className="graph-title-group">
            <h3>⚡ Generating Canonical Process Graph...</h3>
            <p>Constructing deterministic nodes, gateways, sequence edges, and role associations.</p>
          </div>
        </div>
      </div>
    );
  }

  if (!graph) {
    return (
      <div className="graph-viewer-container">
        <p>No graph available.</p>
      </div>
    );
  }

  const activityNodes = graph.nodes.filter((n) => n.type === 'Activity');
  const roleNodes = graph.nodes.filter((n) => n.type === 'Role');
  const gatewayNodes = graph.nodes.filter((n) => n.type === 'Gateway');
  const eventNodes = graph.nodes.filter((n) => n.type === 'Event');
  const sequenceEdges = graph.edges.filter((e) => e.edgeType === 'sequence');
  const conditionalEdges = graph.edges.filter((e) => e.edgeType === 'conditional');
  const associationEdges = graph.edges.filter((e) => e.edgeType === 'association');

  const selectedNode = graph.nodes.find((n) => n.id === selectedNodeId) || null;

  // Compute incoming and outgoing edges for selected node
  const incomingEdges = selectedNode ? graph.edges.filter((e) => e.to === selectedNode.id) : [];
  const outgoingEdges = selectedNode ? graph.edges.filter((e) => e.from === selectedNode.id) : [];

  return (
    <div className="graph-viewer-container">
      {/* Header Bar */}
      <div className="graph-header-bar">
        <div className="graph-title-group">
          <div className="status-pill">
            <span className="status-dot" />
            CANONICAL GRAPH READY
            <span className="status-separator">/</span>
            {graph.graphId}
          </div>
          <h3>⚡ Canonical Process Graph</h3>
          <p>Deterministic graph intermediate representation for validation & BPMN generation.</p>
        </div>

        {/* Stats Row */}
        <div className="graph-stats-row">
          <div className="graph-stat-pill">
            <span>Nodes:</span> <strong>{graph.nodes.length}</strong>
          </div>
          <div className="graph-stat-pill">
            <span>Sequence:</span> <strong>{sequenceEdges.length}</strong>
          </div>
          <div className="graph-stat-pill">
            <span>Decisions:</span> <strong>{gatewayNodes.length}</strong>
          </div>
          <div className="graph-stat-pill">
            <span>Roles:</span> <strong>{roleNodes.length}</strong>
          </div>
        </div>

        {/* View Switcher */}
        <div className="graph-view-tabs">
          <button
            type="button"
            className={`graph-tab-btn ${viewMode === 'visual' ? 'active' : ''}`}
            onClick={() => setViewMode('visual')}
          >
            ⚡ Visual Flow
          </button>
          <button
            type="button"
            className={`graph-tab-btn ${viewMode === 'bpmn' ? 'active' : ''}`}
            onClick={() => setViewMode('bpmn')}
          >
            ⌘ bpmn.io Canvas
          </button>
          <button
            type="button"
            className={`graph-tab-btn ${viewMode === 'topology' ? 'active' : ''}`}
            onClick={() => setViewMode('topology')}
          >
            📐 Topology Table
          </button>
          <button
            type="button"
            className={`graph-tab-btn ${viewMode === 'json' ? 'active' : ''}`}
            onClick={() => setViewMode('json')}
          >
            💻 JSON Graph
          </button>
        </div>
      </div>

      {/* VIEW 1: VISUAL DIAGRAM FLOW */}
      {viewMode === 'visual' && (
        <div className="graph-canvas-wrapper">
          {/* Main Visual Stage */}
          <div className="graph-main-stage">
            {/* Roles / Swimlanes Strip */}
            {roleNodes.length > 0 && (
              <div className="swimlanes-panel">
                <span className="swimlanes-title">👤 PARTICIPATING ROLES & ACTORS</span>
                <div className="roles-pills-container">
                  <button
                    type="button"
                    className={`role-pill-interactive ${selectedRoleId === null ? 'active' : ''}`}
                    onClick={() => setSelectedRoleId(null)}
                  >
                    All Roles
                  </button>
                  {roleNodes.map((role) => {
                    const assignedActs = graph.edges
                      .filter((e) => e.from === role.id && e.edgeType === 'association')
                      .map((e) => e.to);
                    return (
                      <button
                        type="button"
                        key={role.id}
                        className={`role-pill-interactive ${selectedRoleId === role.id ? 'active' : ''}`}
                        onClick={() => setSelectedRoleId(selectedRoleId === role.id ? null : role.id)}
                      >
                        <span className="role-avatar">👤</span>
                        <span>{role.label}</span>
                        <span className="role-count-badge">{assignedActs.length} tasks</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Visual Process Execution Chain */}
            <div className="flow-chain">
              {/* Start Event if present */}
              {eventNodes.some((e) => e.metadata?.eventType === 'start') && (
                <div className="flow-step-wrapper">
                  <div
                    className="event-circle start"
                    onClick={() => setSelectedNodeId(eventNodes.find((e) => e.metadata?.eventType === 'start')?.id || null)}
                  >
                    <span className="event-icon">🟢</span>
                    <span className="event-label">Start</span>
                  </div>
                  <div className="flow-arrow-container">
                    <span className="flow-arrow-line">→</span>
                  </div>
                </div>
              )}

              {/* Sequential Activities & Gateways */}
              {activityNodes.map((act, index) => {
                const roleRef = act.metadata?.roleRef;
                const roleObj = roleNodes.find((r) => r.id === roleRef);
                const isRoleFiltered = selectedRoleId ? roleRef === selectedRoleId : true;

                // Check if this activity connects to a gateway
                const outgoingGatewayEdge = graph.edges.find(
                  (e) => e.from === act.id && e.edgeType === 'sequence' && e.to.startsWith('gateway-')
                );
                const gatewayNode = outgoingGatewayEdge
                  ? gatewayNodes.find((g) => g.id === outgoingGatewayEdge.to)
                  : null;
                const conditionalEdgeFromGateway = gatewayNode
                  ? graph.edges.find((e) => e.from === gatewayNode.id && e.edgeType === 'conditional')
                  : null;

                return (
                  <React.Fragment key={act.id}>
                    {/* Activity Node */}
                    <div
                      className={`node-card ${selectedNodeId === act.id ? 'selected' : ''}`}
                      style={{ opacity: isRoleFiltered ? 1 : 0.4 }}
                      onClick={() => setSelectedNodeId(act.id)}
                    >
                      <div className="node-card-header">
                        <span className="node-step-badge">STEP 0{index + 1}</span>
                        <span className="node-type-icon">⚡</span>
                      </div>
                      <div className="node-label">{act.label}</div>
                      {roleObj && (
                        <div className="node-role-tag">
                          <span>👤</span>
                          <span>{roleObj.label}</span>
                        </div>
                      )}
                    </div>

                    {/* If Gateway follows this activity */}
                    {gatewayNode && (
                      <>
                        <div className="flow-arrow-container">
                          <span className="flow-arrow-line">→</span>
                        </div>
                        <div
                          className={`gateway-card ${selectedNodeId === gatewayNode.id ? 'selected' : ''}`}
                          onClick={() => setSelectedNodeId(gatewayNode.id)}
                        >
                          <div className="gateway-icon">🔀</div>
                          <div className="gateway-title">{gatewayNode.label}</div>
                          <span className="gateway-type-badge">EXCLUSIVE</span>
                        </div>
                        <div className="flow-arrow-container">
                          <span className="flow-arrow-line">→</span>
                          {conditionalEdgeFromGateway?.label && (
                            <span className="flow-condition-tag">
                              [{conditionalEdgeFromGateway.label}]
                            </span>
                          )}
                        </div>
                      </>
                    )}

                    {/* Standard Sequence Arrow if not last and not gateway */}
                    {!gatewayNode && index < activityNodes.length - 1 && (
                      <div className="flow-arrow-container">
                        <span className="flow-arrow-line">→</span>
                      </div>
                    )}
                  </React.Fragment>
                );
              })}

              {/* End Event if present */}
              {eventNodes.some((e) => e.metadata?.eventType === 'end') && (
                <div className="flow-step-wrapper">
                  <div className="flow-arrow-container">
                    <span className="flow-arrow-line">→</span>
                  </div>
                  <div
                    className="event-circle end"
                    onClick={() => setSelectedNodeId(eventNodes.find((e) => e.metadata?.eventType === 'end')?.id || null)}
                  >
                    <span className="event-icon">🏁</span>
                    <span className="event-label">End</span>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Node Inspector Sidebar */}
          <div className="graph-inspector-sidebar">
            <div className="inspector-header">
              <h4>🔍 Node Inspector</h4>
              {selectedNode && (
                <span className="inspector-badge">{selectedNode.type}</span>
              )}
            </div>

            {selectedNode ? (
              <div className="inspector-body">
                <div className="inspector-field">
                  <label>Label</label>
                  <div className="value-box" style={{ color: 'var(--aqua)', fontWeight: 600 }}>
                    {selectedNode.label}
                  </div>
                </div>

                <div className="inspector-field">
                  <label>Deterministic ID</label>
                  <div className="value-box">{selectedNode.id}</div>
                </div>

                {selectedNode.metadata?.roleRef && (
                  <div className="inspector-field">
                    <label>Assigned Role Ref</label>
                    <div className="value-box">{selectedNode.metadata.roleRef}</div>
                  </div>
                )}

                {selectedNode.metadata?.gatewayType && (
                  <div className="inspector-field">
                    <label>Gateway Type</label>
                    <div className="value-box">{selectedNode.metadata.gatewayType}</div>
                  </div>
                )}

                <div className="inspector-field">
                  <label>Incoming Edges ({incomingEdges.length})</label>
                  <div className="value-box">
                    {incomingEdges.length === 0 ? (
                      <span style={{ color: 'var(--muted)' }}>None (Initial Node)</span>
                    ) : (
                      incomingEdges.map((e) => (
                        <div key={e.id} style={{ marginBottom: 4 }}>
                          ← <strong>{e.from}</strong> ({e.edgeType})
                        </div>
                      ))
                    )}
                  </div>
                </div>

                <div className="inspector-field">
                  <label>Outgoing Edges ({outgoingEdges.length})</label>
                  <div className="value-box">
                    {outgoingEdges.length === 0 ? (
                      <span style={{ color: 'var(--muted)' }}>None (Terminal Node)</span>
                    ) : (
                      outgoingEdges.map((e) => (
                        <div key={e.id} style={{ marginBottom: 4 }}>
                          → <strong>{e.to}</strong> ({e.edgeType}
                          {e.label ? ` [${e.label}]` : ''})
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <div className="inspector-body">
                <p style={{ color: 'var(--muted)', fontSize: 13 }}>
                  Click any activity, gateway, or role node in the diagram to inspect its metadata and edge relationships.
                </p>
              </div>
            )}

            {onProceedToBpmn && (
              <button
                type="button"
                className="yellow-button"
                style={{ marginTop: 'auto' }}
                onClick={onProceedToBpmn}
              >
                PROCEED TO BPMN MODELLING <span>↗</span>
              </button>
            )}
          </div>
        </div>
      )}

      {/* VIEW: BPMN.IO CANVAS */}
      {viewMode === 'bpmn' && (
        <BpmnIoCanvas graph={graph} />
      )}

      {/* VIEW 2: TOPOLOGY TABLE VIEW */}
      {viewMode === 'topology' && (
        <div className="topology-table-wrapper">
          <table className="topology-table">
            <thead>
              <tr>
                <th>Edge ID</th>
                <th>Source Node (From)</th>
                <th>Target Node (To)</th>
                <th>Edge Type</th>
                <th>Branch Condition</th>
              </tr>
            </thead>
            <tbody>
              {graph.edges.map((edge) => (
                <tr key={edge.id}>
                  <td style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>{edge.id}</td>
                  <td>
                    <strong>{edge.from}</strong>
                  </td>
                  <td>
                    <strong>{edge.to}</strong>
                  </td>
                  <td>
                    <span className={`edge-type-badge ${edge.edgeType}`}>{edge.edgeType}</span>
                  </td>
                  <td>
                    {edge.label ? (
                      <span className="flow-condition-tag">[{edge.label}]</span>
                    ) : (
                      <span style={{ color: 'var(--muted)' }}>—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* VIEW 3: RAW CANONICAL JSON VIEW */}
      {viewMode === 'json' && (
        <div className="raw-json-wrapper">
          <div className="raw-json-actions">
            <button type="button" className="btn-ghost" onClick={handleCopyJson}>
              {copied ? '✓ Copied!' : '📋 Copy JSON'}
            </button>
          </div>
          <pre className="raw-json-code">{JSON.stringify(graph, null, 2)}</pre>
        </div>
      )}
    </div>
  );
};

// Local fallback builder if backend call is delayed
function buildLocalFallbackGraph(k: ProcessKnowledgeDTO): CanonicalProcessGraph {
  const nodes: GraphNode[] = [];
  const edges: GraphEdge[] = [];

  (k.activities || []).forEach((act) => {
    const slug = act.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    nodes.push({
      id: `activity-${slug}`,
      type: 'Activity',
      label: act,
      metadata: {},
    });
  });

  (k.actors || []).forEach((actor, i) => {
    const slug = actor.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    const roleId = `role-${slug}`;
    nodes.push({ id: roleId, type: 'Role', label: actor, metadata: {} });
    if (i < (k.activities || []).length) {
      const actSlug = (k.activities || [])[i].toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
      edges.push({
        id: `edge-${roleId}-activity-${actSlug}-association`,
        from: roleId,
        to: `activity-${actSlug}`,
        edgeType: 'association',
      });
    }
  });

  (k.gateways || []).forEach((gw) => {
    const slug = gw.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    nodes.push({
      id: `gateway-${slug}`,
      type: 'Gateway',
      label: gw,
      metadata: { gatewayType: 'exclusive' },
    });
  });

  // Basic sequence links
  for (let i = 0; i < (k.activities || []).length - 1; i++) {
    const fromSlug = (k.activities || [])[i].toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    const toSlug = (k.activities || [])[i + 1].toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    edges.push({
      id: `edge-activity-${fromSlug}-activity-${toSlug}-sequence`,
      from: `activity-${fromSlug}`,
      to: `activity-${toSlug}`,
      edgeType: 'sequence',
    });
  }

  return {
    graphId: 'graph-canonical-local',
    nodes,
    edges,
  };
}
