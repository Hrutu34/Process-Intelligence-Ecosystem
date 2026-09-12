import React, { useState, useEffect, useRef } from 'react';
import type {
  ProcessKnowledgeDTO,
  CanonicalProcessGraph,
  ProcessQualityReportDTO,
  QualityIssueDTO,
} from '../../../backend/src/main/java/com/pie/shared/types/dto';
import { CORPUS_SAMPLES, type CorpusSample } from '../utils/bpmnCorpusSamples';
import { generateProcessNarrative, type ProcessNarrative } from '../services/bpmnNarrativeGenerator';
import { parseBpmnXmlClient } from '../utils/bpmnXmlParser';
import { canonicalGraphToBpmnXml } from '../utils/bpmnXmlGenerator';
import { BpmnIoCanvas } from './BpmnIoCanvas';
import { processService } from '../services/processService';
import { chatService } from '../services/chatService';
import './ProcessReviewAgent.css';

interface Props {
  knowledge: ProcessKnowledgeDTO | null;
  graph?: CanonicalProcessGraph | null;
  bpmnXml?: string | null;
  initialNarrative?: ProcessNarrative | null;
  onProcessUpdated?: (
    knowledge: ProcessKnowledgeDTO,
    graph: CanonicalProcessGraph,
    xml: string,
    narrative?: ProcessNarrative
  ) => void;
  onQualityReportUpdated?: (report: ProcessQualityReportDTO | null) => void;
  onProceedToBpmn?: () => void;
}

type SubTab = 'NARRATIVE' | 'DEFECTS' | 'DIAGRAM' | 'XML';

