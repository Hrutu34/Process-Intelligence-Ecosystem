import React, { useRef, useState } from 'react';
import ProcessEntry from './ProcessEntry';
import { AgentLoadingScreen } from './AgentLoadingScreen';
import type { ProcessKnowledgeDTO } from '../../../backend/src/main/java/com/pie/shared/types/dto';

interface AgentCard {
  number: string;
  title: string;
  description: string;
  icon: string;
  accent: string;
}

const AGENTS: AgentCard[] = [
  {
    number: "01",
    title: "Knowledge Extraction Agent",
    description: "Ingests raw business documents and extracts structured process knowledge.",
    icon: "🧠",
    accent: "aqua",
  },
  {
    number: "02",
    title: "Process Intelligence Agent",
    description: "Validates completeness, flags ambiguities, and resolves conflicts.",
    icon: "◉",
    accent: "blue",
  },
  {
    number: "03",
    title: "BPMN 2.0 Modelling Agent",
    description: "Constructs semantic XML and visual process graphs from normalized knowledge.",
    icon: "⌘",
    accent: "yellow",
  },
  {
    number: "04",
    title: "Process Review Agent",
    description: "Translates technical process models back into clear business explanations.",
    icon: "✦",
    accent: "white",
  },
];

interface Props {
  onGetStarted: () => void;
  onViewDemo: () => void;
  onExtractionComplete: (knowledge: ProcessKnowledgeDTO) => void;
}

export const LandingPage: React.FC<Props> = ({
  onGetStarted,
  onViewDemo,
  onExtractionComplete,
}) => {
  const [isExtracting, setIsExtracting] = useState(false);
  const [activeFileCount, setActiveFileCount] = useState(1);
  const processEntryRef = useRef<HTMLDivElement | null>(null);

  const handleStartExtraction = (fileCount: number) => {
    setActiveFileCount(fileCount);
    setIsExtracting(true);
  };

  const handleExtractionSuccess = (data: ProcessKnowledgeDTO) => {
    setIsExtracting(false);
    onExtractionComplete(data);
  };

  return (
    <div className="app">
      {/* Top Header */}
      <header className="header">
        <div className="container header-inner">
          <div className="brand" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
            <div className="logo-mark">π</div>
            <div className="brand-text">
              <span className="brand-title">P.I.E.</span>
              <span className="brand-subtitle">PROCESS INTELLIGENCE ECOSYSTEM</span>
            </div>
          </div>

          <nav className="nav">
            <a href="#engine" className="nav-link">
              THE ENGINE
            </a>
            <a href="#why-pie" className="nav-link">
              WHY P.I.E.
            </a>
            <button type="button" className="btn-ghost" onClick={onViewDemo} style={{ marginLeft: 8 }}>
              TRY DEMO
            </button>
            <button type="button" className="yellow-button" onClick={onGetStarted}>
              SIGN IN <span>↗</span>
            </button>
          </nav>
        </div>
      </header>

      <main>
        {/* VIEW: HERO & INGESTION FLOW */}
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
                />
              )}
            </div>
          </div>
        </section>

        {/* Workflow Engine Cards */}
        <section id="engine" className="workflow">
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
              {AGENTS.map((agent, index) => (
                <article className={`agent-card ${agent.accent}`} key={agent.number}>
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
        <section id="why-pie" className="statement">
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
};
