import React, { useEffect, useState } from 'react';
import './AgentLoadingScreen.css';

interface Props {
  fileCount?: number;
  title?: string;
  mascot?: string;
  steps?: StepItem[];
}

interface StepItem {
  title: string;
  desc: string;
  weight: number;
}

const BASE_STEPS: StepItem[] = [
  { title: 'Ingestion', desc: 'Parsing document streams & reading raw text...', weight: 2500 },
  { title: 'Classification', desc: 'Agent 01: Classifying document type & calibrating confidence...', weight: 5000 },
  { title: 'Extraction', desc: 'Agent 01: Extracting activities, actors, systems & gateways...', weight: 9000 },
  { title: 'Conflict Audit', desc: 'Agent 01: Comparing files & isolating discrepancies...', weight: 8000 },
  { title: 'Normalization', desc: 'Pruning duplicates, validating JSON & sealing DTO graph...', weight: 4000 },
];

export const AgentLoadingScreen: React.FC<Props> = ({
  fileCount = 1,
  title = 'Knowledge Extraction Agent in Progress',
  mascot = '🧠',
  steps = BASE_STEPS,
}) => {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);

  // Scale step timeouts dynamically based on file count
  const multiplier = Math.max(1, fileCount * 0.85);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (currentStepIndex < steps.length - 1) {
      const duration = steps[currentStepIndex].weight * multiplier;
      timer = setTimeout(() => {
        setCurrentStepIndex((prev) => prev + 1);
      }, duration);
    }
    return () => clearTimeout(timer);
  }, [currentStepIndex, multiplier, steps]);

  return (
    <div className="agent-loading-card">
      <div className="loading-orbit">
        <div className="inner-pulse" />
        <span className="agent-mascot">{mascot}</span>
      </div>

      <div className="loading-header">
        <div className="status-pill status-pill-centered">
          <span className="status-dot pulsing" />
          PROCESSING {fileCount} DOCUMENT{fileCount > 1 ? 'S' : ''}
          <span className="status-separator">/</span>
          LOCAL LLM PIPELINE
        </div>
        <h3>{title}</h3>
        <p className="active-step-text">{steps[currentStepIndex].desc}</p>
      </div>

      {/* Connected Track with Validation Ticks */}
      <div className="stepper-container">
        <div className="stepper-track">
          {steps.map((step, idx) => {
            const isCompleted = idx < currentStepIndex;
            const isActive = idx === currentStepIndex;

            return (
              <React.Fragment key={idx}>
                <div className={`step-node ${isCompleted ? 'completed' : ''} ${isActive ? 'active' : ''}`}>
                  <div className="step-circle">
                    {isCompleted ? '✓' : `0${idx + 1}`}
                  </div>
                  <span className="step-label">{step.title}</span>
                </div>

                {idx < steps.length - 1 && (
                  <div className={`step-line ${idx < currentStepIndex ? 'filled' : ''}`} />
                )}
              </React.Fragment>
            );
          })}
        </div>
      </div>
    </div>
  );
};