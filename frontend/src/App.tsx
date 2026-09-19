import "./App.css";
import ProcessEntry from "./components/ProcessEntry";
import { ProcessKnowledgeReview } from "./components/ProcessKnowledgeReview";
import { ProcessIntelligenceAgent } from "./components/ProcessIntelligenceAgent";
import { ProcessGraphViewer, clearProcessGraphCache } from "./components/ProcessGraphViewer";
import { ProcessReviewAgent } from "./components/ProcessReviewAgent";
import { AgentLoadingScreen } from "./components/AgentLoadingScreen";
import { AgentExecutionTracker } from "./components/AgentExecutionTracker";
import { SplashScreen } from "./components/SplashScreen";
import { ChatDock } from "./components/ChatDock";
import BpmnImageViewer from "./components/BpmnImageViewer";
import HistoryPanel from "./components/HistoryPanel";
import EditDrawer from "./components/EditDrawer";
import WhatIsBpmn from "./components/WhatIsBpmn";
import TextToDiagramGenAnim from "./components/TextToDiagramGenAnim";
import DiagramToSummaryGenAnim from "./components/DiagramToSummaryGenAnim";
import { historyStore, type HistoryEntry } from "./services/historyStore";
import { trackerStore, useTrackerStore } from "./services/trackerStore";
import { SunIcon, MoonIcon, StarIcon } from "./services/icons";
import vwgdsLogo from "./assets/vwgds-logo.png";
import type { ProcessKnowledgeDTO } from "../../backend/src/main/java/com/pie/shared/types/dto";
import type { SelectedElementInfo } from "./services/chatService";
import { useCallback, useEffect, useState } from "react";
import { ProcessMiningTab } from "./components/simulation/ProcessMiningTab";

type AgentTab = "01_KNOWLEDGE" | "02_INTELLIGENCE" | "03_BPMN" | "04_REVIEW" | "05_SIMULATION";
type EntryMode = "text-to-diagram" | "diagram-to-summary";