export const ProcessReviewAgent: React.FC<Props> = ({
  knowledge,
  graph,
  bpmnXml,
  initialNarrative,
  onProcessUpdated,
  onQualityReportUpdated,
}) => {
  const [activeSubTab, setActiveSubTab] = useState<SubTab>('NARRATIVE');
  const [narrative, setNarrative] = useState<ProcessNarrative | null>(initialNarrative || null);
  const [aiNarrativeMarkdown, setAiNarrativeMarkdown] = useState<string | null>(null);
  const [isGeneratingAiNarrative, setIsGeneratingAiNarrative] = useState<boolean>(false);
  const [qualityReport, setQualityReport] = useState<ProcessQualityReportDTO | null>(null);
  const [isGenerating, setIsGenerating] = useState<boolean>(false);
  const [selectedCorpusId, setSelectedCorpusId] = useState<string>('F01');
  const [copied, setCopied] = useState<boolean>(false);
  const [localGraph, setLocalGraph] = useState<CanonicalProcessGraph | null>(graph || null);
  const [localXml, setLocalXml] = useState<string>(bpmnXml || '');
  const [processTitle, setProcessTitle] = useState<string>(
    knowledge?.processName || 'Purchase Requisition Approval'
  );

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Sync external props if updated
  useEffect(() => {
    if (graph) setLocalGraph(graph);
    if (bpmnXml) setLocalXml(bpmnXml);
    if (knowledge?.processName) setProcessTitle(knowledge.processName);
  }, [graph, bpmnXml, knowledge]);

  // Propagate quality report changes to parent (chat copilot etc.)
  useEffect(() => {
    onQualityReportUpdated?.(qualityReport);
  }, [qualityReport, onQualityReportUpdated]);

  const handleGenerateAiNarrative = async () => {
    setIsGeneratingAiNarrative(true);
    try {
      const result = await chatService.generateNarrative({
        processName: processTitle,
        bpmnXml: localXml || undefined,
        knowledge,
        graph: localGraph,
        qualityReport,
      });
      if (result.markdown) {
        setAiNarrativeMarkdown(result.markdown);
      } else {
        setAiNarrativeMarkdown(`_Could not generate AI narrative: ${result.error || 'unknown error'}._`);
      }
    } catch (e) {
      setAiNarrativeMarkdown('_AI narrative unavailable. Make sure the backend and Ollama are running._');
    } finally {
      setIsGeneratingAiNarrative(false);
    }
  };

  const handleExportDefectsCsv = () => {
    if (!qualityReport || !qualityReport.issues || qualityReport.issues.length === 0) return;
    const rows: string[] = [];
    rows.push(['Rule ID', 'Severity', 'Element ID', 'Issue', 'Suggestion'].map(csvEscape).join(','));
    qualityReport.issues.forEach((iss: any) => {
      rows.push([
        iss.ruleId || '',
        iss.severity || '',
        iss.elementId || '',
        iss.issue || '',
        iss.suggestion || '',
      ].map(csvEscape).join(','));
    });
    const csv = rows.join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${processTitle || 'process'}-defects.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  // Run or re-run the Executive Summary generation pipeline
  const handleGenerateSummary = async (targetGraph?: CanonicalProcessGraph, title?: string, xml?: string) => {
    setIsGenerating(true);
    const activeGraph = targetGraph || localGraph;
    const activeTitle = title || processTitle;

    try {
      let resolvedGraph = activeGraph;
      let resolvedXml = xml || localXml;

      // If no graph exists yet, construct or parse from localXml or knowledge
      if (!resolvedGraph && resolvedXml) {
        const parsed = parseBpmnXmlClient(resolvedXml);
        resolvedGraph = parsed.graph;
        setLocalGraph(parsed.graph);
        if (!activeTitle) setProcessTitle(parsed.processName);
      } else if (!resolvedGraph && knowledge) {
        // Fallback: try fetching from backend /graph
        try {
          const res = await fetch('http://localhost:8080/api/v1/process/graph', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(knowledge),
          });
          if (res.ok) {
            resolvedGraph = await res.json();
            setLocalGraph(resolvedGraph);
          }
        } catch (e) {
          console.warn('Backend graph fetch fallback:', e);
        }
      }

      if (!resolvedXml && resolvedGraph) {
        resolvedXml = canonicalGraphToBpmnXml(resolvedGraph);
        setLocalXml(resolvedXml);
      }

      // Step 1: Capability 2 - Business Narrative Generation
      if (resolvedGraph) {
        const genNarrative = generateProcessNarrative(resolvedGraph, activeTitle);
        setNarrative(genNarrative);
      }

      // Step 2: Capability 3 - Defect & Quality Audit Validation
      try {
        if (resolvedGraph) {
          const valRes = await fetch('http://localhost:8080/api/v1/process/validate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(resolvedGraph),
          });
          if (valRes.ok) {
            const report: ProcessQualityReportDTO = await valRes.json();
            setQualityReport(report);
          }
        } else if (knowledge) {
          const valRes = await fetch('http://localhost:8080/api/v1/process/validate-knowledge', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(knowledge),
          });
          if (valRes.ok) {
            const report: ProcessQualityReportDTO = await valRes.json();
            setQualityReport(report);
          }
        }
      } catch (valErr) {
        console.warn('Backend quality validation offline, continuing with local results:', valErr);
      }
    } finally {
      setIsGenerating(false);
    }
  };

  // Initial load auto-generation if graph or knowledge is present
  useEffect(() => {
    if (!narrative && (localGraph || knowledge || localXml)) {
      handleGenerateSummary();
    }
  }, []);

  // Handle direct .bpmn / .xml file upload right inside Agent 04
  const handleBpmnFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = e.target.files;
    if (!fileList || fileList.length === 0) return;
    const file = fileList[0];

    setIsGenerating(true);
    setSelectedCorpusId('');

    try {
      const text = await file.text();
      setLocalXml(text);
      const friendlyName = file.name
        .replace(/\.bpmn$/i, '')
        .replace(/\.xml$/i, '')
        .replace(/^[A-Z0-9]+_/, '')
        .replace(/[-_]/g, ' ');
      setProcessTitle(friendlyName);

      // Ingest via processService or client parser
      let pGraph: CanonicalProcessGraph;
      let pKnowledge: ProcessKnowledgeDTO;

      try {
        const entity = await processService.importBpmnFile(file);
        pGraph = entity.graph;
        pKnowledge = entity.knowledge;
        setLocalGraph(pGraph);
        if (entity.narrative) setNarrative(entity.narrative);
      } catch (srvErr) {
        console.warn('processService import fallback to client parser:', srvErr);
        const parsed = parseBpmnXmlClient(text, file.name);
        pGraph = parsed.graph;
        pKnowledge = parsed.knowledge;
        setLocalGraph(pGraph);
      }

      if (onProcessUpdated) {
        onProcessUpdated(pKnowledge, pGraph, text);
      }

      await handleGenerateSummary(pGraph, friendlyName, text);
    } catch (uploadErr) {
      console.error('Failed to parse uploaded BPMN file:', uploadErr);
    } finally {
      setIsGenerating(false);
      e.target.value = '';
    }
  };

  // Handle benchmark corpus sample switch
  const handleSelectCorpusSample = async (sampleId: string) => {
    setSelectedCorpusId(sampleId);
    const sample = CORPUS_SAMPLES.find((s) => s.id === sampleId);
    if (!sample) return;

    setIsGenerating(true);
    const friendlyName = sample.name
      .replace(/\.bpmn$/i, '')
      .replace(/^[A-Z0-9]+_/, '')
      .replace(/[-_]/g, ' ');
    setProcessTitle(friendlyName);
    setLocalXml(sample.xml);

    try {
      let pGraph: CanonicalProcessGraph;
      let pKnowledge: ProcessKnowledgeDTO;

      try {
        const entity = await processService.importBpmnXml(sample.xml, sample.name);
        pGraph = entity.graph;
        pKnowledge = entity.knowledge;
        setLocalGraph(pGraph);
        if (entity.narrative) setNarrative(entity.narrative);
      } catch (err) {
        console.warn('BPMN XML import fallback to client parser:', err);
        const parsed = parseBpmnXmlClient(sample.xml, sample.name);
        pGraph = parsed.graph;
        pKnowledge = parsed.knowledge;
        setLocalGraph(pGraph);
      }

      if (onProcessUpdated) {
        onProcessUpdated(pKnowledge, pGraph, sample.xml);
      }

      await handleGenerateSummary(pGraph, friendlyName, sample.xml);
    } finally {
      setIsGenerating(false);
    }
  };

  // Copy Executive Summary to clipboard
  const handleCopySummary = () => {
    if (!narrative) return;
    navigator.clipboard.writeText(narrative.fullMarkdown);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Export as Markdown (.md)
  const handleDownloadMarkdown = () => {
    if (!narrative) return;
    const blob = new Blob([narrative.fullMarkdown], { type: 'text/markdown;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${processTitle.toLowerCase().replace(/\s+/g, '_')}_executive_summary.md`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const issues = qualityReport?.issues || [];
  const criticalCount = issues.filter((i: QualityIssueDTO) => i.severity === 'HIGH').length;
  const warningCount = issues.filter((i: QualityIssueDTO) => i.severity === 'MEDIUM').length;
  const infoCount = issues.filter((i: QualityIssueDTO) => i.severity === 'LOW').length;
  const score = qualityReport?.qualityScore ?? (issues.length > 0 ? Math.max(30, 100 - issues.length * 15) : 96);
  const scoreClass = score >= 80 ? 'good' : score >= 50 ? 'fair' : 'poor';

  return (
    <div className="pra-container">
      {/* Hidden File Input for BPMN upload */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".bpmn,.xml"
        style={{ display: 'none' }}
        onChange={handleBpmnFileUpload}
      />

      {/* Top Header & Control Card */}
      <div className="pra-header-card">
        <div className="pra-header-top">
          <div className="pra-title-area">
            <span className="pra-badge">AGENT 04 / TRANSLATION & REVIEW</span>
            <h2>{processTitle || 'Autonomous Process Review'}</h2>
            <p>
              Capability 2 (BPMN-to-Text Plain-Language Narrative) & Capability 3 (BPMN 2.0 Defect & Quality Audit
              Scorecard).
            </p>
          </div>

          <div className="pra-header-actions">
            <button
              type="button"
              className="pra-upload-btn"
              onClick={() => fileInputRef.current?.click()}
              title="Upload .bpmn or .xml file"
            >
              <span>📁</span>
              <span>Upload .BPMN File</span>
            </button>

            <button
              type="button"
              className="pra-generate-btn"
              onClick={() => handleGenerateSummary()}
              disabled={isGenerating}
            >
              <span>GENERATE EXECUTIVE SUMMARY</span>
              <span>↗</span>
            </button>
          </div>
        </div>

        {/* Benchmark Corpus Quick Toolbar */}
        <div className="pra-presets-strip">
          <span className="pra-presets-label">BENCHMARK CORPUS:</span>
          {CORPUS_SAMPLES.map((sample: CorpusSample) => (
            <button
              key={sample.id}
              type="button"
              className={`pra-preset-pill ${selectedCorpusId === sample.id ? 'active' : ''}`}
              onClick={() => handleSelectCorpusSample(sample.id)}
            >
              <span className="pra-pill-id">{sample.id}</span>
              <span>{sample.name.replace(/^[A-Z0-9]+_/, '').replace(/\.bpmn$/i, '').replace(/[-_]/g, ' ')}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Loading Animation during generation */}
      {isGenerating ? (
        <div className="pra-generating-card">
          <div className="pra-spinner" />
          <h3>Agent 04: Translating BPMN 2.0 XML...</h3>
          <p>
            Traced process topology, extracting swimlane actors, evaluating decision branch conditions, and running
            13 quality defect rules (D01–D13).
          </p>
        </div>
      ) : (
        <>
          {/* Sub Navigation Tabs */}
          <div className="pra-subtabs">
            <button
              type="button"
              className={`pra-subtab-btn ${activeSubTab === 'NARRATIVE' ? 'active' : ''}`}
              onClick={() => setActiveSubTab('NARRATIVE')}
            >
              <span>📋 Executive Narrative</span>
              <span className="pra-count-badge">Capability 2</span>
            </button>

            <button
              type="button"
              className={`pra-subtab-btn ${activeSubTab === 'DEFECTS' ? 'active' : ''}`}
              onClick={() => setActiveSubTab('DEFECTS')}
            >
              <span>🛡️ Defect Audit Report</span>
              <span className={`pra-count-badge ${issues.length > 0 ? 'alert' : ''}`}>
                {issues.length} {issues.length === 1 ? 'Defect' : 'Defects'}
              </span>
            </button>

            <button
              type="button"
              className={`pra-subtab-btn ${activeSubTab === 'DIAGRAM' ? 'active' : ''}`}
              onClick={() => setActiveSubTab('DIAGRAM')}
            >
              <span>⌘ Visual Diagram</span>
            </button>

            <button
              type="button"
              className={`pra-subtab-btn ${activeSubTab === 'XML' ? 'active' : ''}`}
              onClick={() => setActiveSubTab('XML')}
            >
              <span>&lt;/&gt; BPMN XML</span>
            </button>
          </div>

          {/* SubTab 1: Plain-Language Business Narrative (Capability 2) */}
          {activeSubTab === 'NARRATIVE' && (
            <div className="pra-narrative-view">
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 12 }}>
                <button
                  type="button"
                  className="btn-ghost"
                  onClick={handleGenerateAiNarrative}
                  disabled={isGeneratingAiNarrative || !localGraph}
                  title={localGraph ? 'Ask the local LLM to write a richer narrative' : 'Load a process first'}
                >
                  {isGeneratingAiNarrative ? '⏳ Generating AI narrative...' : '✦ Generate AI Narrative'}
                </button>
              </div>

              {aiNarrativeMarkdown && (
                <div className="pra-narrative-card" style={{ marginBottom: 20 }}>
                  <div className="pra-card-header">
                    <span className="pra-card-icon">✦</span>
                    <span>AI-Generated Business Narrative</span>
                  </div>
                  <div
                    className="pra-ai-narrative-body"
                    style={{ padding: 16, color: 'var(--soft-white)', fontSize: 13, lineHeight: 1.6 }}
                    dangerouslySetInnerHTML={{ __html: renderMarkdown(aiNarrativeMarkdown) }}
                  />
                </div>
              )}

              {narrative ? (
                <>
                  <div className="pra-summary-banner">
                    <h4>Executive Summary</h4>
                    <p>{narrative.executiveSummary}</p>
                  </div>

                  <div className="pra-narrative-grid">
                    {/* Process Initiation Card */}
                    <div className="pra-narrative-card">
                      <div className="pra-card-header">
                        <span className="pra-card-icon">⚡</span>
                        <span>Process Initiation & Trigger</span>
                      </div>
                      <p style={{ color: 'var(--soft-white)', fontSize: '13px', lineHeight: '1.5' }}>
                        {narrative.triggerNarrative}
                      </p>
                    </div>

                    {/* Process Outcomes Card */}
                    <div className="pra-narrative-card">
                      <div className="pra-card-header">
                        <span className="pra-card-icon">🎯</span>
                        <span>Termination & Outcomes</span>
                      </div>
                      <p style={{ color: 'var(--soft-white)', fontSize: '13px', lineHeight: '1.5' }}>
                        {narrative.outcomeNarrative}
                      </p>
                    </div>
                  </div>

                  {/* Sequential Steps Breakdown */}
                  <div className="pra-narrative-card">
                    <div className="pra-card-header">
                      <span className="pra-card-icon">🪜</span>
                      <span>Sequential Business Flow & Task Allocation</span>
                    </div>
                    <div className="pra-steps-list">
                      {narrative.flowSteps && narrative.flowSteps.length > 0 ? (
                        narrative.flowSteps.map((step, idx) => (
                          <div key={idx} className="pra-step-item">
                            <span className="pra-step-num">#{idx + 1}</span>
                            <span
                              dangerouslySetInnerHTML={{
                                __html: step
                                  .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                                  .replace(/\*(.*?)\*/g, '<em>$1</em>'),
                              }}
                            />
                          </div>
                        ))
                      ) : (
                        <p style={{ color: 'var(--dim)', fontSize: '13px' }}>No sequential tasks found.</p>
                      )}
                    </div>
                  </div>

                  {/* Decision Gateways */}
                  {narrative.decisionNarratives && narrative.decisionNarratives.length > 0 && (
                    <div className="pra-narrative-card">
                      <div className="pra-card-header">
                        <span className="pra-card-icon">🔀</span>
                        <span>Decision Points & Branching Logic (XOR Gateways)</span>
                      </div>
                      <div className="pra-steps-list">
                        {narrative.decisionNarratives.map((dec, idx) => (
                          <div
                            key={idx}
                            className="pra-decision-item"
                            dangerouslySetInnerHTML={{
                              __html: dec
                                .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                                .replace(/\*(.*?)\*/g, '<em>$1</em>')
                                .replace(/\\rightarrow/g, '→'),
                            }}
                          />
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Parallel Concurrency Tracks */}
                  {narrative.concurrencyNarratives && narrative.concurrencyNarratives.length > 0 && (
                    <div className="pra-narrative-card">
                      <div className="pra-card-header">
                        <span className="pra-card-icon">⚡</span>
                        <span>Concurrent Execution & Parallel Tracks (AND Gateways)</span>
                      </div>
                      <div className="pra-steps-list">
                        {narrative.concurrencyNarratives.map((con, idx) => (
                          <div
                            key={idx}
                            className="pra-concurrency-item"
                            dangerouslySetInnerHTML={{
                              __html: con
                                .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                                .replace(/\*(.*?)\*/g, '<em>$1</em>'),
                            }}
                          />
                        ))}
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <div className="pra-clean-state">
                  <h3>No Narrative Generated Yet</h3>
                  <p>Click "GENERATE EXECUTIVE SUMMARY" or upload a .bpmn file to translate the model.</p>
                </div>
              )}
            </div>
          )}

          {/* SubTab 2: Defect Audit Report (Capability 3) */}
          {activeSubTab === 'DEFECTS' && (
            <div className="pra-defects-view">
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
                <button
                  type="button"
                  className="btn-ghost"
                  onClick={handleExportDefectsCsv}
                  disabled={!qualityReport?.issues || qualityReport.issues.length === 0}
                  title="Download defect list as CSV"
                >
                  ⬇ Export Defects CSV
                </button>
              </div>
              {/* Scorecard Header */}
              <div className="pra-scorecard">
                <div className={`pra-score-gauge ${scoreClass}`}>
                  <span className="pra-score-val">{score}</span>
                  <span className="pra-score-label">Score / 100</span>
                </div>

                <div className="pra-scorecard-info">
                  <h3>BPMN 2.0 Quality Defect Audit</h3>
                  <p>
                    Automated inspection across 13 defect benchmarks: vague task names, orphan nodes, parallel
                    split/join mismatches, unlabeled decision gateways, duplicate tasks, missing swimlanes, and linear
                    complexity.
                  </p>
                </div>

                <div className="pra-defect-badges">
                  <div className="pra-count-pill critical">
                    <span className="pra-count-num">{criticalCount}</span>
                    <span className="pra-count-lbl">CRITICAL</span>
                  </div>
                  <div className="pra-count-pill warning">
                    <span className="pra-count-num">{warningCount}</span>
                    <span className="pra-count-lbl">WARNING</span>
                  </div>
                  <div className="pra-count-pill info">
                    <span className="pra-count-num">{infoCount}</span>
                    <span className="pra-count-lbl">INFO</span>
                  </div>
                </div>
              </div>

              {/* Defect Cards List */}
              <div className="pra-defects-list">
                {issues.length > 0 ? (
                  issues.map((issue: QualityIssueDTO, idx: number) => {
                    const sevClass =
                      issue.severity === 'HIGH' ? 'critical' : issue.severity === 'MEDIUM' ? 'warning' : 'info';
                    return (
                      <div key={idx} className={`pra-defect-card ${sevClass}`}>
                        <div className="pra-defect-header">
                          <span className="pra-defect-rule">
                            <span>RULE:</span>
                            <code>{issue.ruleId || 'BPMN_QUALITY_DEFECT'}</code>
                          </span>
                          <span
                            className="pra-badge"
                            style={{
                              borderColor:
                                issue.severity === 'HIGH'
                                  ? '#ef476f'
                                  : issue.severity === 'MEDIUM'
                                  ? '#ffd166'
                                  : 'var(--aqua)',
                              color:
                                issue.severity === 'HIGH'
                                  ? '#ef476f'
                                  : issue.severity === 'MEDIUM'
                                  ? '#ffd166'
                                  : 'var(--aqua)',
                            }}
                          >
                            {issue.severity}
                          </span>
                        </div>

                        <div className="pra-defect-title">
                          {issue.elementId ? `Element [${issue.elementId}]: ` : ''}
                          {issue.issue}
                        </div>

                        {issue.suggestion && (
                          <div className="pra-defect-fix">
                            <strong>💡 Recommended Action: </strong>
                            {issue.suggestion}
                          </div>
                        )}
                      </div>
                    );
                  })
                ) : (
                  <div className="pra-clean-state">
                    <span style={{ fontSize: '32px' }}>✅</span>
                    <h3>Zero BPMN 2.0 Defects Detected</h3>
                    <p>
                      This process diagram adheres to all BPMN 2.0 modelling standards with well-formed start/end
                      events, labeled decision gateways, and balanced concurrency.
                    </p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* SubTab 3: Visual BPMN Canvas */}
          {activeSubTab === 'DIAGRAM' && (
            <div className="pra-diagram-frame">
              {localGraph ? (
                <BpmnIoCanvas
                  graph={localGraph}
                  rawXml={localXml}
                  editable
                  onXmlChange={(xml) => setLocalXml(xml)}
                />
              ) : (
                <div style={{ padding: '40px', textAlign: 'center', color: '#666' }}>No BPMN Graph Available</div>
              )}
            </div>
          )}

          {/* SubTab 4: Technical BPMN 2.0 XML */}
          {activeSubTab === 'XML' && (
            <div className="pra-xml-container">
              <pre className="pra-xml-code">{localXml || canonicalGraphToBpmnXml(localGraph!)}</pre>
            </div>
          )}

          {/* Bottom Export & Utility Bar */}
          <div className="pra-export-bar">
            <div className="pra-export-group">
              <button type="button" className="pra-ghost-btn" onClick={handleCopySummary}>
                <span>{copied ? '✓ Copied!' : '📋 Copy Summary'}</span>
              </button>
              <button type="button" className="pra-ghost-btn" onClick={handleDownloadMarkdown}>
                <span>💾 Export Markdown (.md)</span>
              </button>
              <button type="button" className="pra-ghost-btn" onClick={() => window.print()}>
                <span>📄 Print / PDF</span>
              </button>
            </div>

            <div style={{ color: 'var(--dim)', fontFamily: 'var(--mono)', fontSize: '11px' }}>
              P.I.E. Autonomous Process Review Engine · BPMN 2.0 Compliant
            </div>
          </div>
        </>
      )}
    </div>
  );
};

function csvEscape(value: string): string {
  const v = value == null ? '' : String(value);
  if (v.includes(',') || v.includes('"') || v.includes('\n') || v.includes('\r')) {
    return `"${v.replace(/"/g, '""')}"`;
  }
  return v;
}

function renderMarkdown(md: string): string {
  const escapeHtml = (s: string) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  const lines = md.split(/\r?\n/);
  const out: string[] = [];
  let inList = false;
  let inOrdered = false;

  const closeList = () => {
    if (inList) {
      out.push('</ul>');
      inList = false;
    }
    if (inOrdered) {
      out.push('</ol>');
      inOrdered = false;
    }
  };

  for (const raw of lines) {
    const line = raw.trimEnd();

    if (/^##\s+/.test(line)) {
      closeList();
      out.push(`<h4>${escapeHtml(line.replace(/^##\s+/, ''))}</h4>`);
    } else if (/^#\s+/.test(line)) {
      closeList();
      out.push(`<h3>${escapeHtml(line.replace(/^#\s+/, ''))}</h3>`);
    } else if (/^\s*[-*]\s+/.test(line)) {
      if (!inList) {
        closeList();
        out.push('<ul>');
        inList = true;
      }
      out.push(`<li>${inlineFormat(escapeHtml(line.replace(/^\s*[-*]\s+/, '')))}</li>`);
    } else if (/^\s*\d+\.\s+/.test(line)) {
      if (!inOrdered) {
        closeList();
        out.push('<ol>');
        inOrdered = true;
      }
      out.push(`<li>${inlineFormat(escapeHtml(line.replace(/^\s*\d+\.\s+/, '')))}</li>`);
    } else if (line.trim() === '') {
      closeList();
    } else {
      closeList();
      out.push(`<p>${inlineFormat(escapeHtml(line))}</p>`);
    }
  }
  closeList();
  return out.join('\n');
}

function inlineFormat(html: string): string {
  return html
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>');
}
