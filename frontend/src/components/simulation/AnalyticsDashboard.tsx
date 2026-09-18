import React, { useState } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
  Cell
} from 'recharts';
import {
  Clock,
  AlertOctagon,
  Layers,
  ListOrdered,
  Maximize2,
  Minimize2,
  Download,
  Search,
  Sparkles,
  AlertCircle,
  CheckCircle2,
  X
} from 'lucide-react';
import type { SimulationResult, DynamicChartSpec } from '../../services/simulationTypes';
import { DynamicContextualChart } from './DynamicContextualChart';

interface AnalyticsDashboardProps {
  simulationResult: SimulationResult | null;
  isMinimized: boolean;
  onToggleMinimize: () => void;
  onClose?: () => void;
  onExportCsv: () => void;
  contextualChartSpec?: DynamicChartSpec | null;
}

export const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({
  simulationResult,
  isMinimized,
  onToggleMinimize,
  onClose,
  onExportCsv,
  contextualChartSpec
}) => {
  const [activeTab, setActiveTab] = useState<'bottlenecks' | 'leadtime' | 'resources' | 'eventlog' | 'aichart'>(
    'bottlenecks'
  );
  const [logSearchQuery, setLogSearchQuery] = useState<string>('');

  if (!simulationResult) {
    return (
      <div className={`analytics-panel ${isMinimized ? 'minimized' : ''}`}>
        <div className="analytics-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.88rem', fontWeight: 600 }}>
            <Clock size={16} color="#818cf8" />
            <span>Process Simulation &amp; Performance Analytics</span>
          </div>
          <span style={{ fontSize: '0.76rem', color: '#64748b' }}>
            Click &quot;Run Simulation&quot; in the action toolbar to compute performance metrics
          </span>
        </div>
      </div>
    );
  }

  const { summary, activityBottlenecks, histogram, eventLog } = simulationResult;
  const diag = summary.executiveDiagnosis;
  const entityName = diag?.entityName || 'Cases';
  const entitySingular = diag?.entitySingular || 'Case';

  const CustomBottleneckTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      const waitTime = payload.find((p: any) => p.dataKey === 'avgWaitMinutes')?.value || 0;
      const serviceTime = payload.find((p: any) => p.dataKey === 'avgServiceMinutes')?.value || 0;
      const totalTime = (Number(waitTime) + Number(serviceTime)).toFixed(1);
      const activityData = activityBottlenecks.find((a) => a.name === label);

      return (
        <div className="custom-recharts-tooltip">
          <div
            style={{
              fontWeight: 700,
              marginBottom: '6px',
              color: '#f8fafc',
              borderBottom: '1px solid rgba(255,255,255,0.1)',
              paddingBottom: '4px'
            }}
          >
            {label}
          </div>
          <div style={{ color: '#fb7185', display: 'flex', justifyContent: 'space-between', gap: '12px' }}>
            <span>🔴 Wasted Queue Wait (Delay):</span>
            <strong>{waitTime} min</strong>
          </div>
          <div style={{ color: '#818cf8', display: 'flex', justifyContent: 'space-between', gap: '12px' }}>
            <span>🟣 Active Working Time:</span>
            <strong>{serviceTime} min</strong>
          </div>
          <div
            style={{
              color: '#e2e8f0',
              marginTop: '4px',
              borderTop: '1px solid rgba(255,255,255,0.1)',
              paddingTop: '4px',
              display: 'flex',
              justifyContent: 'space-between'
            }}
          >
            <span>⏱️ Total Task Duration:</span>
            <strong>{totalTime} min</strong>
          </div>
          {activityData && (
            <div style={{ fontSize: '0.72rem', color: '#94a3b8', marginTop: '4px' }}>
              Resource Utilization:{' '}
              <strong style={{ color: activityData.utilizationPercent >= 80 ? '#fb7185' : '#38bdf8' }}>
                {activityData.utilizationPercent}%
              </strong>{' '}
              ({activityData.capacity} workers)
            </div>
          )}
        </div>
      );
    }
    return null;
  };

  const filteredEventLogs = (eventLog || []).filter((item) => {
    if (!logSearchQuery) return true;
    const q = logSearchQuery.toLowerCase();
    return item.caseId.toLowerCase().includes(q) || item.activityName.toLowerCase().includes(q);
  });

  return (
    <div className={`analytics-panel ${isMinimized ? 'minimized' : ''}`}>
      {/* Header with Navigation Tabs */}
      <div className="analytics-header">
        <div className="analytics-tabs">
          <button
            className={`analytics-tab-btn ${activeTab === 'bottlenecks' ? 'active' : ''}`}
            onClick={() => setActiveTab('bottlenecks')}
          >
            <AlertOctagon size={15} />
            <span>Bottlenecks &amp; Queues</span>
          </button>

          <button
            className={`analytics-tab-btn ${activeTab === 'leadtime' ? 'active' : ''}`}
            onClick={() => setActiveTab('leadtime')}
          >
            <Clock size={15} />
            <span>Lead Time Distribution</span>
          </button>

          <button
            className={`analytics-tab-btn ${activeTab === 'resources' ? 'active' : ''}`}
            onClick={() => setActiveTab('resources')}
          >
            <Layers size={15} />
            <span>Resource Utilization &amp; Costs</span>
          </button>

          <button
            className={`analytics-tab-btn ${activeTab === 'eventlog' ? 'active' : ''}`}
            onClick={() => setActiveTab('eventlog')}
          >
            <ListOrdered size={15} />
            <span>Process Mining Event Log</span>
          </button>

          {contextualChartSpec && (
            <button
              className={`analytics-tab-btn ${activeTab === 'aichart' ? 'active' : ''}`}
              onClick={() => setActiveTab('aichart')}
              style={{ color: '#c084fc' }}
            >
              <Sparkles size={15} />
              <span>AI Dynamic Graph</span>
            </button>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button
            className="toolbar-btn"
            onClick={onToggleMinimize}
            title={isMinimized ? 'Expand Analytics Dashboard' : 'Minimize Dashboard'}
          >
            {isMinimized ? <Maximize2 size={15} /> : <Minimize2 size={15} />}
          </button>
          {onClose && (
            <button
              className="toolbar-btn"
              onClick={onClose}
              title="Close Graphs & Analytics"
              style={{ color: '#f43f5e' }}
            >
              <X size={16} />
            </button>
          )}
        </div>
      </div>

      {/* Main Analytics Content */}
      {!isMinimized && (
        <div className="analytics-content">
          {/* AI Executive Process Diagnosis Banner */}
          {diag && (
            <div
              style={{
                padding: '12px 16px',
                borderRadius: '10px',
                background:
                  diag.severity === 'critical'
                    ? 'rgba(244, 63, 94, 0.12)'
                    : diag.severity === 'warning'
                    ? 'rgba(245, 158, 11, 0.12)'
                    : 'rgba(16, 185, 129, 0.12)',
                border: `1px solid ${
                  diag.severity === 'critical'
                    ? 'rgba(244, 63, 94, 0.35)'
                    : diag.severity === 'warning'
                    ? 'rgba(245, 158, 11, 0.35)'
                    : 'rgba(16, 185, 129, 0.35)'
                }`,
                marginBottom: '14px',
                display: 'flex',
                flexDirection: 'column',
                gap: '6px'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {diag.severity === 'critical' ? (
                    <AlertCircle size={17} color="#f43f5e" />
                  ) : diag.severity === 'warning' ? (
                    <AlertOctagon size={17} color="#f59e0b" />
                  ) : (
                    <CheckCircle2 size={17} color="#10b981" />
                  )}
                  <strong style={{ fontSize: '0.9rem', color: '#f8fafc' }}>{diag.headline}</strong>
                </div>
                <span
                  style={{
                    fontSize: '0.68rem',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                    padding: '2px 8px',
                    borderRadius: '12px',
                    background:
                      diag.severity === 'critical'
                        ? 'rgba(244, 63, 94, 0.25)'
                        : diag.severity === 'warning'
                        ? 'rgba(245, 158, 11, 0.25)'
                        : 'rgba(16, 185, 129, 0.25)',
                    color:
                      diag.severity === 'critical'
                        ? '#fb7185'
                        : diag.severity === 'warning'
                        ? '#fbbf24'
                        : '#34d399'
                  }}
                >
                  {diag.severity}
                </span>
              </div>
              <p style={{ margin: 0, fontSize: '0.78rem', color: '#cbd5e1', lineHeight: '1.45' }}>
                {diag.rootCause}
              </p>
              {diag.recommendation && (
                <div
                  style={{
                    fontSize: '0.76rem',
                    color: '#a5b4fc',
                    background: 'rgba(99, 102, 241, 0.1)',
                    padding: '6px 10px',
                    borderRadius: '6px',
                    marginTop: '2px'
                  }}
                >
                  💡 <strong>Actionable Recommendation:</strong> {diag.recommendation}
                </div>
              )}
            </div>
          )}

          {/* Top KPI Cards Grid */}
          <div className="kpi-grid">
            {/* Avg Cycle Time */}
            <div className="kpi-card">
              <div className="kpi-title">Average End-to-End Time</div>
              <div className="kpi-value">{summary.avgCycleTimeHours} hrs</div>
              <div className="kpi-sub">
                <span style={{ color: '#818cf8' }}>Active: {summary.avgServiceTimeHours || 0}h</span>
                <span>•</span>
                <span style={{ color: '#fb7185' }}>Wait: {summary.avgWaitTimeHours || 0}h</span>
              </div>
            </div>

            {/* SLA Adherence */}
            <div className="kpi-card">
              <div className="kpi-title">On-Time SLA Adherence</div>
              <div
                className="kpi-value"
                style={{
                  color:
                    summary.slaAdherencePercent >= 85
                      ? '#34d399'
                      : summary.slaAdherencePercent >= 60
                      ? '#f59e0b'
                      : '#fb7185'
                }}
              >
                {summary.slaAdherencePercent}%
              </div>
              <div className="kpi-sub">
                <span>
                  Target: &le; {summary.slaThresholdHours} hrs ({summary.slaBreachedCount} late)
                </span>
              </div>
            </div>

            {/* Primary Bottleneck */}
            <div className="kpi-card">
              <div className="kpi-title">Primary Bottleneck</div>
              <div
                className="kpi-value"
                style={{
                  fontSize: '1.15rem',
                  color: '#fb7185',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap'
                }}
              >
                {summary.primaryBottleneck}
              </div>
              <div className="kpi-sub">
                <span>Avg Queue Delay: {activityBottlenecks[0]?.avgWaitMinutes || 0} min</span>
              </div>
            </div>

            {/* Avg Cost Per Case */}
            <div className="kpi-card">
              <div className="kpi-title">Cost per {entitySingular}</div>
              <div className="kpi-value">${summary.avgCostPerCase}</div>
              <div className="kpi-sub">
                <span>Total Cost: ${summary.totalProcessCost.toLocaleString()}</span>
              </div>
            </div>
          </div>

          {/* TAB 1: BOTTLENECK ANALYSIS */}
          {activeTab === 'bottlenecks' && (
            <div className="chart-container-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', flexWrap: 'wrap', gap: '8px' }}>
                <span style={{ fontSize: '0.82rem', color: '#f8fafc', fontWeight: 600 }}>
                  Where are {entityName} Waiting in Line? (Queue Delay vs. Active Service Time)
                </span>
                <span style={{ fontSize: '0.72rem', color: '#38bdf8' }}>
                  💡 Tallest coral red bar is your #1 delay driver. Increase capacity to unclog the workflow.
                </span>
              </div>
              <div style={{ flex: 1, width: '100%', minWidth: 0, minHeight: 0 }}>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={activityBottlenecks} margin={{ top: 10, right: 25, left: 0, bottom: 45 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                    <XAxis
                      dataKey="name"
                      stroke="#94a3b8"
                      fontSize={11}
                      angle={-18}
                      textAnchor="end"
                      interval={0}
                      height={50}
                    />
                    <YAxis stroke="#94a3b8" fontSize={11} />
                    <Tooltip content={<CustomBottleneckTooltip />} />
                    <Legend wrapperStyle={{ fontSize: '11px', paddingTop: '4px' }} />
                    <Bar
                      dataKey="avgWaitMinutes"
                      name="Queue Delay (Sitting in Line)"
                      stackId="a"
                      fill="#f43f5e"
                      radius={[0, 0, 0, 0]}
                    />
                    <Bar
                      dataKey="avgServiceMinutes"
                      name="Active Working Time"
                      stackId="a"
                      fill="#6366f1"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* TAB 2: LEAD TIME DISTRIBUTION */}
          {activeTab === 'leadtime' && (
            <div className="chart-container-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', flexWrap: 'wrap', gap: '8px' }}>
                <span style={{ fontSize: '0.82rem', color: '#f8fafc', fontWeight: 600 }}>
                  Completion Time Spread for {entityName}
                </span>
                <span style={{ fontSize: '0.72rem', color: '#38bdf8' }}>
                  💡 Cyan bars beat the deadline ({summary.slaThresholdHours}h). Red bars were late.
                </span>
              </div>
              <div style={{ flex: 1, width: '100%', minWidth: 0, minHeight: 0 }}>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={histogram} margin={{ top: 10, right: 25, left: 0, bottom: 25 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                    <XAxis dataKey="range" stroke="#94a3b8" fontSize={11} height={30} />
                    <YAxis stroke="#94a3b8" fontSize={11} />
                    <Tooltip
                      formatter={(value: any) => [`${value} ${entityName}`, 'Volume']}
                      contentStyle={{ background: '#0f172a', borderColor: '#334155', borderRadius: '8px' }}
                    />
                    <ReferenceLine
                      x={summary.slaBinRange || histogram[Math.min(histogram.length - 1, 4)]?.range}
                      stroke="#f43f5e"
                      strokeDasharray="4 4"
                      label={{
                        value: `SLA Deadline (${summary.slaThresholdHours}h)`,
                        fill: '#fb7185',
                        fontSize: 11,
                        position: 'top'
                      }}
                    />
                    <Bar dataKey="count" name={`Completed ${entityName}`} radius={[4, 4, 0, 0]}>
                      {histogram.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.isBreached ? '#f43f5e' : '#06b6d4'} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* TAB 3: RESOURCE UTILIZATION & COSTS */}
          {activeTab === 'resources' && (
            <div className="chart-container-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', flexWrap: 'wrap', gap: '8px' }}>
                <span style={{ fontSize: '0.82rem', color: '#f8fafc', fontWeight: 600 }}>
                  Worker Stress &amp; Capacity Meter (% of Time Busy per Activity)
                </span>
                <span style={{ fontSize: '0.72rem', color: '#38bdf8' }}>
                  💡 Green (&lt;60%) is relaxed. Orange (60-85%) is balanced. Red (&gt;85%) is overloaded.
                </span>
              </div>
              <div style={{ flex: 1, width: '100%', minWidth: 0, minHeight: 0 }}>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={activityBottlenecks} margin={{ top: 10, right: 25, left: 0, bottom: 45 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
                    <XAxis
                      dataKey="name"
                      stroke="#94a3b8"
                      fontSize={11}
                      angle={-18}
                      textAnchor="end"
                      interval={0}
                      height={50}
                    />
                    <YAxis stroke="#94a3b8" fontSize={11} domain={[0, 100]} />
                    <Tooltip
                      formatter={(val: any, _name: any, item: any) => [
                        `${val}% (${item.payload.capacity} workers)`,
                        'Utilization'
                      ]}
                      contentStyle={{ background: '#0f172a', borderColor: '#334155', borderRadius: '8px' }}
                    />
                    <ReferenceLine
                      y={85}
                      stroke="#f59e0b"
                      strokeDasharray="3 3"
                      label={{ value: 'Stress Line (85%)', fill: '#f59e0b', fontSize: 10 }}
                    />
                    <Bar dataKey="utilizationPercent" name="Utilization %" radius={[4, 4, 0, 0]}>
                      {activityBottlenecks.map((entry, index) => (
                        <Cell
                          key={`cell-${index}`}
                          fill={
                            entry.utilizationPercent >= 85
                              ? '#f43f5e'
                              : entry.utilizationPercent >= 60
                              ? '#f59e0b'
                              : '#10b981'
                          }
                        />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* TAB 4: PROCESS MINING EVENT LOG EXPLORER */}
          {activeTab === 'eventlog' && (
            <div className="chart-container-card" style={{ height: 'auto', minHeight: '340px' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: '12px',
                  flexWrap: 'wrap',
                  gap: '10px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '320px', maxWidth: '100%' }}>
                  <Search size={15} color="#64748b" />
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Search by Case ID or Activity..."
                    style={{ padding: '6px 12px', fontSize: '0.78rem', width: '100%' }}
                    value={logSearchQuery}
                    onChange={(e) => setLogSearchQuery(e.target.value)}
                  />
                </div>
                <button
                  className="btn btn-secondary"
                  onClick={onExportCsv}
                  style={{ padding: '6px 12px', fontSize: '0.78rem' }}
                >
                  <Download size={14} />
                  <span>Download Full CSV</span>
                </button>
              </div>

              <div style={{ maxHeight: '300px', overflowY: 'auto', overflowX: 'auto', border: '1px solid rgba(255,255,255,0.06)', borderRadius: '8px' }}>
                <table
                  style={{
                    width: '100%',
                    minWidth: '650px',
                    borderCollapse: 'collapse',
                    fontSize: '0.78rem',
                    fontFamily: 'monospace'
                  }}
                >
                  <thead>
                    <tr
                      style={{
                        background: 'rgba(30, 41, 59, 0.8)',
                        borderBottom: '1px solid rgba(255,255,255,0.1)',
                        textAlign: 'left',
                        color: '#94a3b8',
                        position: 'sticky',
                        top: 0,
                        zIndex: 2
                      }}
                    >
                      <th style={{ padding: '8px 10px' }}>Case ID</th>
                      <th style={{ padding: '8px 10px' }}>Activity</th>
                      <th style={{ padding: '8px 10px' }}>Queue Entry</th>
                      <th style={{ padding: '8px 10px' }}>Service Start</th>
                      <th style={{ padding: '8px 10px' }}>Wait (min)</th>
                      <th style={{ padding: '8px 10px' }}>Duration (min)</th>
                      <th style={{ padding: '8px 10px' }}>Cost ($)</th>
                      <th style={{ padding: '8px 10px' }}>Resource</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredEventLogs.slice(0, 100).map((log, idx) => (
                      <tr key={idx} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                        <td style={{ padding: '6px 10px', color: '#a5b4fc' }}>{log.caseId}</td>
                        <td style={{ padding: '6px 10px', color: '#f8fafc' }}>{log.activityName}</td>
                        <td style={{ padding: '6px 10px', color: '#94a3b8' }}>{log.queueEntryTime}m</td>
                        <td style={{ padding: '6px 10px', color: '#94a3b8' }}>{log.startTime}m</td>
                        <td
                          style={{
                            padding: '6px 10px',
                            color: log.waitTimeMinutes > 20 ? '#fb7185' : '#e2e8f0',
                            fontWeight: log.waitTimeMinutes > 20 ? 700 : 400
                          }}
                        >
                          {log.waitTimeMinutes}m
                        </td>
                        <td style={{ padding: '6px 10px', color: '#38bdf8' }}>{log.durationMinutes}m</td>
                        <td style={{ padding: '6px 10px', color: '#34d399' }}>${log.cost}</td>
                        <td style={{ padding: '6px 10px', color: '#c084fc' }}>{log.resource}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 5: AI DYNAMIC GRAPH */}
          {activeTab === 'aichart' && contextualChartSpec && (
            <div style={{ width: '100%', minWidth: 0 }}>
              <DynamicContextualChart chartSpec={contextualChartSpec} />
            </div>
          )}
        </div>
      )}
    </div>
  );
};