function App() {
  const [extractedData, setExtractedData] = useState<ProcessKnowledgeDTO | null>(null);
  const [isExtracting, setIsExtracting] = useState<boolean>(false);
  const [activeFileCount, setActiveFileCount] = useState<number>(1);
  const [activeTab, setActiveTab] = useState<AgentTab>("01_KNOWLEDGE");
  const [currentBpmnXml, setCurrentBpmnXml] = useState<string | null>(null);
  const [currentSourceText, setCurrentSourceText] = useState<string>("");
  const [, setHighlightedElement] = useState<{ id: string; color: string } | null>(null);
  const [selectedBpmnElement, setSelectedBpmnElement] = useState<SelectedElementInfo | null>(null);
  const [showSplash, setShowSplash] = useState(true);
  const [entryMode, setEntryMode] = useState<EntryMode>("text-to-diagram");
  const [view, setView] = useState<"landing" | "ingest">("landing");
  const trackerState = useTrackerStore();
  const [isTrackerHovered, setIsTrackerHovered] = useState<boolean>(false);

  useEffect(() => {
    if (!isExtracting) return;
    
    trackerStore.setStageActive('INPUT_RECEIVED', 'Reading input and validating format...');
    
    const sequence = [
      { delay: 1000, fn: () => trackerStore.setStageActive('CLASSIFYING', 'Classifying document...') },
      { delay: 2500, fn: () => trackerStore.setStageActive('KNOWLEDGE_EXTRACTION', 'Extracting process knowledge...') },
      { delay: 5000, fn: () => trackerStore.setStageComplete('KNOWLEDGE_EXTRACTION', 'Knowledge extraction complete. Ready for intelligence.') }
    ];
    
    const timers = sequence.map(step => setTimeout(step.fn, step.delay));
    return () => timers.forEach(clearTimeout);
  }, [isExtracting]);

  const openIngestPage = (mode: EntryMode) => {
    setEntryMode(mode);
    setView("ingest");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  const backToLanding = () => {
    setView("landing");
    setIsExtracting(false);
  };

  // History (F6)
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyEntries, setHistoryEntries] = useState<HistoryEntry[]>([]);
  const refreshHistory = useCallback(() => setHistoryEntries(historyStore.list()), []);
  useEffect(() => { refreshHistory(); }, [refreshHistory]);

  // Edit drawer (F5)
  const [editDrawerOpen, setEditDrawerOpen] = useState(false);
  const [regenerating, setRegenerating] = useState(false);

  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const toggleTheme = () => {
    const newTheme = theme === "dark" ? "light" : "dark";
    setTheme(newTheme);
    document.documentElement.setAttribute("data-theme", newTheme);
  };

  const agents = [
    { number: "01", icon: "/mascots/ApplePie.png", title: "Apple Pie (Extraction)", description: "Turns messy documents, policies, and tribal knowledge into structured process intelligence.", accent: "aqua" },
    { number: "02", icon: "/mascots/BlueBerryPie.png", title: "Blueberry Pie (Intelligence)", description: "Finds gaps, dead ends, missing owners, contradictions, and hidden process risks.", accent: "yellow" },
    { number: "03", icon: "/mascots/CherryPie.png", title: "Cherry Pie (BPMN)", description: "Transforms validated process knowledge into clean, editable BPMN 2.0 models.", accent: "aqua" },
    { number: "04", icon: "/mascots/PecanPie.png", title: "Pecan Pie (Review)", description: "Turns complex process diagrams back into language your business actually understands.", accent: "yellow" },
    { number: "05", icon: "/mascots/KeyLimePie.png", title: "Key Lime Pie (Simulation)", description: "Simulates stochastic tokens, identifies bottlenecks, computes SLA adherence, and exports Celonis/Disco event logs.", accent: "aqua" },
  ];

  const handleStartExtraction = (fileCount: number) => {
    setActiveFileCount(fileCount);
    setIsExtracting(true);
  };

  const handleExtractionSuccess = (data: ProcessKnowledgeDTO) => {
    setExtractedData(data);
    setIsExtracting(false);
    setActiveTab("01_KNOWLEDGE");
    historyStore.add({
      type: "diagram",
      title: (data as any)?.processName || "Extracted process",
      preview: [(data as any)?.activities?.[0], (data as any)?.actors?.[0]].filter(Boolean).join(" · ") || "Ingested knowledge",
      knowledge: data,
      sourceText: currentSourceText,
    });
    refreshHistory();
  };

  const handleBpmnImported = ({ knowledge, bpmnXml }: { knowledge: ProcessKnowledgeDTO; bpmnXml: string }) => {
    setExtractedData(knowledge);
    setCurrentBpmnXml(bpmnXml);
    setIsExtracting(false);
    setActiveTab("04_REVIEW");
    historyStore.add({
      type: entryMode === "diagram-to-summary" ? "summary" : "diagram",
      title: (knowledge as any)?.processName || "Imported BPMN",
      preview: "BPMN diagram imported for review",
      knowledge,
      bpmnXml,
    });
    refreshHistory();
  };

  const handleReset = () => {
    setExtractedData(null);
    setIsExtracting(false);
    setCurrentBpmnXml(null);
    setCurrentSourceText("");
    setHighlightedElement(null);
  };

  // F5: regenerate BPMN from edited text
  const handleRegenerate = async (newText: string) => {
    setRegenerating(true);
    try {
      const res = await fetch("http://localhost:8080/api/v1/process/extract-text", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: newText }),
      });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      setCurrentSourceText(newText);
      setExtractedData(data);
      setCurrentBpmnXml(null);
      setActiveTab("03_BPMN");
      setEditDrawerOpen(false);
      historyStore.add({
        type: "diagram",
        title: (data as any)?.processName || "Regenerated process",
        preview: newText.slice(0, 120),
        knowledge: data,
        sourceText: newText,
      });
      refreshHistory();
    } catch (err: any) {
      alert(err?.message || "Failed to regenerate diagram.");
    } finally {
      setRegenerating(false);
    }
  };

  // Auto-sync BPMN edits to downstream knowledge and graph representations.
  useEffect(() => {
    if (!currentBpmnXml) return;
    const timer = setTimeout(async () => {
      try {
        const bpmnForm = new FormData();
        bpmnForm.append('file', new Blob([currentBpmnXml], { type: 'text/xml' }), 'auto-sync.bpmn');
        const res = await fetch('http://localhost:8080/api/v1/process/import-bpmn-file', {
          method: 'POST',
          body: bpmnForm,
        });
        if (res.ok) {
          const data = await res.json();
          setExtractedData((prev) => {
            if (!prev) return data.knowledge;
            const isPlaceholder = (s: string) => {
              const lower = (s || '').trim().toLowerCase();
              return ['unassigned', 'others', 'unknown', 'someone', 'n/a', 'none', 'null', 'placeholder', 'tbd'].includes(lower);
            };
            const incomingActors = (data.knowledge?.actors || []).filter((a: string) => !isPlaceholder(a));
            const incomingRoles = (data.knowledge?.roles || []).filter((r: string) => !isPlaceholder(r));

            return {
              ...prev,
              activities: data.knowledge?.activities && data.knowledge.activities.length > 0 ? data.knowledge.activities : prev.activities,
              actors: incomingActors.length > 0 ? incomingActors : prev.actors,
              roles: incomingRoles.length > 0 ? incomingRoles : prev.roles,
              gateways: data.knowledge?.gateways && data.knowledge.gateways.length > 0 ? data.knowledge.gateways : prev.gateways,
              events: data.knowledge?.events && data.knowledge.events.length > 0 ? data.knowledge.events : prev.events,
              // Preserve rich textual items that BPMN XML cannot represent
              inputs: prev.inputs && prev.inputs.length > 0 ? prev.inputs : (data.knowledge?.inputs || []),
              outputs: prev.outputs && prev.outputs.length > 0 ? prev.outputs : (data.knowledge?.outputs || []),
              businessRules: prev.businessRules && prev.businessRules.length > 0 ? prev.businessRules : (data.knowledge?.businessRules || []),
              processName: (prev as any)?.processName || data.processName || (data.knowledge as any)?.processName,
            };
          });
          window.dispatchEvent(new CustomEvent("pie:bpmn-applied", { detail: { bpmnXml: currentBpmnXml } }));
        }
      } catch (err) {
        console.error("Failed to auto-sync BPMN edits to knowledge", err);
      }
    }, 1500);
    return () => clearTimeout(timer);
  }, [currentBpmnXml]);

  // F6: reopen history entry
  const handleReopenEntry = (entry: HistoryEntry) => {
    if (entry.knowledge) setExtractedData(entry.knowledge);
    if (entry.bpmnXml) setCurrentBpmnXml(entry.bpmnXml);
    if (entry.sourceText) setCurrentSourceText(entry.sourceText);
    setActiveTab(entry.type === "summary" ? "04_REVIEW" : "03_BPMN");
    setHistoryOpen(false);
  };

  const handleDeleteEntry = (id: string) => { historyStore.remove(id); refreshHistory(); };
  const handleClearHistory = () => { historyStore.clear(); refreshHistory(); };

  const tabStatus = (tab: AgentTab) => (
    <span className={activeTab === tab ? "tab-badge" : "tab-pending-badge"}>
      {activeTab === tab ? "Active" : "Ready"}
    </span>
  );

  return (
    <>
      {showSplash && <SplashScreen onComplete={() => setShowSplash(false)} />}
      <div className="app">
        <div className="noise" />

        {/* Navigation (F4: VWGDS logo, F6: History button) */}
        <header className="header">
          <div className="container nav">
            <a
              href="/"
              className="logo"
              onClick={(e) => {
                if (extractedData) { e.preventDefault(); handleReset(); }
              }}
            >
              <img
                src="/mascots/Pie.png"
                alt="PIE Logo"
                className="logo-img"
                style={{ width: 50, height: 50, objectFit: "cover", borderRadius: "50%", boxShadow: "0 0 15px rgba(34, 197, 94, 0.25)" }}
              />
              <span>Pie</span>
            </a>

            <div className="nav-center">
              <span>PROCESS INTELLIGENCE ECOSYSTEM</span>
            </div>

            <div className="nav-right" style={{ display: "flex", alignItems: "center", gap: "12px" }}>
              <button
                className="nav-history-btn"
                onClick={() => {
                  if (!extractedData) {
                    setExtractedData({ processName: 'Enterprise Workflow Simulator' } as any);
                  }
                  setActiveTab("05_SIMULATION");
                  setView("landing");
                }}
                title="Open Process Mining & Discrete Event Simulator"
                style={{ borderColor: 'rgba(99, 102, 241, 0.4)', color: '#c7d2fe' }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polygon points="5 3 19 12 5 21 5 3" />
                </svg>
                Simulation
              </button>
              <button
                className="nav-history-btn"
                onClick={() => setHistoryOpen((v) => !v)}
                aria-label="History"
                title="Process history"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                History
                {historyEntries.length > 0 && <span className="history-badge">{historyEntries.length}</span>}
              </button>
               <button className="theme-toggle" onClick={toggleTheme} aria-label="Toggle Theme">
                 {theme === "dark" ? <SunIcon /> : <MoonIcon />}
               </button>
              <span className="vwgds-logo-frame" aria-label="Volkswagen Group Digital Solutions">
                <img src={vwgdsLogo} alt="VWGDS" className="vwgds-logo-img" />
              </span>
              <a href="https://github.com/Hrutu34" target="_blank" rel="noopener noreferrer" className="github">
                GitHub ↗
              </a>
            </div>
          </div>
        </header>

        <main>
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

                  <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
                    <button type="button" className="btn-outline" onClick={handleReset}>
                      ↺ New Document Ingestion
                    </button>
                  </div>
                </div>

                <div className="workspace-tabs">
                  <button type="button" className={`agent-tab ${activeTab === "01_KNOWLEDGE" ? "active" : ""}`} onClick={() => setActiveTab("01_KNOWLEDGE")}>
                    <span>Apple Pie</span>{tabStatus("01_KNOWLEDGE")}
                  </button>
                  <button type="button" className={`agent-tab ${activeTab === "02_INTELLIGENCE" ? "active" : ""}`} onClick={() => setActiveTab("02_INTELLIGENCE")}>
                    <span>Blueberry Pie</span>{tabStatus("02_INTELLIGENCE")}
                  </button>
                  <button type="button" className={`agent-tab ${activeTab === "03_BPMN" ? "active" : ""}`} onClick={() => setActiveTab("03_BPMN")}>
                    <span>Cherry Pie</span>{tabStatus("03_BPMN")}
                  </button>
                  <button type="button" className={`agent-tab ${activeTab === "04_REVIEW" ? "active" : ""}`} onClick={() => setActiveTab("04_REVIEW")}>
                    <span>Pecan Pie</span>{tabStatus("04_REVIEW")}
                  </button>
                  <button type="button" className={`agent-tab ${activeTab === "05_SIMULATION" ? "active" : ""}`} onClick={() => setActiveTab("05_SIMULATION")}>
                    <span>Process Mining</span>{tabStatus("05_SIMULATION")}
                  </button>
                </div>

                {activeTab === "01_KNOWLEDGE" && (
                  <ProcessKnowledgeReview
                    data={extractedData}
                    onProceedToIntelligence={() => setActiveTab("02_INTELLIGENCE")}
                    onReset={handleReset}
                    onKnowledgeChange={(next) => {
                      // User edited the extracted entities. Invalidate every
                      // downstream artifact derived from the previous knowledge
                      // so the graph and BPMN diagram are regenerated fresh
                      // from the corrected DTO — no stale cached output leaks
                      // into Blueberry Pie or Cherry Pie.
                      clearProcessGraphCache();
                      setCurrentBpmnXml(null);
                      setExtractedData(next);
                    }}
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
                    onProceed={() => setActiveTab("04_REVIEW")}
                    proceedLabel="PROCEED TO PROCESS REVIEW <span>↗</span>"
                    onXmlChange={setCurrentBpmnXml}
                    externalXml={currentBpmnXml}
                    onElementSelected={setSelectedBpmnElement}
                  />
                )}

                {activeTab === "04_REVIEW" && (
                  <div className="review-layout">
                    <aside className="review-diagram-panel">
                      <div className="review-diagram-head">
                        <span className="review-diagram-title">BPMN Preview</span>
                        <span className="review-diagram-sub">read-only — edit in Cherry Pie</span>
                      </div>
                      <BpmnImageViewer bpmnXml={currentBpmnXml} height={340} />
                    </aside>
                    <div className="review-summary-panel custom-scrollbar">
                      <ProcessReviewAgent
                        bpmnXml={currentBpmnXml}
                        onHighlightIssue={(id, color) => setHighlightedElement(id ? { id, color: color || "#ff6b6b" } : null)}
                      />
                      <div style={{ marginTop: '16px', display: 'flex', justifyContent: 'flex-end' }}>
                        <button
                          type="button"
                          className="btn-primary"
                          onClick={() => setActiveTab("05_SIMULATION")}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            padding: '8px 16px',
                            fontSize: '0.82rem',
                            fontWeight: 600,
                            borderRadius: '8px',
                            cursor: 'pointer',
                            background: 'linear-gradient(135deg, #6366f1 0%, #4f46e5 100%)',
                            color: '#ffffff',
                            border: '1px solid #6366f1',
                            boxShadow: '0 4px 15px rgba(99, 102, 241, 0.35)'
                          }}
                        >
                          <span>PROCEED TO PROCESS MINING &amp; SIMULATION</span>
                          <span>↗</span>
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {activeTab === "05_SIMULATION" && (
                  <ProcessMiningTab
                    bpmnXml={currentBpmnXml}
                    userContext={currentSourceText}
                    processName={(extractedData as any)?.processName || "Ecosystem Process"}
                  />
                )}
              </div>
            </section>
          ) : view === "ingest" ? (
            /* INGEST PAGE (F2 from item list — separate page) */
            <section className="ingest-page">
              <div className="container">
                <div className="ingest-page-head">
                  <button type="button" className="btn-outline" onClick={backToLanding}>
                    ← Back
                  </button>
                  <div className="ingest-page-title">
                    <div className={"ingest-page-badge " + (entryMode === "diagram-to-summary" ? "badge-purple" : "badge-blue")}>
                      {entryMode === "diagram-to-summary" ? "Diagram → Summary" : "Text → Diagram"}
                    </div>
                    <h2>
                      {entryMode === "diagram-to-summary"
                        ? "Upload a BPMN file to summarize"
                        : "Provide process notes or documents"}
                    </h2>
                    <p>
                      {entryMode === "diagram-to-summary"
                        ? "Drop a .bpmn / .bpmn20.xml file. Pie will render it, validate it, and produce a plain-language walkthrough."
                        : "Paste text or drop PDFs, DOCX, XLSX. Apple Pie will extract structured process knowledge and generate an editable BPMN 2.0 diagram."}
                    </p>
                  </div>
                </div>

                <div className="ingest-page-body">
                  {isExtracting ? (
                    <AgentLoadingScreen
                      fileCount={activeFileCount}
                      title="Apple Pie (Extraction) in Progress"
                      mascotImage="/mascots/ApplePie.png"
                    />
                  ) : (
                    <ProcessEntry
                      onStart={handleStartExtraction}
                      onSuccess={handleExtractionSuccess}
                      onBpmnImported={handleBpmnImported}
                      onError={() => setIsExtracting(false)}
                      initialTab={entryMode === "diagram-to-summary" ? "file" : "text"}
                    />
                  )}
                </div>
              </div>
            </section>
          ) : (
            /* LANDING VIEW */
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
                    Pie transforms unstructured business knowledge into
                    <span> validated processes</span>, editable
                    <span> BPMN 2.0 models</span>, and explanations your team can actually use.
                  </p>

                  {/* F1: Two glassy tabs — each opens the ingest page */}
                  <div className="bubbles">
                    <button
                      type="button"
                      className="bubble bubble--neon-blue"
                      onClick={() => openIngestPage("text-to-diagram")}
                    >
                      <span className="bubble-sheen" />
                      <span className="bubble-glow bubble-glow--a" />
                      <div className="bubble-icon">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                          <polyline points="14 2 14 8 20 8" />
                          <line x1="8" y1="13" x2="16" y2="13" />
                          <line x1="8" y1="17" x2="14" y2="17" />
                        </svg>
                      </div>
                      <h2>Text → Diagram</h2>
                      <p>Paste process notes or upload documents. Pie extracts structured knowledge and generates an editable BPMN 2.0 diagram.</p>
                      <span className="bubble-cta">Start ingestion →</span>
                    </button>

                    <button
                      type="button"
                      className="bubble bubble--neon-purple"
                      onClick={() => openIngestPage("diagram-to-summary")}
                    >
                      <span className="bubble-sheen" />
                      <span className="bubble-glow bubble-glow--b" />
                      <div className="bubble-icon">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <circle cx="6" cy="6" r="3" />
                          <rect x="10" y="4" width="10" height="4" rx="1" />
                          <line x1="6" y1="9" x2="6" y2="15" />
                          <rect x="10" y="13" width="10" height="4" rx="1" />
                          <circle cx="6" cy="18" r="3" />
                        </svg>
                      </div>
                      <h2>Diagram → Summary</h2>
                      <p>Upload a BPMN file. Pie renders it, validates the model, and produces a plain-language walkthrough your team can read.</p>
                      <span className="bubble-cta">Upload BPMN →</span>
                    </button>

                    <button
                      type="button"
                      className="bubble bubble--neon-blue"
                      style={{ borderColor: 'rgba(99, 102, 241, 0.5)' }}
                      onClick={() => {
                        if (!extractedData) {
                          setExtractedData({ processName: 'Enterprise Workflow Simulator' } as any);
                        }
                        setActiveTab("05_SIMULATION");
                      }}
                    >
                      <span className="bubble-sheen" />
                      <span className="bubble-glow bubble-glow--a" style={{ background: 'rgba(99, 102, 241, 0.3)' }} />
                      <div className="bubble-icon">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#818cf8" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <polygon points="5 3 19 12 5 21 5 3" />
                        </svg>
                      </div>
                      <h2>Simulation &amp; Mining</h2>
                      <p>Simulate thousands of stochastic cases, analyze worker queues, identify bottlenecks, and export Celonis/Disco event logs.</p>
                      <span className="bubble-cta" style={{ color: '#818cf8' }}>Launch Studio →</span>
                    </button>
                  </div>

                  {/* F2: Animation showing both processes */}
                  <div className="anim-grid">
                    <div className="anim-card anim-card--blue">
                      <div className="anim-card-title">How Text → Diagram works</div>
                      <TextToDiagramGenAnim />
                    </div>
                    <div className="anim-card anim-card--purple">
                      <div className="anim-card-title">How Diagram → Summary works</div>
                      <DiagramToSummaryGenAnim />
                    </div>
                  </div>

                </div>
              </section>

              {/* F3: What is BPMN section */}
              <section className="container">
                <WhatIsBpmn />
              </section>

              <section className="workflow">
                <div className="container">
                  <div className="section-intro">
                    <div>
                      <div className="section-number">02 / THE BAKERY</div>
                      <h2>Four agents.<br /><span>One process brain.</span></h2>
                    </div>
                    <p>
                      Pie transforms unstructured business knowledge into validated processes, editable BPMN 2.0 models,
                      and explanations your team can actually use.
                    </p>
                  </div>

                  <div className="agent-grid">
                    {agents.map((agent, index) => (
                      <article className={`agent-card ${agent.accent}`} key={agent.number}>
                        <div className="agent-header">
                          <span className="agent-index">{agent.number}</span>
                          <img src={agent.icon} alt={agent.title} className="agent-icon" style={{ width: 64, height: 64, objectFit: "cover", borderRadius: "50%" }} />
                        </div>
                        <div className="agent-content">
                          <h3>{agent.title}</h3>
                          <p>{agent.description}</p>
                        </div>
                        <div className="agent-footer">
                          <span>PIE {agent.number}</span>
                          <span>0{index + 1} ———→</span>
                        </div>
                      </article>
                    ))}
                  </div>

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

              <section className="statement">
                <div className="container">
                  <div className="statement-card">
                    <div className="statement-top">
                      <span>03</span>
                      <span>WHY PIE?</span>
                      <span className="statement-star"><StarIcon /></span>
                    </div>
                    <h2>Your business already has the knowledge.<span> It's just trapped in documents.</span></h2>
                    <div className="statement-bottom">
                      <span>UNSTRUCTURED → STRUCTURED</span>
                      <span className="yellow-tag">AI + BPMN 2.0</span>
                    </div>
                  </div>
                </div>
              </section>

              <section className="final-cta">
                <div className="container">
                  <div className="cta-inner">
                    <div className="cta-orbit orbit-one" />
                    <div className="cta-orbit orbit-two" />
                    <div className="cta-content">
                      <span className="section-number">04 / INITIALIZE</span>
                      <h2>Let's make your<br /><span>processes visible.</span></h2>
                      <button
                        className="mega-button"
                        type="button"
                        onClick={() => openIngestPage("text-to-diagram")}
                      >
                        <span>START WITH A DOCUMENT</span>
                        <strong>↗</strong>
                      </button>
                    </div>
                    <div className="pie-mark"></div>
                  </div>
                </div>
              </section>
            </>
          )}
        </main>

        <footer className="footer">
          <div className="container footer-inner">
            <div 
              className="footer-status-container"
              onMouseEnter={() => setIsTrackerHovered(true)}
              onMouseLeave={() => setIsTrackerHovered(false)}
            >
              <button className="footer-status-btn" aria-label="Pipeline Status">
                <span 
                  className={`status-dot ${isExtracting ? 'pulsing' : ''}`} 
                  style={!isExtracting ? { backgroundColor: 'var(--text-dimmer)' } : {}}
                />
                {isExtracting ? 'Pipeline Active' : 'System Idle'}
              </button>

              {isTrackerHovered && (
                <div className="tracker-popup-container">
                  <AgentExecutionTracker state={trackerState} />
                </div>
              )}
            </div>
            <span>PROCESS INTELLIGENCE ECOSYSTEM</span>
            <span>BUILT FOR THE I.MOBILOTHON © 2026</span>
          </div>
        </footer>

        {/* F6: History slide-in */}
        <HistoryPanel
          open={historyOpen}
          onClose={() => setHistoryOpen(false)}
          entries={historyEntries}
          onClear={handleClearHistory}
          onDeleteEntry={handleDeleteEntry}
          onReopenEntry={handleReopenEntry}
        />

        {/* F5: Edit drawer — available in BPMN/Review views */}
        {extractedData && (activeTab === "03_BPMN" || activeTab === "04_REVIEW") && (
          <EditDrawer
            open={editDrawerOpen}
            onToggle={() => setEditDrawerOpen((v) => !v)}
            onClose={() => setEditDrawerOpen(false)}
            sourceText={currentSourceText}
            onRegenerate={handleRegenerate}
            regenerating={regenerating}
          />
        )}

        {currentBpmnXml && (
          <ChatDock
            processName={(extractedData as any)?.processName || "Process Model"}
            bpmnXml={currentBpmnXml}
            knowledge={extractedData}
            selectedElement={selectedBpmnElement}
            onClearSelectedElement={() => setSelectedBpmnElement(null)}
            sourceText={currentSourceText}
            onBpmnUpdated={setCurrentBpmnXml}
          />
        )}
      </div>
    </>
  );
}

export default App;
