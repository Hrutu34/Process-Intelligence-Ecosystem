import React from 'react';
import './AgentExecutionTracker.css';

export interface TrackerState {
  currentStage: string;
  completedStages: string[];
  failedStage: string | null;
  statusMessage: string;
  progressPercentage: number;
}

interface Props {
  state: TrackerState;
  onRetry?: () => void;
}

const STAGES = [
  { id: 'INPUT_RECEIVED', label: '1. Reading input', agent: 'Ingestion Agent' },
  { id: 'CLASSIFYING', label: '2. Classifying document', agent: 'Ingestion Agent' },
  { id: 'KNOWLEDGE_EXTRACTION', label: '3. Extracting process knowledge', agent: 'Knowledge Extraction Agent' },
  { id: 'BUILD_GRAPH', label: '4. Building process graph', agent: 'Knowledge Extraction Agent' },
  { id: 'PROCESS_INTELLIGENCE', label: '5. Detecting issues', agent: 'Process Intelligence Agent' },
  { id: 'BPMN_MODELLING', label: '6. Generating BPMN', agent: 'BPMN Modelling Agent' },
  { id: 'PROCESS_REVIEW', label: '7. Reviewing BPMN', agent: 'Process Review Agent' },
  { id: 'FINAL_OUTPUT', label: '8. Preparing final output', agent: 'Orchestrator' },
];

export const AgentExecutionTracker: React.FC<Props> = ({ state, onRetry }) => {
  const isFailed = !!state.failedStage;
  const isSuccess = state.currentStage === 'FINAL_OUTPUT' && state.completedStages.includes('FINAL_OUTPUT');

  const currentStageIndex = STAGES.findIndex(s => s.id === state.currentStage);
  const activeAgent = STAGES.find(s => s.id === state.currentStage)?.agent || 'Orchestrator';

  return (
    <div className="agent-execution-tracker">
      <div className="tracker-layout">
        {/* Left Side: Stepper */}
        <div className="tracker-stepper">
          <h4 className="stepper-title">Agentic Workflow</h4>
          <div className="stepper-list">
            {STAGES.map((stage, idx) => {
              const isCompleted = state.completedStages.includes(stage.id) || idx < currentStageIndex;
              const isActive = state.currentStage === stage.id && !isFailed && !isSuccess;
              const hasFailed = state.failedStage === stage.id;

              return (
                <div key={stage.id} className={`stepper-node ${isCompleted ? 'completed' : ''} ${isActive ? 'active' : ''} ${hasFailed ? 'failed' : ''}`}>
                  <div className="stepper-icon">
                    {isCompleted ? '✓' : isActive ? '⏳' : hasFailed ? '✖' : ' '}
                  </div>
                  <span className="stepper-label">{stage.label}</span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Side: Status Card */}
        <div className="tracker-status-card">
          <div className="status-header">
            <span className="pulse-dot" style={{ background: isFailed ? 'var(--coral)' : isSuccess ? 'var(--aqua)' : 'var(--yellow)' }}></span>
            <span className="active-agent-name">{isFailed ? 'EXECUTION FAILED' : isSuccess ? 'PIPELINE COMPLETE' : activeAgent.toUpperCase()}</span>
          </div>

          <div className="status-body">
            {isFailed ? (
              <div className="error-state">
                <p>{state.statusMessage}</p>
                {onRetry && (
                  <button className="yellow-button" onClick={onRetry}>
                    RETRY PROCESS
                  </button>
                )}
              </div>
            ) : isSuccess ? (
              <div className="success-state">
                <div className="success-icon">✓</div>
                <p>Process extracted and modelled successfully.</p>
              </div>
            ) : (
              <div className="loading-state">
                <div className="orbit-spinner">
                  <div className="orbit-core"></div>
                  <div className="orbit-ring"></div>
                </div>
                <div className="logs-window">
                  <code>&gt; {state.statusMessage}</code>
                  <div className="progress-bar-container">
                    <div className="progress-bar" style={{ width: `${state.progressPercentage}%` }}></div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

