import React from 'react';
import {
  Sliders,
  X,
  Clock,
  RotateCw
} from 'lucide-react';
import type { SimulationParameters, DurationDistribution } from '../../services/simulationTypes';

interface ParameterPanelProps {
  selectedNode: { id: string; name: string; type: string } | null;
  onClose: () => void;
  parameters: SimulationParameters;
  onChangeParameters: (newParams: SimulationParameters) => void;
  onResetParameters: () => void;
}

export const ParameterPanel: React.FC<ParameterPanelProps> = ({
  selectedNode,
  onClose,
  parameters,
  onChangeParameters,
  onResetParameters
}) => {
  const isTask = selectedNode && parameters.tasks?.[selectedNode.id];
  const isGateway = selectedNode && parameters.gateways?.[selectedNode.id];
  const taskParam = isTask ? parameters.tasks[selectedNode.id] : null;
  const gwParam = isGateway ? parameters.gateways[selectedNode.id] : null;
  const globalParam = parameters.global || {
    caseCount: 400,
    arrivalMeanMinutes: 20,
    slaThresholdHours: 12,
    arrivalDistribution: 'exponential' as const
  };

  const handleTaskChange = (field: string, value: any) => {
    if (!taskParam || !selectedNode) return;
    const updated: SimulationParameters = {
      ...parameters,
      tasks: {
        ...parameters.tasks,
        [selectedNode.id]: {
          ...taskParam,
          [field]: value
        }
      }
    };
    onChangeParameters(updated);
  };

  const handleGatewayChange = (branchIndex: number, newProb: number | string) => {
    if (!gwParam || !selectedNode) return;
    const branches = [...gwParam.branches];
    branches[branchIndex].probability = Number(newProb);

    if (branches.length > 1) {
      const remainingProb = Math.max(0, 1 - Number(newProb));
      const otherIndices = branches.map((_, i) => i).filter((i) => i !== branchIndex);
      const otherSum = otherIndices.reduce((sum, idx) => sum + (branches[idx].probability || 0), 0);

      if (otherSum > 0) {
        otherIndices.forEach((idx) => {
          branches[idx].probability = Number(
            ((branches[idx].probability / otherSum) * remainingProb).toFixed(2)
          );
        });
      } else {
        const perOther = remainingProb / otherIndices.length;
        otherIndices.forEach((idx) => {
          branches[idx].probability = Number(perOther.toFixed(2));
        });
      }
    }

    const updated: SimulationParameters = {
      ...parameters,
      gateways: {
        ...parameters.gateways,
        [selectedNode.id]: {
          ...gwParam,
          branches
        }
      }
    };
    onChangeParameters(updated);
  };

  const handleGlobalChange = (field: string, value: any) => {
    const updated: SimulationParameters = {
      ...parameters,
      global: {
        ...parameters.global,
        [field]: value
      }
    };
    onChangeParameters(updated);
  };

  return (
    <aside className="side-drawer">
      {/* Header */}
      <div className="drawer-header">
        <div className="drawer-title">
          <Sliders size={18} color="#818cf8" />
          <span>{selectedNode ? 'Entity Parameter Inspector' : 'Global Process Settings'}</span>
        </div>
        <div>
          {selectedNode && (
            <button className="toolbar-btn" onClick={onClose} title="Close entity inspector">
              <X size={16} />
            </button>
          )}
        </div>
      </div>

      {/* Body */}
      <div className="drawer-body">
        {/* CASE 1: TASK IS SELECTED */}
        {isTask && taskParam && selectedNode && (
          <>
            <div style={{ borderBottom: '1px solid rgba(255,255,255,0.06)', paddingBottom: '12px' }}>
              <div
                style={{
                  fontSize: '0.7rem',
                  color: '#94a3b8',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em'
                }}
              >
                Activity Node
              </div>
              <div style={{ fontSize: '1rem', fontWeight: 700, color: '#f8fafc', marginTop: '2px' }}>
                {taskParam.name || selectedNode.id}
              </div>
              <div style={{ fontSize: '0.72rem', color: '#64748b', fontFamily: 'monospace' }}>
                ID: {selectedNode.id}
              </div>
            </div>

            {/* Duration Distribution: Strictly Normal or Constant */}
            <div className="form-group">
              <label className="form-label">
                <span>Duration Model</span>
                <Clock size={14} color="#a5b4fc" />
              </label>
              <select
                className="form-select"
                value={taskParam.distribution === 'constant' ? 'constant' : 'normal'}
                onChange={(e) => {
                  const newDist = e.target.value as DurationDistribution;
                  const updated: SimulationParameters = {
                    ...parameters,
                    tasks: {
                      ...parameters.tasks,
                      [selectedNode.id]: {
                        ...taskParam,
                        distribution: newDist,
                        stdDev:
                          newDist === 'constant'
                            ? 0
                            : taskParam.stdDev || Math.max(1, Math.round(taskParam.meanDuration * 0.25))
                      }
                    }
                  };
                  onChangeParameters(updated);
                }}
              >
                <option value="normal">👤 Normal (Human Work — Varies around average)</option>
                <option value="constant">🤖 Constant (Automated — Exact same time every time)</option>
              </select>
            </div>

            {/* Mean / Exact Duration */}
            <div className="form-group">
              <label className="form-label">
                <span>
                  {taskParam.distribution === 'constant'
                    ? 'Exact Execution Time (Minutes)'
                    : 'Average Processing Time (Minutes)'}
                </span>
                <span className="slider-value">{taskParam.meanDuration} min</span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="1"
                  max="240"
                  step="1"
                  className="slider-input"
                  value={taskParam.meanDuration}
                  onChange={(e) => handleTaskChange('meanDuration', Number(e.target.value))}
                />
                <input
                  type="number"
                  min="1"
                  max="500"
                  className="form-input"
                  style={{ width: '70px', padding: '4px 8px' }}
                  value={taskParam.meanDuration}
                  onChange={(e) => handleTaskChange('meanDuration', Number(e.target.value))}
                />
              </div>
            </div>

            {/* Standard Deviation (Only for Normal) vs Constant Notice */}
            {taskParam.distribution !== 'constant' ? (
              <div className="form-group">
                <label className="form-label">
                  <span>Variation Around Average (± Std Dev)</span>
                  <span className="slider-value">±{taskParam.stdDev} min</span>
                </label>
                <div className="slider-container">
                  <input
                    type="range"
                    min="0"
                    max="60"
                    step="1"
                    className="slider-input"
                    value={taskParam.stdDev || 0}
                    onChange={(e) => handleTaskChange('stdDev', Number(e.target.value))}
                  />
                  <input
                    type="number"
                    min="0"
                    max="100"
                    className="form-input"
                    style={{ width: '70px', padding: '4px 8px' }}
                    value={taskParam.stdDev || 0}
                    onChange={(e) => handleTaskChange('stdDev', Number(e.target.value))}
                  />
                </div>
                <span style={{ fontSize: '0.7rem', color: '#64748b' }}>
                  💡 ~68% of transactions will finish between{' '}
                  {Math.max(1, taskParam.meanDuration - (taskParam.stdDev || 0))} and{' '}
                  {taskParam.meanDuration + (taskParam.stdDev || 0)} min.
                </span>
              </div>
            ) : (
              <div
                style={{
                  padding: '8px 12px',
                  background: 'rgba(6, 182, 212, 0.08)',
                  border: '1px solid rgba(6, 182, 212, 0.22)',
                  borderRadius: '8px',
                  fontSize: '0.74rem',
                  color: '#67e8f9',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  marginBottom: '12px'
                }}
              >
                <span>
                  ⚡ <strong>Fixed Machine Task</strong>: Zero variance. Always takes {taskParam.meanDuration} min.
                </span>
              </div>
            )}

            {/* Resource Capacity */}
            <div className="form-group">
              <label className="form-label">
                <span>Resource Headcount / Capacity</span>
                <span className="slider-value">{taskParam.resourceCapacity} agents</span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="1"
                  max="20"
                  step="1"
                  className="slider-input"
                  value={taskParam.resourceCapacity}
                  onChange={(e) => handleTaskChange('resourceCapacity', Number(e.target.value))}
                />
                <input
                  type="number"
                  min="1"
                  max="50"
                  className="form-input"
                  style={{ width: '70px', padding: '4px 8px' }}
                  value={taskParam.resourceCapacity}
                  onChange={(e) => handleTaskChange('resourceCapacity', Number(e.target.value))}
                />
              </div>
              <span style={{ fontSize: '0.7rem', color: '#64748b' }}>
                💡 When demand exceeds capacity, tokens wait in queue causing a bottleneck.
              </span>
            </div>

            {/* Hourly Labor Rate */}
            <div className="form-group">
              <label className="form-label">
                <span>Hourly Labor Cost ($/hour)</span>
                <span className="slider-value">${taskParam.hourlyLaborRate}/hr</span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="0"
                  max="250"
                  step="5"
                  className="slider-input"
                  value={taskParam.hourlyLaborRate || 0}
                  onChange={(e) => handleTaskChange('hourlyLaborRate', Number(e.target.value))}
                />
                <input
                  type="number"
                  min="0"
                  max="500"
                  className="form-input"
                  style={{ width: '70px', padding: '4px 8px' }}
                  value={taskParam.hourlyLaborRate || 0}
                  onChange={(e) => handleTaskChange('hourlyLaborRate', Number(e.target.value))}
                />
              </div>
            </div>

            {/* Fixed Cost per Execution */}
            <div className="form-group">
              <label className="form-label">
                <span>Fixed Cost per Case ($)</span>
                <span className="slider-value">${taskParam.costPerExecution}</span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="0"
                  max="150"
                  step="1"
                  className="slider-input"
                  value={taskParam.costPerExecution || 0}
                  onChange={(e) => handleTaskChange('costPerExecution', Number(e.target.value))}
                />
                <input
                  type="number"
                  min="0"
                  max="1000"
                  className="form-input"
                  style={{ width: '70px', padding: '4px 8px' }}
                  value={taskParam.costPerExecution || 0}
                  onChange={(e) => handleTaskChange('costPerExecution', Number(e.target.value))}
                />
              </div>
            </div>

            {/* Error / Rework Rate */}
            <div className="form-group">
              <label className="form-label">
                <span>Rework / Loop Probability (%)</span>
                <span className="slider-value">{Math.round((taskParam.reworkRate || 0) * 100)}%</span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="0"
                  max="0.5"
                  step="0.01"
                  className="slider-input"
                  value={taskParam.reworkRate || 0}
                  onChange={(e) => handleTaskChange('reworkRate', Number(e.target.value))}
                />
              </div>
            </div>
          </>
        )}

        {/* CASE 2: EXCLUSIVE GATEWAY IS SELECTED */}
        {isGateway && gwParam && selectedNode && (
          <>
            <div style={{ borderBottom: '1px solid rgba(255,255,255,0.06)', paddingBottom: '12px' }}>
              <div
                style={{
                  fontSize: '0.7rem',
                  color: '#94a3b8',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em'
                }}
              >
                XOR Decision Gateway
              </div>
              <div style={{ fontSize: '1rem', fontWeight: 700, color: '#f8fafc', marginTop: '2px' }}>
                {gwParam.name || selectedNode.id}
              </div>
              <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
                Configure branch routing probabilities (must total 100%)
              </div>
            </div>

            {gwParam.branches.map((branch, idx) => (
              <div className="form-group" key={branch.flowId}>
                <label className="form-label">
                  <span
                    style={{
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      maxWidth: '200px'
                    }}
                  >
                    Branch: {branch.name || branch.flowId}
                  </span>
                  <span className="slider-value">{Math.round(branch.probability * 100)}%</span>
                </label>
                <div className="slider-container">
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.01"
                    className="slider-input"
                    value={branch.probability}
                    onChange={(e) => handleGatewayChange(idx, e.target.value)}
                  />
                </div>
              </div>
            ))}
          </>
        )}

        {/* CASE 3: NO NODE SELECTED - GLOBAL SIMULATION SETTINGS */}
        {!selectedNode && (
          <>
            <div style={{ borderBottom: '1px solid rgba(255,255,255,0.06)', paddingBottom: '12px' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: '4px'
                }}
              >
                <div
                  style={{
                    fontSize: '0.7rem',
                    color: '#94a3b8',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em'
                  }}
                >
                  Process Environment
                </div>
                {globalParam.domainName && (
                  <span
                    style={{
                      fontSize: '0.68rem',
                      padding: '2px 8px',
                      borderRadius: '10px',
                      background: 'rgba(99, 102, 241, 0.18)',
                      color: '#c7d2fe',
                      border: '1px solid rgba(99, 102, 241, 0.3)',
                      fontWeight: 600,
                      maxWidth: '180px',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap'
                    }}
                    title={globalParam.domainName}
                  >
                    {globalParam.domainName}
                  </span>
                )}
              </div>
              <div style={{ fontSize: '1rem', fontWeight: 700, color: '#f8fafc' }}>
                Global Process Settings
              </div>
              <div style={{ fontSize: '0.74rem', color: '#94a3b8', marginTop: '2px' }}>
                Simulate demand volume and arrival rates for{' '}
                <strong>{globalParam.entityName || 'cases'}</strong>
              </div>
            </div>

            {/* Total Cases */}
            <div className="form-group">
              <label className="form-label">
                <span>{globalParam.caseCountLabel || 'Number of Process Cases (Instances)'}</span>
                <span className="slider-value">
                  {globalParam.caseCount} {globalParam.entityName || 'cases'}
                </span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="50"
                  max="1500"
                  step="50"
                  className="slider-input"
                  value={globalParam.caseCount}
                  onChange={(e) => handleGlobalChange('caseCount', Number(e.target.value))}
                />
                <input
                  type="number"
                  min="10"
                  max="5000"
                  className="form-input"
                  style={{ width: '70px', padding: '4px 8px' }}
                  value={globalParam.caseCount}
                  onChange={(e) => handleGlobalChange('caseCount', Number(e.target.value))}
                />
              </div>
            </div>

            {/* Inter-arrival Rate */}
            <div className="form-group">
              <label className="form-label">
                <span>{globalParam.arrivalLabel || 'Mean Inter-Arrival Time (Poisson)'}</span>
                <span className="slider-value">{globalParam.arrivalMeanMinutes} min</span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="1"
                  max="120"
                  step="1"
                  className="slider-input"
                  value={globalParam.arrivalMeanMinutes}
                  onChange={(e) => handleGlobalChange('arrivalMeanMinutes', Number(e.target.value))}
                />
                <input
                  type="number"
                  min="1"
                  max="300"
                  className="form-input"
                  style={{ width: '70px', padding: '4px 8px' }}
                  value={globalParam.arrivalMeanMinutes}
                  onChange={(e) => handleGlobalChange('arrivalMeanMinutes', Number(e.target.value))}
                />
              </div>
              <span style={{ fontSize: '0.7rem', color: '#64748b' }}>
                {globalParam.arrivalHelpText ||
                  '💡 Shorter interval means higher arrival volume and increased stress on task resources.'}
              </span>
            </div>

            {/* SLA Target Limit */}
            <div className="form-group">
              <label className="form-label">
                <span>{globalParam.slaLabel || 'SLA Target Limit (Hours)'}</span>
                <span className="slider-value">{globalParam.slaThresholdHours} hrs</span>
              </label>
              <div className="slider-container">
                <input
                  type="range"
                  min="0.5"
                  max="96"
                  step="0.5"
                  className="slider-input"
                  value={globalParam.slaThresholdHours}
                  onChange={(e) => handleGlobalChange('slaThresholdHours', Number(e.target.value))}
                />
                <input
                  type="number"
                  min="0.5"
                  max="200"
                  step="0.5"
                  className="form-input"
                  style={{ width: '70px', padding: '4px 8px' }}
                  value={globalParam.slaThresholdHours}
                  onChange={(e) => handleGlobalChange('slaThresholdHours', Number(e.target.value))}
                />
              </div>
              <span style={{ fontSize: '0.7rem', color: '#64748b' }}>
                {globalParam.slaHelpText ||
                  '💡 Cases taking longer than this limit are flagged as SLA breaches.'}
              </span>
            </div>
          </>
        )}

        {/* Reset button */}
        <div
          style={{
            marginTop: 'auto',
            paddingTop: '16px',
            borderTop: '1px solid rgba(255,255,255,0.06)'
          }}
        >
          <button
            className="btn btn-secondary"
            style={{ width: '100%' }}
            onClick={onResetParameters}
            title="Reset parameters to smart defaults"
          >
            <RotateCw size={14} />
            <span>Reset to Defaults</span>
          </button>
        </div>
      </div>
    </aside>
  );
};
