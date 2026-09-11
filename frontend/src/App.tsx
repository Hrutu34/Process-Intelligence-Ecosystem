import "./App.css";
import ProcessEntry from "./components/ProcessEntry";
import { ProcessKnowledgeReview } from "./components/ProcessKnowledgeReview";
import { ProcessIntelligenceAgent } from "./components/ProcessIntelligenceAgent";
import { ProcessGraphViewer } from "./components/ProcessGraphViewer";
import { ProcessReviewAgent } from "./components/ProcessReviewAgent";
import { AgentLoadingScreen } from "./components/AgentLoadingScreen";
import type {
  CanonicalProcessGraph,
  ProcessKnowledgeDTO,
} from "../../backend/src/main/java/com/pie/shared/types/dto";
import type { ProcessNarrative } from "./services/bpmnNarrativeGenerator";
import { CORPUS_SAMPLES } from "./utils/bpmnCorpusSamples";
import { processService } from "./services/processService";
import { parseBpmnXmlClient } from "./utils/bpmnXmlParser";
import { useRef, useState } from "react";

type AgentTab = "01_KNOWLEDGE" | "02_INTELLIGENCE" | "03_BPMN" | "04_REVIEW";

function App() {
  const processEntryRef = useRef<HTMLDivElement | null>(null);
  const [extractedData, setExtractedData] = useState<ProcessKnowledgeDTO | null>(null);
  const [bpmnXml, setBpmnXml] = useState<string | null>(null);
  const [processGraph, setProcessGraph] = useState<CanonicalProcessGraph | null>(null);
  const [processNarrative, setProcessNarrative] = useState<ProcessNarrative | null>(null);
  const [isExtracting, setIsExtracting] = useState<boolean>(false);
  const [activeFileCount, setActiveFileCount] = useState<number>(1);
  const [activeTab, setActiveTab] = useState<AgentTab>("01_KNOWLEDGE");

  const agents = [
    {
      number: "01",
      icon: "🧠",
      title: "Knowledge Extraction",
      description: "Turns messy documents, policies, and tribal knowledge into structured process intelligence.",
      accent: "aqua",
    },
    {
      number: "02",
      icon: "◉",
      title: "Process Intelligence",
      description: "Finds gaps, dead ends, missing owners, contradictions, and hidden process risks.",
      accent: "yellow",
    },
    {
      number: "03",
      icon: "⌘",
      title: "BPMN Modelling",
      description: "Transforms validated process knowledge into clean, editable BPMN 2.0 models.",
      accent: "aqua",
    },
    {
      number: "04",
      icon: "✦",
      title: "Process Review",
      description: "Translates technical BPMN XML back into clear executive summaries and audit reports.",
      accent: "yellow",
    },
  ];

  const handleStartExtraction = (fileCount: number) => {
    setActiveFileCount(fileCount);
    setIsExtracting(true);
  };

  const handleExtractionSuccess = (
    data: ProcessKnowledgeDTO,
    xml?: string,
    graph?: CanonicalProcessGraph,
    narrative?: ProcessNarrative,
    directTab?: AgentTab
  ) => {
    setExtractedData(data);
    if (xml) setBpmnXml(xml);
    if (graph) setProcessGraph(graph);
    if (narrative) setProcessNarrative(narrative);
    setIsExtracting(false);
    setActiveTab(directTab || (xml ? "04_REVIEW" : "01_KNOWLEDGE"));
  };

  const handleReset = () => {
    setExtractedData(null);
    setBpmnXml(null);
    setProcessGraph(null);
    setProcessNarrative(null);
    setIsExtracting(false);
    setActiveTab("01_KNOWLEDGE");
  };

  const handleSelectAgentCard = async (agentNumber: string) => {
    const defaultSample = CORPUS_SAMPLES[0]; // F01_clean_purchase_requisition.bpmn
    try {
      const p = await processService.importBpmnXml(defaultSample.xml, defaultSample.name);
      handleExtractionSuccess(
        p.knowledge,
        p.bpmnXml,
        p.graph,
        p.narrative,
        agentNumber === "04"
          ? "04_REVIEW"
          : agentNumber === "03"
          ? "03_BPMN"
          : agentNumber === "02"
          ? "02_INTELLIGENCE"
          : "01_KNOWLEDGE"
      );
    } catch {
      const parsed = parseBpmnXmlClient(defaultSample.xml, defaultSample.name);
      handleExtractionSuccess(
        parsed.knowledge,
        parsed.xml,
        parsed.graph,
        undefined,
        agentNumber === "04" ? "04_REVIEW" : "01_KNOWLEDGE"
      );
    }
  };

  return (
    <div className="app">
      <div className="noise" />

      {/* Navigation */}
      <header className="header">
        <div className="container nav">
          <a
            href="/"
            className="logo"
            onClick={(e) => {
              if (extractedData) {
                e.preventDefault();
                handleReset();
              }
            }}
          >
            <span className="logo-mark">π</span>
            <span>P.I.E.</span>
          </a>

          <div className="nav-center">
            <span>PROCESS INTELLIGENCE</span>
            <span className="nav-line" />
            <span>v0.1</span>
          </div>

          <a
            href="https://github.com/Hrutu34"
            target="_blank"
            rel="noopener noreferrer"
            className="github"
          >
            GitHub ↗
          </a>
        </div>
      </header>

      <main>
        {/* VIEW 1: 4-AGENT WORKSPACE DASHBOARD */}
        {extractedData ? (
          <section className="workspace-shell">
            <div className="container">
              <div className="workspace-header">
                <div className="workspace-title-group">
                  <div className="status-pill">
                    <span className="status-dot" />
                    DISCOVERY GRAPH ACTIVE
                    <span className="status-separator">/</span>
                    ECOSYSTEM READY
                  </div>
                  <h2>Autonomous Process Discovery Workspace</h2>
                </div>

                <button type="button" className="btn-outline" onClick={handleReset}>
                  ↺ New Document Ingestion
                </button>
              </div>

              {/* 4 Agent Navigation Tabs */}
              <div className="workspace-tabs">
                <button
                  type="button"
                  className={`agent-tab ${activeTab === "01_KNOWLEDGE" ? "active" : ""}`}
                  onClick={() => setActiveTab("01_KNOWLEDGE")}
                >
                  <span>🧠 01 Knowledge Extraction</span>
                  <span className="tab-badge">Active</span>
                </button>

                <button
                  type="button"
                  className={`agent-tab ${activeTab === "02_INTELLIGENCE" ? "active" : ""}`}
                  onClick={() => setActiveTab("02_INTELLIGENCE")}
                >
                  <span>◉ 02 Process Intelligence</span>
                  <span className="tab-pending-badge">Ready</span>
                </button>

                <button
                  type="button"
                  className={`agent-tab ${activeTab === "03_BPMN" ? "active" : ""}`}
                  onClick={() => setActiveTab("03_BPMN")}
                >
                  <span>⌘ 03 BPMN Modelling</span>
                  <span className="tab-pending-badge">Ready</span>
                </button>

                <button
                  type="button"
                  className={`agent-tab ${activeTab === "04_REVIEW" ? "active" : ""}`}
                  onClick={() => setActiveTab("04_REVIEW")}
                >
                  <span>✦ 04 Process Review</span>
                  <span className="tab-pending-badge">Ready</span>
                </button>
              </div>

              {/* Tab Contents */}
              {activeTab === "01_KNOWLEDGE" && (
                <ProcessKnowledgeReview
                  data={extractedData}
                  onProceedToIntelligence={() => setActiveTab("02_INTELLIGENCE")}
                  onProceedToBpmn={() => setActiveTab("03_BPMN")}
                  onReset={handleReset}
                />
              )}

              {activeTab === "02_INTELLIGENCE" && (
                <ProcessIntelligenceAgent
                  knowledge={extractedData}
                  onProceedToBpmn={() => setActiveTab("03_BPMN")}
                />
              )}

              {activeTab === "03_BPMN" && (
                <ProcessGraphViewer
                  knowledge={extractedData}
                  defaultView="bpmn"
                  onProceedToBpmn={() => setActiveTab("04_REVIEW")}
                />
              )}

              {activeTab === "04_REVIEW" && (
                <ProcessReviewAgent
                  knowledge={extractedData}
                  graph={processGraph}
                  bpmnXml={bpmnXml}
                  initialNarrative={processNarrative}
                  onProcessUpdated={(k, g, x, n) => {
                    setExtractedData(k);
                    setProcessGraph(g);
                    setBpmnXml(x);
                    if (n) setProcessNarrative(n);
                  }}
                  onProceedToBpmn={() => setActiveTab("03_BPMN")}
                />
              )}
            </div>
          </section>
        ) : (
          /* VIEW 2: LANDING & INGESTION FLOW */
          <>
            <section className="hero">
              <div className="grid-background" />

              <div className="container hero-inner">
                <div className="status-pill">
                  <span className="status-dot" />
                  SYSTEM ONLINE
                  <span className="status-separator">/</span>
                  HACKATHON MVP
                </div>

                <div className="hero-label">
                  <span>01</span>
                  <span className="label-line" />
                  <span>PROCESS INTELLIGENCE ECOSYSTEM</span>
                </div>

                <h1>
                  Make the <span className="outline-text">invisible</span>
                  <br />
                  <span className="aqua-text">process visible.</span>
                </h1>

                <p className="hero-copy">
                  P.I.E. transforms unstructured business knowledge into
                  <span> validated processes</span>, editable
                  <span> BPMN 2.0 models</span>, and explanations your team can actually use.
                </p>

                {/* Ingestion Dropzone OR Animated Stepper Loading */}
                <div ref={processEntryRef} className="process-entry-wrapper">
                  {isExtracting ? (
                    <AgentLoadingScreen fileCount={activeFileCount} />
                  ) : (
                    <ProcessEntry
                      onStart={handleStartExtraction}
                      onSuccess={handleExtractionSuccess}
                      onError={() => setIsExtracting(false)}
                    />
                  )}
                </div>
              </div>
            </section>

            {/* Workflow Engine Cards */}
            <section className="workflow">
              <div className="container">
                <div className="section-intro">
                  <div>
                    <div className="section-number">02 / THE ENGINE</div>
                    <h2>
                      Four agents.
                      <br />
                      <span>One process brain.</span>
                    </h2>
                  </div>

                  <p>
                    P.I.E. transforms unstructured business knowledge into validated processes, editable BPMN 2.0 models,
                    and explanations your team can actually use.
                  </p>
                </div>

                <div className="agent-grid">
                  {agents.map((agent, index) => (
                    <article
                      className={`agent-card ${agent.accent}`}
                      key={agent.number}
                      onClick={() => handleSelectAgentCard(agent.number)}
                      style={{ cursor: "pointer" }}
                      title={`Launch Agent ${agent.number}: ${agent.title}`}
                    >
                      <div className="agent-header">
                        <span className="agent-index">{agent.number}</span>
                        <span className="agent-icon">{agent.icon}</span>
                      </div>

                      <div className="agent-content">
                        <h3>{agent.title}</h3>
                        <p>{agent.description}</p>
                      </div>

                      <div className="agent-footer">
                        <span>AGENT {agent.number}</span>
                        <span>LAUNCH 0{index + 1} ———→</span>
                      </div>
                    </article>
                  ))}
                </div>

                {/* Process Line */}
                <div className="process-line">
                  <div className="process-node aqua-node">KNOWLEDGE</div>
                  <div className="process-connector" />
                  <div className="process-node">INTELLIGENCE</div>
                  <div className="process-connector" />
                  <div className="process-node yellow-node">BPMN</div>
                  <div className="process-connector" />
                  <div className="process-node">EXPLAIN</div>
                </div>
              </div>
            </section>

            {/* Feature Statement */}
            <section className="statement">
              <div className="container">
                <div className="statement-card">
                  <div className="statement-top">
                    <span>03</span>
                    <span>WHY P.I.E.?</span>
                    <span>✦</span>
                  </div>

                  <h2>
                    Your business already has the knowledge.
                    <span> It's just trapped in documents.</span>
                  </h2>

                  <div className="statement-bottom">
                    <span>UNSTRUCTURED → STRUCTURED</span>
                    <span className="yellow-tag">AI + BPMN 2.0</span>
                  </div>
                </div>
              </div>
            </section>

            {/* Final CTA */}
            <section className="final-cta">
              <div className="container">
                <div className="cta-inner">
                  <div className="cta-orbit orbit-one" />
                  <div className="cta-orbit orbit-two" />

                  <div className="cta-content">
                    <span className="section-number">04 / INITIALIZE</span>
                    <h2>
                      Let's make your
                      <br />
                      <span>processes visible.</span>
                    </h2>

                    <button
                      className="mega-button"
                      type="button"
                      onClick={() =>
                        processEntryRef.current?.scrollIntoView({
                          behavior: "smooth",
                          block: "center",
                        })
                      }
                    >
                      <span>START WITH A DOCUMENT OR BPMN</span>
                      <strong>↗</strong>
                    </button>
                  </div>
                  <div className="pie-mark">π</div>
                </div>
              </div>
            </section>
          </>
        )}
      </main>

      <footer className="footer">
        <div className="container footer-inner">
          <span>🥧 P.I.E.</span>
          <span>PROCESS INTELLIGENCE ECOSYSTEM</span>
          <span>BUILT FOR THE I.MOBILOTHON © 2026</span>
        </div>
      </footer>
    </div>
  );
}

export default App;