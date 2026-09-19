# P.I.E. — Process Intelligence Ecosystem

> **Turn plain business language into validated, editable BPMN 2.0 diagrams — powered by a four-agent AI workflow.**

Team **Prompt Cartel** · VWGDS India · Engineering [R&D] (I-DK-R)

---

## TL;DR

Paste a paragraph like *"An employee submits a leave request. The manager reviews it…"* and P.I.E. gives you back:

- A structured **process graph** (activities, roles, gateways, systems, events).
- A **BPMN 2.0 XML** with lanes, task types, gateways, timers, and end events.
- A **review report** (issues, quality score, plain-language summary).
- An **editable canvas** (`bpmn-js`) with chat-based refinement.

Runs locally against a local LLM (Ollama), a VW LLMaaS gateway, or Gemini.

---

## Quick Start

```bash
git clone <repo> pie && cd pie
cp .env.example .env         # add ONE LLM credential (see Profiles)
chmod +x pie.sh
./pie.sh verify              # sanity-check toolchain + env
./pie.sh build               # mvn compile + npm install
./pie.sh start               # pick profile, boots backend + frontend
```

Open:

| Service    | URL                                    |
|------------|----------------------------------------|
| Frontend   | http://localhost:5173                  |
| Backend    | http://localhost:8080                  |
| H2 console | http://localhost:8080/h2-console (local profile) |

All CLI commands:

```bash
./pie.sh {verify|build|start|stop [backend|frontend|all]|status|logs [backend|frontend]|test [b|f]|zip [out.zip]}
```

### Profiles

| Profile   | DB              | LLM                          | Required env                                                |
|-----------|-----------------|------------------------------|-------------------------------------------------------------|
| `local`   | H2 in-memory    | Ollama (local)               | none                                                        |
| `staging` | PostgreSQL      | VW LLMaaS                    | `VW_LLM_*`                                                  |
| `prod`    | Cloud DB        | Gemini                       | `GEMINI_API_KEY`                                            |
| `prod-h2` | H2 file         | VW LLMaaS / OpenAI gpt-4o    | `VW_LLM_CLIENT_ID`, `VW_LLM_CLIENT_SECRET`, `VW_LLM_API_KEY` |

Prerequisites: **Java 17+**, **Maven 3.9+** (or bundled `./mvnw`), **Node 18+**, optional Docker for staging.

---

## Prove It Works

Five ready-to-paste narratives + expected outputs live in [`showcase/`](showcase/):

| # | Case                              | Highlights                                        |
|---|-----------------------------------|---------------------------------------------------|
| 1 | Leave Request Approval            | dual exclusive gateways, correct role split      |
| 2 | Payment Retry Loop                | loop-back edge, retry counter, escalation        |
| 3 | Expense 7-Day SLA                 | timer intermediate event (ISO-8601 `P7D`)        |
| 4 | Employee Onboarding               | parallel fork/join across 3 lanes                |
| 5 | Purchase Requisition + Threshold  | rework loop + multi-approver escalation          |
| 6 | Order Fulfillment                 | pure parallel fork + join, multi-lane            |

Each case in `expected_outputs.txt` lists MUST-HAVE elements and MUST-NOT regression traps — tick them off in the generated diagram.

---

## Architecture

```
Business User
     │
     ▼
Frontend (React + Vite + bpmn-js)
     │  REST
     ▼
Backend (Spring Boot)
     │
     ▼
Agentic Orchestrator
     ├── Knowledge Extraction  →  ProcessKnowledgeDTO (activities, actors, gateways, rules)
     ├── Process Intelligence  →  ProcessGraphDTO (nodes + edges + validation)
     ├── BPMN Modelling        →  BPMN 2.0 XML + DI layout
     └── Process Review        →  quality score + issues + plain-language summary
     │
     ▼
H2 / PostgreSQL   +   Storage (uploads, snapshots, versions)
```

### The four agents

