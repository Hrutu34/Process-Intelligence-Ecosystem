# Epic 3: Process Graph Builder Engine & BPMN Modelling Agent

> **Status:** In Progress / Active  
> **Target Module:** `backend` (Java Spring Boot) & `frontend` (React + TypeScript + BPMN.io)  
> **Key Goal:** Transform structured process knowledge into a validated, canonical directed process graph and generate standards-compliant, editable BPMN 2.0 diagrams.

---

## 1. Executive Summary & Analogy

### What is Epic 3 in Simple Terms?
- **Epic 2 (Before):** Acted like a **transcriptionist & reader** — it read messy documents/text and extracted the ingredients: steps, people, decisions, and systems.
- **Epic 3 (Now):** Acts like the **architect & blueprint generator** — it takes those raw ingredients, figures out what order they connect in (building the mathematical graph), checks for structural integrity (no missing exits or loops), and draws an official, interactive engineering blueprint (**BPMN 2.0 diagram**).

```
   [ Raw Documents / Text ]
              │
              ▼  (Epic 2: Knowledge Extraction)
   [ Extracted Steps, Roles & Decisions ]
              │
              ▼  (Epic 3: Graph Engine & BPMN)
   ┌─────────────────────────────────────────┐
   │ 1. Canonical Graph Builder (Topology)   │
   │ 2. Graph Connectivity Validator         │
   │ 3. BPMN 2.0 XML Engine                  │
   │ 4. Interactive BPMN.io Visualizer       │
   └─────────────────────────────────────────┘
              │
              ▼
   [ Validated, Editable BPMN 2.0 Blueprint ]
```

---

## 2. How "Users", "Roles", and "Performers" Are Represented

In the Process Intelligence Ecosystem, "Users" are modeled at two distinct layers:

### A. Process Actors / Roles (Inside the Process Flow)
When analyzing business procedures (e.g., "The Quality Engineer inspects the engine, then sends it to the Warehouse Manager"), people are represented as follows:

1. **Extraction Level (`ProcessKnowledgeDTO`)**:
   - Captured as entity strings in `actors: string[]` and `roles: string[]`.
2. **Canonical Graph Level (`CanonicalProcessGraph`)**:
   - **Role Nodes (`NodeType.Role`)**: Explicit graph nodes created for each unique actor/role (e.g., `role-quality-engineer`).
   - **Association Edges (`EdgeType.Association`)**: Directed connections linking a role node to its respective activity node (`role-quality-engineer` $\rightarrow$ `activity-inspect-engine`).
   - **Node Metadata (`NodeMetadata.roleRef`)**: Each activity node carries a direct reference to its primary performer.
3. **BPMN 2.0 Model Level**:
   - **Swimlanes (`<bpmn:lane>`)**: Process activities are grouped into horizontal swimlanes corresponding to roles.
   - **Performers (`<bpmn:performer>`)**: Assigned to individual task XML definitions.

### B. Application Users (System Actors)
- Platform users (Process Analysts, Auditors, Operations Leads) who log in to the P.I.E. web application, review extractions, customize graph layouts, and export models.

---

## 3. Epic 3 Architecture & Data Flow

```mermaid
flowchart TD
    subgraph Epic2 ["Epic 2 Output"]
        PK[ProcessKnowledgeDTO JSON]
    end

    subgraph BackendEngine ["Epic 3 Backend (Spring Boot)"]
        Builder[CanonicalProcessGraphBuilder]
        Validator[ProcessGraphValidator]
        Controller[ProcessController /api/v1/process/{id}/graph]
        
        PK --> Builder
        Builder -->|Constructs Nodes & Edges| Graph[CanonicalProcessGraph]
        Graph --> Validator
        Validator -->|Validates Start/End, Cycles, Connectivity| Controller
    end

    subgraph FrontendEngine ["Epic 3 Frontend (React + BPMN.io)"]
        Viewer[ProcessGraphViewer]
        XMLGen[bpmnXmlGenerator.ts]
        Canvas[BpmnIoCanvas / bpmn-js]
        
        Controller -->|Canonical JSON| Viewer
        Viewer --> XMLGen
        XMLGen -->|BPMN 2.0 XML with DI Coordinates| Canvas
        Canvas -->|Render / Zoom / Pan / Export| UserInterface[Interactive BPMN Editor]
    end
```

---

## 4. Key Components & Artifacts

### 4.1 Backend Components (`backend/src/main/java/com/pie/`)

