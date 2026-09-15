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
**Target Environment:** AWS EC2 (Frankfurt `t3a.xlarge`, 4 vCPUs, 16GB RAM)
**Configuration File:** `backend/src/main/resources/application-staging.yml`

- **Database:** PostgreSQL
- **AI Provider:** LLMaaS API (OpenAI-Compatible endpoint from Hackathon Organizers)
- **Model:** Configurable via `${LLMAAS_MODEL:gpt-4o-mini}`
- **Purpose:** Testing in a cloud environment that mirrors production closely, validating PostgreSQL schema migrations (`ddl-auto: update`), and integrating with the Hackathon's provided LLM infrastructure.
- **Optimizations:** Enforces a strict `max-tokens: 2048` ceiling to conserve the API key's rate limits and token constraints.
- **Safety:** Ollama and Google GenAI are explicitly disabled.

## 3. Production Profile (`prod`)
**Target Environment:** Render Platform
**Configuration File:** `backend/src/main/resources/application-prod.yml`

- **Database:** PostgreSQL (Render Managed DB)
- **AI Provider:** Google Gemini API
- **Model:** `gemini-3.5-flash`
- **Purpose:** Fully-fledged production deployment.
- **Optimizations:** Enforces a strict `max-tokens: 2048` ceiling to manage Google Gemini API costs and token usage.
- **Safety:** Uses `hibernate.ddl-auto: validate` to prevent accidental schema modifications or data loss in production. Ollama and OpenAI are explicitly disabled.

---

## Usage

To activate a specific profile, set the `SPRING_PROFILES_ACTIVE` environment variable before running the application, or pass it via the command line:

```bash
# Local
export SPRING_PROFILES_ACTIVE=local

# Staging
export SPRING_PROFILES_ACTIVE=staging
export DB_URL=jdbc:postgresql://<ec2-ip>:5432/pie_db
export DB_USER=postgres
export DB_PASS=yourpassword
export LLMAAS_API_KEY=your_hackathon_api_key

# Prod
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://<render-db-url>:5432/pie_db
export DB_USER=postgres
export DB_PASS=yourpassword
export GEMINI_API_KEY=your_gemini_api_key
```