| Agent                       | Input                     | Output                                      | Key files                                       |
|-----------------------------|---------------------------|---------------------------------------------|-------------------------------------------------|
| **Knowledge Extraction**    | Free text or docs         | `ProcessKnowledgeDTO` (JSON)                | `KnowledgeExtractionService`, `knowledge-extraction-prompt.txt` |
| **Process Intelligence**    | `ProcessKnowledgeDTO`     | `ProcessGraphDTO` (nodes + edges + roles)   | `ProcessGraphBuilder`, `ProcessGraphValidator`  |
| **BPMN Modelling**          | `ProcessGraphDTO`         | BPMN 2.0 XML with DI layout                 | `BpmnDomainModelMapper`, `BpmnXmlGenerationService` |
| **Process Review**          | BPMN XML + knowledge      | Summary + issues + quality score            | `AiProcessReviewService`, `AiProcessQualityService` |

### Chat + refinement loop

`AiBpmnRefinementService` + `chat-edit-prompt.txt` let the user say *"add a rejection path from Review to End"* and the diagram updates in place with a version bump (`BpmnVersionService`).

---

## Repository Layout

```
pie-ecosystem/
├── README.md              ← this file
├── pie.sh                 ← CLI (start / stop / build / test / zip)
├── docker-compose.yml     ← Postgres for staging profile
├── backend/               ← Spring Boot API + agents
│   └── src/main/java/com/pie/backend/
│       ├── service/       ← agents, graph builder, BPMN generator
│       ├── controller/    ← REST endpoints
│       └── dto/           ← wire contracts
├── frontend/              ← React + Vite + bpmn-js canvas
├── agents/                ← per-agent info + prompts (info_Todo scaffolds)
├── samples/               ← reference narratives + expected outputs
├── showcase/              ← 6 curated demo cases + expected outputs
├── docs/                  ← architecture + roadmap notes
└── tests/                 ← integration + e2e placeholders
```

---

## Feature Highlights

**Extraction discipline** — anti-hallucination prompt keeps LLM from inventing actors/systems; sense-making rules recover implicit starts/ends and gateway conditions from terse narratives.

**Graph builder that respects semantics**
- Stop-word-aware token overlap prevents generic nouns like *"request"* from cross-wiring every gateway.
- One-to-one rule→gateway assignment with complementary-condition fallback (rejected pairs with approved).
- Parallel fork/join detection; unconditional branches; join gateways vacuum open fork terminals.
- Multi-start / multi-end wiring by nearest-label match.

**BPMN generator**
- Correct task typing (`userTask`, `serviceTask`, `sendTask`, `manualTask`, `receiveTask`, `scriptTask`).
- Lane-aware DI layout with widened spacing to reduce clutter.
- Orthogonal edge routing with staggered gutters — no overlapping arrows on gateways with 3+ outputs.
- Timer events emit ISO-8601 (`P7D`, `PT2H`).

**Review layer**
- Plain-language summary for business readers.
- Quality checks (missing owner, disconnected node, ambiguous gateway, no end event).
- Chat-based fixes with version history.

---

## Testing

```bash
./pie.sh test            # backend + frontend
./pie.sh test b          # backend only
./pie.sh test f          # frontend only
```

Focused test class for the graph engine:

```bash
cd backend
./mvnw -Dtest=CanonicalProcessGraphBuilderTest test
```

---

## Packaging for Submission

```bash
./pie.sh zip                 # -> submission.zip (~5 MB, source only)
./pie.sh zip mypie.zip       # custom output name
```

Excludes: `.git`, `node_modules`, `target`, `dist`, `.env*`, logs, IDE files, build wrappers.
Includes: `RUN_INSTRUCTIONS.txt` at archive root with a self-contained setup guide.

---

## Roadmap (post-hackathon)

- Quality scoring with weighted checks and per-lane heatmap.
- Version diff between BPMN revisions.
- Process-mining hooks (event logs → conformance overlay).
- Enterprise connectors: SAP, Jira, ServiceNow, Confluence.
- Template library for common patterns (approval, procurement, onboarding).
- Automation-readiness scorecard.

---

## Team

**Prompt Cartel** — VWGDS India, Engineering [R&D] (I-DK-R)

| Name              | Focus                                     |
|-------------------|-------------------------------------------|
| Gaidhani, Prajwal | Java, Agentic AI, Python                  |
| Kamble, Jay       | Agentic AI                                |
| Surve, Hrutu      | Java Full Stack + Agentic AI Workflows    |

---

## License

Hackathon MVP + internal innovation demo. Enterprise licensing terms defined at scale-up.

---

## One-Line Pitch

> **P.I.E. transforms unstructured business knowledge into validated, editable, and explainable BPMN 2.0 process models using a four-agent AI workflow.**
