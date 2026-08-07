## Local Development Setup

### 1. Start Infrastructure (Postgres & Ollama)

```bash
docker-compose up -d
# Pull your local LLM (first time only)
docker exec -it pie_ollama ollama run llama3
```

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