| File / Component | Purpose |
| :--- | :--- |
| [`CanonicalProcessGraphBuilder.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/backend/service/CanonicalProcessGraphBuilder.java) | Converts normalized knowledge entities into a directed graph of Activities, Gateways, Events, Roles, and Systems. |
| [`ProcessGraphValidator.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/backend/service/ProcessGraphValidator.java) | Validates start/end existence, identifies unreachable nodes, cycles, and dangling branches. |
| [`CanonicalProcessGraph.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/shared/dto/CanonicalProcessGraph.java) | Core DTO containing graph ID, process name, node list, edge list, and metadata. |
| [`GraphNode.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/shared/dto/GraphNode.java) & [`GraphEdge.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/shared/dto/GraphEdge.java) | Node schema (Activity, Gateway, Event, Role, System) and edge schema (Sequence, Conditional, Association, Message). |
| [`ProcessController.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/backend/controller/ProcessController.java) | Exposes `GET /api/v1/process/{id}/graph` to build and serve canonical graphs. |

### 4.2 Frontend Components (`frontend/src/`)

| File / Component | Purpose |
| :--- | :--- |
| [`bpmnXmlGenerator.ts`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/frontend/src/utils/bpmnXmlGenerator.ts) | Algorithmic layout calculation and BPMN 2.0 XML generator with diagram interchange (BPMNDI) coordinates. |
| [`BpmnIoCanvas.tsx`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/frontend/src/components/BpmnIoCanvas.tsx) | `bpmn-js` / `bpmn.io` canvas wrapper providing zoom, panning, fit-to-viewport, and export to BPMN XML/SVG. |
| [`ProcessGraphViewer.tsx`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/frontend/src/components/ProcessGraphViewer.tsx) | Multi-tab preview panel (Interactive BPMN Diagram, Graph Topology Inspector, Raw JSON Payload, Process Metrics). |

---

## 5. Epic 3 Task Breakdown & Roadmap

- [x] **E3-T10: Develop Process Graph Builder Engine**
  - Implement node/edge factories for Activities, Gateways, Events, Roles.
  - Implement graph validation (start/end node verification, cycle checks).
  - Add comprehensive unit tests ([`CanonicalProcessGraphBuilderTest.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/test/java/com/pie/backend/service/CanonicalProcessGraphBuilderTest.java)).
- [x] **E3-T11: BPMN 2.0 XML Generator**
  - Synthesize BPMN 2.0 XML structure from canonical graph nodes and edges.
  - Compute 2D coordinates (`BPMNShape`, `BPMNEdge`, waypoints).
- [x] **E3-T12: BPMN.io Viewer Canvas**
  - Integrate `bpmn-js` viewer/modeler with toolbar controls.
  - Provide XML and SVG download options.
- [x] **TASK-012: Start Event Validation Rule**
  - Implemented in [`ProcessQualityValidator.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/backend/service/ProcessQualityValidator.java).
  - Validates Start Event existence and identifies invalid initiation sequences (e.g. process starting with approval without initiation trigger).
- [x] **TASK-013: End Event Validation Rule**
  - Implemented in [`ProcessQualityValidator.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/backend/service/ProcessQualityValidator.java).
  - Detects abrupt terminations, missing End Events, and dead-end activities.
- [x] **TASK-014: Gateway Validation Engine**
  - Implemented in [`ProcessQualityValidator.java`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/backend/src/main/java/com/pie/backend/service/ProcessQualityValidator.java).
  - Validates single branch decisions, missing condition labels, missing rejected/alternative paths, and dead-end branches.
- [x] **Agent 02 Process Intelligence UI**
  - Implemented in [`ProcessIntelligenceAgent.tsx`](file:///d:/vwits%20hackathon/Process-Intelligence-Ecosystem/frontend/src/components/ProcessIntelligenceAgent.tsx) to display quality scores, categorized gap cards, and recommendations.
- [ ] **E3-T13: Multi-Lane Swimlane Routing**
  - Group sequence flows and task nodes into lane partitions based on `roleRef`.
- [ ] **E3-T14: Interactive Graph-to-BPMN Editing & Refinement**
  - Support two-way updates (manual visual modifications reflected back into canonical graph).

---

## 6. How to Run & Verify

1. **Run Backend Tests:**
   ```bash
   cd backend
   ./mvnw test -Dtest=CanonicalProcessGraphBuilderTest,ProcessControllerTest
   ```
2. **Start Development Servers:**
   ```bash
   # In root directory
   ./pie.sh start
   ```
3. **Open Browser:**
   - Navigate to `http://localhost:5173`.
   - In the Process Knowledge Review screen, click **"Build & View Process Graph"** or switch to the **"03 BPMN Modelling"** tab.
