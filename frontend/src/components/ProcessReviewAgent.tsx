import React, { useState } from 'react';
import './ProcessReviewAgent.css';

import { AgentLoadingScreen } from './AgentLoadingScreen';

interface Props {
  bpmnXml: string | null;
}

interface ReviewReport {
  summary: string;
  issues: string[];
  recommendations: string[];
}

export const ProcessReviewAgent: React.FC<Props> = ({ bpmnXml }) => {
  const [report, setReport] = useState<ReviewReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasFetched = React.useRef(false);

  React.useEffect(() => {
    if (bpmnXml && !report && !loading && !error && !hasFetched.current) {
      hasFetched.current = true;
      handleGenerateSummary();
    }
  }, [bpmnXml]);

  const handleGenerateSummary = async () => {
    if (!bpmnXml) {
      setError("No BPMN XML available. Please generate or import a BPMN diagram first.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await fetch('http://localhost:8080/api/v1/process/review/summary', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: bpmnXml }),
      });

      if (!response.ok) {
        throw new Error(`Failed to generate review: ${response.statusText}`);
      }

      const data = await response.json();
      setReport(data);
    } catch (err: any) {
      setError(err.message || "An error occurred during review generation.");
    } finally {
      setLoading(false);
    }
  };



  if (loading) {
    return (
      <AgentLoadingScreen
        title="Process Review Agent"
        mascot="👀"
        steps={[
          { title: 'Parsing BPMN', desc: 'Analyzing diagram structure and gateways...', weight: 2000 },
          { title: 'Semantic Translation', desc: 'Translating BPMN nodes to business language...', weight: 4000 },
          { title: 'Synthesizing Summary', desc: 'Generating detailed executive overview...', weight: 3000 },
        ]}
      />
    );
  }

  return (
    <div className="process-review-agent">
      {!report && (
        <div className="agent-placeholder-card">
          <span className="agent-icon">👀</span>
          <h3>Agent 04: Process Review & Translation Agent</h3>
          <p>
            Translates technical BPMN XML back into clear executive summaries and audit reports.
          </p>
          {error && (
            <div style={{ marginTop: '20px' }}>
              <p className="error-text">{error}</p>
              <button className="yellow-button" onClick={() => { hasFetched.current = false; handleGenerateSummary(); }}>
                RETRY GENERATION
              </button>
            </div>
          )}
        </div>
      )}

      {report && (
        <div className="review-report-card">
          <div className="report-header">
            <h3>Executive Process Summary</h3>
            <button className="btn-ghost" onClick={() => setReport(null)}>
              Reset
            </button>
          </div>
          <div className="report-content">
            <div className="summary-section">
              <h4>Process Summary</h4>
              <p>{report.summary}</p>
            </div>
            {report.issues && report.issues.length > 0 && (
              <div className="issues-section">
                <h4>Potential Issues</h4>
                <ul>
                  {report.issues.map((issue, idx) => (
                    <li key={idx}>{issue}</li>
                  ))}
                </ul>
              </div>
            )}
            {report.recommendations && report.recommendations.length > 0 && (
              <div className="recommendations-section">
                <h4>Recommendations</h4>
                <ul>
                  {report.recommendations.map((rec, idx) => (
                    <li key={idx}>{rec}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

