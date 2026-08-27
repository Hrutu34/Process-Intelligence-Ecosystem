## Local Development Setup

The application has three Spring profiles:

- `local`: H2 in-memory database and Ollama.
- `staging`: cloud PostgreSQL and Groq. This profile does not start or require Docker.
- `prod`: PostgreSQL and Gemini, intended for Render deployment.

For `staging`, set these variables in the root `.env` file. Use the JDBC connection details supplied by your PostgreSQL provider; keep the credentials out of source control.

```bash
SPRING_PROFILE=staging
GROQ_API_KEY=your-groq-api-key
GROQ_MODEL=openai/gpt-oss-20b
DB_URL=jdbc:postgresql://your-host:5432/your-database?sslmode=require
DB_USER=your-database-user
DB_PASS=your-database-password
```

Groq exposes an OpenAI-compatible API, so the backend uses `https://api.groq.com/openai` as its default endpoint. `DB_URL`, `DB_USER`, and `DB_PASS` are intentionally required for `staging`; there is no localhost fallback.

### 1. Start Local Ollama (local profile only)

```bash
# Ensure Ollama is running locally and pull the model (first time only)
ollama run llama3.2:3b
```

The `staging` profile uses the configured cloud PostgreSQL and Groq API directly, so it does not need this step.

### 2. Start Backend (Java Spring Boot)

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Start Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```