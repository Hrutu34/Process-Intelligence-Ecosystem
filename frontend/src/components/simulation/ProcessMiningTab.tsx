import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Play,
  BarChart3,
  Download,
  Upload,
  Layers,
  ChevronDown
} from 'lucide-react';
import { parseBpmnXml, generateDefaultParameters } from '../../services/bpmnParser';
import { runDiscreteEventSimulation } from '../../services/simulationEngine';
import { exportEventLogToCsv, IntegrationBridge } from '../../services/integrationBridge';
import { generateContextualChart } from '../../services/llmService';
import { CORPUS_MODELS } from '../../data/corpusData';
import type {
  ProcessTopology,
  SimulationParameters,
  SimulationResult,
  DynamicChartSpec
} from '../../services/simulationTypes';
import { BpmnViewer } from './BpmnViewer';
import { ParameterPanel } from './ParameterPanel';
import { AnalyticsDashboard } from './AnalyticsDashboard';
import './simulation.css';

interface ProcessMiningTabProps {
  bpmnXml?: string | null;
  userContext?: string;
  processName?: string;
}

export const ProcessMiningTab: React.FC<ProcessMiningTabProps> = ({
  bpmnXml,
  userContext = '',
  processName = 'Current Process'
}) => {
  const [selectedModelId, setSelectedModelId] = useState<string>('current');
  const [currentXml, setCurrentXml] = useState<string>('');
  const [activeProcessTitle, setActiveProcessTitle] = useState<string>(processName);

  const [parsedBpmn, setParsedBpmn] = useState<ProcessTopology | null>(null);
  const [parameters, setParameters] = useState<SimulationParameters | null>(null);
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null);
  const [contextualChartSpec, setContextualChartSpec] = useState<DynamicChartSpec | null>(null);

  const [selectedNode, setSelectedNode] = useState<{ id: string; name: string; type: string } | null>(null);
  const [isSimulating, setIsSimulating] = useState<boolean>(false);
  const [showGraphs, setShowGraphs] = useState<boolean>(false);
  const [isDashboardMinimized, setIsDashboardMinimized] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const graphsRef = useRef<HTMLDivElement | null>(null);
  const simulationResultRef = useRef(simulationResult);
  simulationResultRef.current = simulationResult;

  // Ingest XML and initialize simulation
  const ingestXml = useCallback(
    (xml: string, title: string, customParams: SimulationParameters | null = null) => {
      try {
        const parsed = parseBpmnXml(xml);
        const defaults = customParams || generateDefaultParameters(parsed);

        setCurrentXml(xml);
        setActiveProcessTitle(title);
        setParsedBpmn(parsed);
        setParameters(defaults);
        setSelectedNode(null);

        const initialResult = runDiscreteEventSimulation(parsed, defaults);
        setSimulationResult(initialResult);

        // Generate baseline contextual chart
        generateContextualChart(initialResult, userContext).then(setContextualChartSpec);
      } catch (err) {
        console.error('Error ingesting BPMN XML:', err);
      }
    },
    [userContext]
  );

  // Sync upstream BPMN XML if passed
  useEffect(() => {
    if (bpmnXml && bpmnXml.trim().length > 0) {
      setSelectedModelId('current');
      ingestXml(bpmnXml, processName || 'Ecosystem BPMN Model');
    } else {
      // Fallback to first corpus model if no upstream XML is available
      const defaultModel = CORPUS_MODELS[0];
      if (defaultModel) {
        setSelectedModelId(defaultModel.id);
        ingestXml(defaultModel.xml, defaultModel.name);
      }
    }
  }, [bpmnXml, processName, ingestXml]);

  // Load a model from benchmark corpus
  const handleSelectModel = (modelId: string) => {
    if (modelId === 'current' && bpmnXml) {
      setSelectedModelId('current');
      ingestXml(bpmnXml, processName || 'Ecosystem BPMN Model');
      return;
    }

    const found = CORPUS_MODELS.find((m) => m.id === modelId);
    if (found) {
      setSelectedModelId(found.id);
      ingestXml(found.xml, found.name);
    }
  };

  // Run simulation with current parameters
  const handleRunSimulation = useCallback(
    (paramsToUse: SimulationParameters | null = null) => {
      const p = paramsToUse || parameters;
      if (!parsedBpmn || !p) return;
      setIsSimulating(true);

      setTimeout(() => {
        try {
          const result = runDiscreteEventSimulation(parsedBpmn, p);
          setSimulationResult(result);

          // Update dynamic contextual chart
          generateContextualChart(result, userContext).then(setContextualChartSpec);

          if (window.ProcessIQ_Bridge) {
            window.ProcessIQ_Bridge.notifySimulationComplete(result);
          }
        } catch (err: any) {
          console.error('Simulation execution error:', err);
          alert('Simulation error: ' + (err?.message || err));
        } finally {
          setIsSimulating(false);
        }
      }, 120);
    },
    [parsedBpmn, parameters, userContext]
  );

  // Reset to domain defaults
  const handleResetParameters = () => {
    if (!parsedBpmn) return;
    const defaults = generateDefaultParameters(parsedBpmn);
    setParameters(defaults);
    handleRunSimulation(defaults);
  };

  // Export event log CSV
  const handleExportCsv = () => {
    if (simulationResult && simulationResult.eventLog) {
      exportEventLogToCsv(
        simulationResult.eventLog,
        `${activeProcessTitle.replace(/\s+/g, '_')}_event_log.csv`
      );
    }
  };

  // File upload handler
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const xml = event.target?.result as string;
      if (xml) {
        setSelectedModelId('uploaded');
        ingestXml(xml, file.name.replace(/\.[^/.]+$/, ''));
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  // Setup integration bridge
  useEffect(() => {
    const bridge = new IntegrationBridge({
      onLoadBpmn: (xml, name, customParams) => ingestXml(xml, name || 'Imported Process', customParams),
      onSetParameters: (p) => setParameters(p),
      onRunSimulation: () => handleRunSimulation(),
      getResults: () => simulationResultRef.current,
      exportCsv: () => handleExportCsv()
    });
    window.ProcessIQ_Bridge = bridge;
  }, [ingestXml, handleRunSimulation]);

  return (
    <div className="simulation-tab-container">
      {/* Simulation Action Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 18px',
          background: 'rgba(15, 23, 42, 0.95)',
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
          gap: '12px',
          flexWrap: 'wrap'
        }}
      >
        {/* Left: Model Selector & Ingestion */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Layers size={16} color="#4ade80" />
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#eafff0' }}>Model:</span>
          </div>

          <div style={{ position: 'relative' }}>
            <select
              className="corpus-select"
              value={selectedModelId}
              onChange={(e) => handleSelectModel(e.target.value)}
              style={{
                background: 'rgba(30, 41, 59, 0.8)',
                color: '#eafff0',
                border: '1px solid rgba(255, 255, 255, 0.12)',
                borderRadius: '8px',
                padding: '6px 28px 6px 12px',
                fontSize: '0.82rem',
                cursor: 'pointer',
                appearance: 'none',
                minWidth: '220px'
              }}
            >
              {bpmnXml && <option value="current">Current Ecosystem Diagram</option>}
              <optgroup label="Enterprise Benchmarks (10 Models)">
                {CORPUS_MODELS.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({m.category})
                  </option>
                ))}
              </optgroup>
              {selectedModelId === 'uploaded' && <option value="uploaded">Uploaded File</option>}
            </select>
            <ChevronDown
              size={14}
              color="#9fc2ac"
              style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }}
            />
          </div>

          <input
            type="file"
            accept=".bpmn,.xml"
            ref={fileInputRef}
            onChange={handleFileUpload}
            style={{ display: 'none' }}
          />
          <button
            className="btn btn-secondary"
            onClick={() => fileInputRef.current?.click()}
            style={{ padding: '6px 12px', fontSize: '0.78rem' }}
            title="Import custom .bpmn or .xml diagram file"
          >
            <Upload size={14} />
            <span>Upload BPMN</span>
          </button>
        </div>

        {/* Right: Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {/* Toggle Graphs & Analytics Button */}
          <button
            className={showGraphs ? 'btn btn-primary' : 'btn btn-secondary'}
            onClick={() => {
              const next = !showGraphs;
              setShowGraphs(next);
              if (next) {
                setTimeout(() => {
                  graphsRef.current?.scrollIntoView({ behavior: 'smooth' });
                }, 100);
              }
            }}
            style={{
              padding: '6px 14px',
              fontSize: '0.8rem',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '7px',
              background: showGraphs ? 'linear-gradient(135deg, #15803d 0%, #166534 100%)' : undefined,
              borderColor: showGraphs ? 'rgba(34, 197, 94, 0.6)' : undefined
            }}
            title="Toggle bottleneck distribution, queue metrics, and event log below"
          >
            <BarChart3 size={15} color={showGraphs ? '#ffffff' : '#4ade80'} />
            <span>{showGraphs ? 'Hide Graphs & Analytics' : 'Show Graphs & Analytics'}</span>
            {simulationResult && (
              <span
                style={{
                  fontSize: '0.7rem',
                  padding: '2px 7px',
                  borderRadius: '10px',
                  background: showGraphs ? 'rgba(255, 255, 255, 0.22)' : 'rgba(34, 197, 94, 0.25)',
                  color: showGraphs ? '#ffffff' : '#bbf7d0',
                  fontWeight: 700
                }}
              >
                {simulationResult.summary.avgCycleTimeHours}h avg
              </span>
            )}
          </button>

          {/* Export CSV */}
          <button
            className="btn btn-secondary"
            onClick={handleExportCsv}
            disabled={!simulationResult}
            style={{ padding: '6px 12px', fontSize: '0.8rem' }}
            title="Export event logs formatted for Celonis, Disco, or PM4Py"
          >
            <Download size={14} />
            <span>Export CSV</span>
          </button>

          {/* Run Simulation CTA */}
          <button
            className="btn btn-primary"
            onClick={() => handleRunSimulation()}
            disabled={isSimulating}
            style={{
              padding: '7px 18px',
              fontSize: '0.85rem',
              fontWeight: 700,
              background: 'linear-gradient(135deg, #22c55e 0%, #15803d 100%)',
              boxShadow: '0 0 15px rgba(34, 197, 94, 0.35)',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}
          >
            <Play size={14} fill="currentColor" />
            <span>{isSimulating ? 'Simulating...' : 'Run Simulation'}</span>
          </button>
        </div>
      </div>

      {/* Main Workspace (BPMN Canvas + Parameter Panel) */}
      <div className="main-workspace">
        <BpmnViewer
          xml={currentXml}
          processName={activeProcessTitle}
          onSelectNode={setSelectedNode}
          selectedNodeId={selectedNode?.id}
          simulationResult={simulationResult}
          parameters={parameters}
          parsedBpmn={parsedBpmn}
        />

        <ParameterPanel
          selectedNode={selectedNode}
          onClose={() => setSelectedNode(null)}
          parameters={parameters || ({ global: {}, tasks: {}, gateways: {} } as any)}
          onChangeParameters={(newParams) => {
            setParameters(newParams);
          }}
          onResetParameters={handleResetParameters}
        />
      </div>

      {/* Analytics & Event Logs Section (Rendered Below BPMN View on Toggle) */}
      {showGraphs && (
        <div ref={graphsRef} style={{ width: '100%', marginTop: '20px' }}>
          <AnalyticsDashboard
            simulationResult={simulationResult}
            isMinimized={isDashboardMinimized}
            onToggleMinimize={() => setIsDashboardMinimized(!isDashboardMinimized)}
            onClose={() => setShowGraphs(false)}
            onExportCsv={handleExportCsv}
            contextualChartSpec={contextualChartSpec}
          />
        </div>
      )}
    </div>
  );
};
