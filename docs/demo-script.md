# 🚀 P.I.E. Orchestration CLI (`pie.sh`)

The `pie.sh` script is the central nervous system for developing and demonstrating the Process Intelligence Ecosystem. It handles environment validation, cross-platform compilation, and background process management for the frontend, backend, and infrastructure.

## 📋 Prerequisites

Ensure the following global dependencies are installed and available in your system `PATH`:
*   **Java 17+**
*   **Maven 3.8+** (Native installation via `winget` or `brew`, I used `chocolatey` btw)
*   **Node.js 18+**
*   **Docker** (Required only for the `e2e` profile)

---

## 🎮 How to Run

Before your first run, make the script executable:
`chmod +x pie.sh`

Run the script by passing a command argument:
`./pie.sh [command]`

### Interactive Startup
When you run `./pie.sh start`, the script will prompt you to select an environment profile:

1.  **Local (Default):** Instant startup. Uses an in-memory H2 database. Bypasses Docker entirely. Best for UI/UX testing and rapid API development.
2.  **E2E (End-to-End):** Starts a local PostgreSQL instance via Docker before booting the backend. Best for testing database migrations and production-parity logic.
3.  **Prod:** Bypasses Docker. Connects to the cloud-hosted Render database.

---

## 🧰 Command Reference

| Command | Action | Description |
| :--- | :--- | :--- |
| `verify` | **Pre-flight Check** | Validates that Java, Maven, Node, Docker (if needed), and your API keys are correctly configured. |
| `build` | **Compile Stack** | Installs React dependencies (`npm install`) and compiles the Spring Boot backend (`mvn clean compile`). |
| `start` | **Launch System** | Prompts for a profile, verifies the environment, and launches all required services in the background. |
| `status` | **Health Check** | Displays running Docker containers and active process IDs (PIDs) for the backend and frontend. |
| `logs` | **Tail Output** | Streams combined live console logs from both the frontend and backend. Press `Ctrl+C` to exit. |
| `logs [service]` | **Targeted Logs** | Stream logs for a specific service (e.g., `./pie.sh logs backend` or `./pie.sh logs frontend`). |
| `stop` | **Graceful Shutdown** | Kills the background processes and stops the Docker infrastructure safely. |

---

## 📁 Artifact Locations

The script automatically generates hidden directories in the root of the project to manage state:
*   `.logs/` - Contains `frontend.log` and `backend.log`.
*   `.pids/` - Contains the raw Process IDs for active services, ensuring clean shutdowns.
*   `.env` - (User created) Holds the `GEMINI_API_KEY` and database credentials.