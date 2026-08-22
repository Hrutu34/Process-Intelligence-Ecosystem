import "./App.css";
import ProcessEntry from "./components/ProcessEntry";
import { ProcessKnowledgeReview } from "./components/ProcessKnowledgeReview";
import type { ProcessKnowledgeDTO } from "../../backend/src/main/java/com/pie/shared/types/dto";
import { useRef, useState } from "react";

function App() {
  const processEntryRef = useRef<HTMLDivElement | null>(null);
  const [extractedData, setExtractedData] = useState<ProcessKnowledgeDTO | null>(null);

  const agents = [
    {
      number: "01",
      icon: "🧠",
      title: "Knowledge Extraction",
      description:
        "Turns messy documents, policies, and tribal knowledge into structured process intelligence.",
      accent: "aqua",
    },
    {
      number: "02",
      icon: "◉",
      title: "Process Intelligence",
      description:
        "Finds gaps, dead ends, missing owners, contradictions, and hidden process risks.",
      accent: "yellow",
    },
    {
      number: "03",
      icon: "⌘",
      title: "BPMN Modelling",
      description:
        "Transforms validated process knowledge into clean, editable BPMN 2.0 models.",
      accent: "aqua",
    },
    {
      number: "04",
      icon: "✦",
      title: "Process Review",
      description:
        "Turns complex process diagrams back into language your business actually understands.",
      accent: "yellow",
    },
  ];

  return (
    <div className="app">
      <div className="noise" />

      {/* Navigation */}
      <header className="header">
        <div className="container nav">
          <a href="/" className="logo">
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
        {/* Hero Section */}
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
              Make the
              <span className="outline-text"> invisible </span>
              <br />
              <span className="aqua-text">process visible.</span>
            </h1>

            <p className="hero-copy">
              P.I.E. transforms unstructured business knowledge into
              <span> validated processes</span>, editable
              <span> BPMN 2.0 models</span>, and explanations your team can
              actually use.
            </p>

            {/* Ingestion Console / Review Dashboard */}
            <div ref={processEntryRef} className="process-entry-wrapper">
              {!extractedData ? (
                <ProcessEntry onSuccess={(data) => setExtractedData(data)} />
              ) : (
                <ProcessKnowledgeReview
                  data={extractedData}
                  onReset={() => setExtractedData(null)}
                  onProceedToBpmn={() => alert('Proceeding to Agent 03: BPMN Modelling...')}
                />
              )}
            </div>
          </div>
        </section>

        {/* Workflow */}
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
                P.I.E. transforms unstructured business knowledge into
                validated processes, editable BPMN 2.0 models, and explanations
                your team can actually use.
              </p>
            </div>

            <div className="agent-grid">
              {agents.map((agent, index) => (
                <article
                  className={`agent-card ${agent.accent}`}
                  key={agent.number}
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
                    <span>0{index + 1} ———→</span>
                  </div>
                </article>
              ))}
            </div>

            {/* Process line */}
            <div className="process-line">
              <div className="process-node aqua-node">KNOWLEDGE</div>
              <div className="process-connector">
                <span />
              </div>
              <div className="process-node">INTELLIGENCE</div>
              <div className="process-connector">
                <span />
              </div>
              <div className="process-node yellow-node">BPMN</div>
              <div className="process-connector">
                <span />
              </div>
              <div className="process-node">EXPLAIN</div>
            </div>
          </div>
        </section>

        {/* Feature statement */}
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
                  <span>START WITH A DOCUMENT</span>
                  <strong>↗</strong>
                </button>
              </div>

              <div className="pie-mark">π</div>
            </div>
          </div>
        </section>
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