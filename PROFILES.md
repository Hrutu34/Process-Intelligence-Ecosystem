# Application Profiles

The Process Intelligence Ecosystem (PIE) uses Spring Boot profiles to manage configurations across different environments. This ensures that the database and AI providers are strictly isolated and optimized for their respective deployment targets.

## 1. Local Profile (`local`)
**Target Environment:** Local Developer Machine
**Configuration File:** `backend/src/main/resources/application-local.yml`

- **Database:** H2 (In-Memory)
- **AI Provider:** Ollama (Local LLM)
- **Model:** `llama3.2:3b`
- **Purpose:** Fast, cost-free local development with zero external dependencies.
- **Optimizations:** `num-predict` set to 4096 and `num-ctx` to 8192 to manage local RAM overhead.
- **Safety:** External APIs (OpenAI/Google) are explicitly disabled.

## 2. Staging Profile (`staging`)
**Target Environment:** AWS EC2 / Staging Cloud Environment
**Configuration File:** `backend/src/main/resources/application-staging.yml`

- **Database:** PostgreSQL
- **AI Provider:** Groq (OpenAI-Compatible endpoint: `https://api.groq.com/openai/v1`)
- **Model:** High-end flagship model (Default: `llama-3.3-70b-versatile`, configurable via `${GROQ_MODEL}`)
- **API Key:** `${GROQ_API_KEY}` (or fallback `${LLMAAS_API_KEY}`)
- **Purpose:** Testing and validating the entire multi-agent pipeline against Groq's high-speed, high-reasoning 70B parameter models with PostgreSQL schema validation (`ddl-auto: update`).
- **Optimizations:** Configured with `max-tokens: 4096` and `temperature: 0.1` for deterministic JSON blueprints and BPMN extraction.
- **Safety:** Ollama and Google GenAI are explicitly disabled in this profile.

## 3. Production Profile (`prod`)
**Target Environment:** Render Platform / Managed Cloud
**Configuration File:** `backend/src/main/resources/application-prod.yml`

- **Database:** PostgreSQL (Render Managed DB)
- **AI Provider:** Google Gemini API
- **Model:** `gemini-3.5-flash`
- **Purpose:** Fully-fledged production deployment.
- **Optimizations:** Enforces a strict `max-tokens: 2048` ceiling to manage Google Gemini API costs and token usage.
- **Safety:** Uses `hibernate.ddl-auto: validate` to prevent accidental schema modifications or data loss in production. Ollama and OpenAI are explicitly disabled.

## 4. Production H2 Profile (`prod-h2`)
**Target Environment:** File-backed On-Premises / Enterprise Demo
**Configuration File:** `backend/src/main/resources/application-prod-h2.yml`

- **Database:** File-based H2 Database (`jdbc:h2:file:./storage/pie_db`)
- **AI Provider:** VW Group LLMaaS (OAuth2 Client-Credentials + `gpt-4o`)
- **Purpose:** Enterprise demonstration without needing external PostgreSQL servers.

---

## Usage

To activate a specific profile, set the `SPRING_PROFILE` or `SPRING_PROFILES_ACTIVE` environment variable before running the application, or pass it via `pie.sh`:

```bash
# Local
export SPRING_PROFILES_ACTIVE=local

# Staging (Groq + PostgreSQL)
export SPRING_PROFILES_ACTIVE=staging
export DB_URL=jdbc:postgresql://<ec2-ip>:5432/pie_db
export DB_USER=postgres
export DB_PASS=yourpassword
export GROQ_API_KEY=gsk_your_groq_api_key
# Optional custom model: export GROQ_MODEL=llama-3.3-70b-versatile

# Prod (Gemini + PostgreSQL)
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://<render-db-url>:5432/pie_db
export DB_USER=postgres
export DB_PASS=yourpassword
export GEMINI_API_KEY=your_gemini_api_key

# Prod-H2 (VW LLMaaS + File H2)
export SPRING_PROFILES_ACTIVE=prod-h2
export VW_LLM_CLIENT_ID=your_idp_client_id
export VW_LLM_CLIENT_SECRET=your_idp_client_secret
export VW_LLM_API_KEY=your_llm_api_key
```
