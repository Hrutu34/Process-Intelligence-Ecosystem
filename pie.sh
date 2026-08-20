#!/usr/bin/env bash

# P.I.E. Ecosystem - CLI Management Script
# Usage: ./pie.sh [verify|build|start|stop|status|logs]

LOG_DIR=".logs"
PID_DIR=".pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

# Formatting colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Auto-load .env file if present
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# Fallback profile if not starting interactively
PROFILE=${SPRING_PROFILE:-local}

verify_env() {
  echo -e "${YELLOW}--- Verifying Prerequisites & Environment ---${NC}"
  local errors=0

  # Only enforce Docker if running the e2e profile
  if [ "$PROFILE" = "e2e" ]; then
    if ! command -v docker &> /dev/null; then
      echo -e "${RED}[X] Docker is not installed or not in PATH (Required for e2e)${NC}"
      errors=$((errors+1))
    else
      echo -e "${GREEN}[✓] Docker found${NC}"
    fi
  fi

  if ! command -v java &> /dev/null; then
    echo -e "${RED}[X] Java is not installed or not in PATH${NC}"
    errors=$((errors+1))
  else
    echo -e "${GREEN}[✓] Java found ($(java -version 2>&1 | head -n 1))${NC}"
  fi

  if ! command -v mvn &> /dev/null; then
    echo -e "${RED}[X] Maven (mvn) is not installed natively or not in PATH${NC}"
    errors=$((errors+1))
  else
    # Extract just the version line to keep the logs clean
    echo -e "${GREEN}[✓] Maven found ($(mvn --version 2>&1 | head -n 1))${NC}"
  fi

  if ! command -v node &> /dev/null; then
    echo -e "${RED}[X] Node.js is not installed or not in PATH${NC}"
    errors=$((errors+1))
  else
    echo -e "${GREEN}[✓] Node.js found ($(node -v))${NC}"
  fi

  if [ "$PROFILE" = "prod" ] && [ -z "$GEMINI_API_KEY" ]; then
    echo -e "${RED}[X] GEMINI_API_KEY is missing from environment / .env file${NC}"
    errors=$((errors+1))
  else
    echo -e "${GREEN}[✓] API Key check passed${NC}"
  fi

  if [ $errors -gt 0 ]; then
    echo -e "${RED}Verification failed with $errors error(s). Please fix them before starting.${NC}\n"
    return 1
  fi
  echo -e "${GREEN}All checks passed successfully!${NC}\n"
  return 0
}

build_apps() {
  echo -e "${YELLOW}--- Compiling & Building Applications ---${NC}"
  
  echo -e "${YELLOW}1. Compiling Backend (Java Spring Boot)...${NC}"
  (cd backend && mvn clean compile)
  
  echo -e "${YELLOW}2. Installing Frontend dependencies (React + Vite)...${NC}"
  (cd frontend && npm install)
  
  echo -e "${GREEN}Build complete!${NC}\n"
}

run_tests() {
  TARGET="$1"
  echo -e "${YELLOW}--- Running Test Suites (${TARGET:-all}) ---${NC}"
  verify_env || return 1

  BACK_EXIT=0
  FRONT_EXIT=0

  if [ "$TARGET" = "f" ]; then
    echo -e "${YELLOW}Frontend-only mode selected${NC}"
  elif [ "$TARGET" = "b" ]; then
    echo -e "${YELLOW}Backend-only mode selected${NC}"
  fi

  if [ "$TARGET" = "b" ] || [ -z "$TARGET" ]; then
    echo -e "${YELLOW}1) Backend tests (Maven)...${NC}"
    (cd backend && mvn -q test) > "$LOG_DIR/backend-tests.log" 2>&1
    BACK_EXIT=$?
    if [ $BACK_EXIT -ne 0 ]; then
      echo -e "${RED}[X] Backend tests failed. See $LOG_DIR/backend-tests.log${NC}"
    else
      echo -e "${GREEN}[✓] Backend tests passed. Log: $LOG_DIR/backend-tests.log${NC}"
    fi
  fi

  if [ "$TARGET" = "f" ] || [ -z "$TARGET" ]; then
    echo -e "${YELLOW}2) Frontend tests (npm)...${NC}"
    (cd frontend && npm test --silent) > "$LOG_DIR/frontend-tests.log" 2>&1 || true
    FRONT_EXIT=$?
    if [ $FRONT_EXIT -ne 0 ]; then
      echo -e "${RED}[X] Frontend tests failed or no test script. See $LOG_DIR/frontend-tests.log${NC}"
    else
      echo -e "${GREEN}[✓] Frontend tests passed. Log: $LOG_DIR/frontend-tests.log${NC}"
    fi
  fi

  if [ $BACK_EXIT -ne 0 ] || [ $FRONT_EXIT -ne 0 ]; then
    echo -e "${RED}One or more test suites failed.${NC}"
    return 1
  fi

  echo -e "${GREEN}All test suites passed.${NC}"
  return 0
}

choose_profile() {
  echo -e "${YELLOW}Select the environment profile to run:${NC}"
  echo "1) Local (H2 In-Memory DB - Instant start, no Docker)"
  echo "2) E2E   (Local Docker PostgreSQL - Production parity)"
  echo "3) Prod  (Cloud Database - Connects to Render)"
  
  read -p "Enter choice [1-3] (Default: 1): " choice

  case $choice in
    2)
      PROFILE="e2e"
      ;;
    3)
      PROFILE="prod"
      ;;
    *)
      PROFILE="local"
      ;;
  esac
  
  export SPRING_PROFILE=$PROFILE
  echo -e "${GREEN}Selected Profile: $PROFILE${NC}\n"
}

start_all() {
  # 1. Ask for profile first
  choose_profile

  # 2. Verify based on selected profile
  verify_env || exit 1

  echo -e "${YELLOW}--- Starting P.I.E. Ecosystem (Profile: $PROFILE) ---${NC}"

  # 3. Smart Infrastructure Routing
  if [ "$PROFILE" = "e2e" ]; then
    echo -e "${YELLOW}[1/3] Starting Database (Docker PostgreSQL)...${NC}"
    docker-compose up -d
  else
    echo -e "${YELLOW}[1/3] Skipping Docker (Using H2 or Cloud DB)...${NC}"
  fi

  # 4. Start Backend in background
  echo -e "${YELLOW}[2/3] Starting Backend (Spring Boot)...${NC}"
  (
    cd backend
    SPRING_PROFILE=$PROFILE mvn spring-boot:run > "../$LOG_DIR/backend.log" 2>&1 &
    echo $! > "../$PID_DIR/backend.pid"
  )
  echo -e "${GREEN}Backend running in background (PID: $(cat $PID_DIR/backend.pid)). Logs: $LOG_DIR/backend.log${NC}"

  # 5. Start Frontend in background
  echo -e "${YELLOW}[3/3] Starting Frontend (React + Vite)...${NC}"
  (
    cd frontend
    npm run dev > "../$LOG_DIR/frontend.log" 2>&1 &
    echo $! > "../$PID_DIR/frontend.pid"
  )
  echo -e "${GREEN}Frontend running in background (PID: $(cat $PID_DIR/frontend.pid)). Logs: $LOG_DIR/frontend.log${NC}"

  echo -e "\n${GREEN}=== All Services Started ===${NC}"
  echo -e "Frontend: ${YELLOW}http://localhost:5173${NC}"
  echo -e "Backend:  ${YELLOW}http://localhost:8080${NC}"
  
  if [ "$PROFILE" = "local" ]; then
    echo -e "H2 DB:    ${YELLOW}http://localhost:8080/h2-console${NC} (URL: jdbc:h2:mem:pie_db)"
  fi
  
  echo -e "Run ${YELLOW}./pie.sh status${NC} or ${YELLOW}./pie.sh logs${NC} to monitor."
}

stop_all() {
  echo -e "${YELLOW}--- Stopping P.I.E. Ecosystem ---${NC}"

  if [ -f "$PID_DIR/backend.pid" ]; then
    PID=$(cat "$PID_DIR/backend.pid")
    echo -e "${YELLOW}Stopping Backend (PID: $PID)...${NC}"
    kill $PID 2>/dev/null || true
    rm "$PID_DIR/backend.pid"
  fi

  if [ -f "$PID_DIR/frontend.pid" ]; then
    PID=$(cat "$PID_DIR/frontend.pid")
    echo -e "${YELLOW}Stopping Frontend (PID: $PID)...${NC}"
    kill $PID 2>/dev/null || true
    rm "$PID_DIR/frontend.pid"
  fi

  if command -v docker &> /dev/null && docker-compose ps &> /dev/null; then
    echo -e "${YELLOW}Stopping Docker Infrastructure...${NC}"
    docker-compose down 2>/dev/null || true
  fi

  echo -e "${GREEN}All services stopped clean.${NC}"
}

status_all() {
  echo -e "${YELLOW}--- P.I.E. Ecosystem Status ---${NC}"
  
  if command -v docker &> /dev/null; then
    echo -e "\n${YELLOW}Docker Infrastructure:${NC}"
    docker-compose ps 2>/dev/null || echo "No containers running."
  fi

  echo -e "\n${YELLOW}Backend Status:${NC}"
  if [ -f "$PID_DIR/backend.pid" ] && kill -0 $(cat "$PID_DIR/backend.pid") 2>/dev/null; then
    echo -e "${GREEN}Running (PID: $(cat $PID_DIR/backend.pid))${NC}"
  else
    echo -e "${RED}Stopped${NC}"
  fi

  echo -e "\n${YELLOW}Frontend Status:${NC}"
  if [ -f "$PID_DIR/frontend.pid" ] && kill -0 $(cat "$PID_DIR/frontend.pid") 2>/dev/null; then
    echo -e "${GREEN}Running (PID: $(cat $PID_DIR/frontend.pid))${NC}"
  else
    echo -e "${RED}Stopped${NC}"
  fi
}

kill_backend() {
  echo -e "${YELLOW}--- Kill Backend ---${NC}"
  if [ -f "$PID_DIR/backend.pid" ]; then
    PID=$(cat "$PID_DIR/backend.pid")
    if kill -0 $PID 2>/dev/null; then
      echo -e "${YELLOW}Stopping Backend (PID: $PID)...${NC}"
      kill $PID 2>/dev/null || true
      rm "$PID_DIR/backend.pid" 2>/dev/null || true
      echo -e "${GREEN}Backend process $PID killed.${NC}"
    else
      echo -e "${YELLOW}PID file exists but process $PID is not running. Removing PID file...${NC}"
      rm "$PID_DIR/backend.pid" 2>/dev/null || true
    fi
  else
    echo -e "${YELLOW}No PID file found. Attempting to detect process listening on port 8080...${NC}"

    # Try lsof
    PID_PORT=""
    if command -v lsof >/dev/null 2>&1; then
      PID_PORT=$(lsof -ti :8080 2>/dev/null | head -n1 || true)
    fi

    # Try ss
    if [ -z "$PID_PORT" ] && command -v ss >/dev/null 2>&1; then
      PID_PORT=$(ss -ltnp 2>/dev/null | grep ':8080' | sed -E 's/.*pid=([0-9]+),.*/\1/' | head -n1 || true)
    fi

    # Try netstat (common on Windows/git-bash)
    if [ -z "$PID_PORT" ] && command -v netstat >/dev/null 2>&1; then
      # netstat -ano output: Proto LocalAddress ForeignAddress State PID
      PID_PORT=$(netstat -ano 2>/dev/null | grep -E ':8080\s' | awk '{print $NF}' | head -n1 || true)
    fi

    if [ -n "$PID_PORT" ]; then
      echo -e "${YELLOW}Found process on port 8080 with PID: $PID_PORT. Attempting to kill...${NC}"
      kill $PID_PORT 2>/dev/null || kill -9 $PID_PORT 2>/dev/null || true
      echo -e "${GREEN}Process $PID_PORT killed (if it existed).${NC}"
    else
      echo -e "${RED}Could not detect a process listening on port 8080. Please kill the process manually.${NC}"
    fi
  fi
}

logs_all() {
  SERVICE=$1
  if [ "$SERVICE" = "backend" ]; then
    tail -f "$LOG_DIR/backend.log"
  elif [ "$SERVICE" = "frontend" ]; then
    tail -f "$LOG_DIR/frontend.log"
  else
    echo -e "${YELLOW}Tailing Backend & Frontend logs (Press Ctrl+C to exit)...${NC}"
    tail -f "$LOG_DIR/backend.log" "$LOG_DIR/frontend.log"
  fi
}

case "$1" in
  verify)
    verify_env
    ;;
  build)
    build_apps
    ;;
  start)
    start_all
    ;;
  stop)
    stop_all
    ;;
  status)
    status_all
    ;;
  logs)
    logs_all $2
    ;;
  test)
    run_tests "$2"
    ;;
  kill-backend)
    kill_backend
    ;;
  kill)
    if [ "$2" = "backend" ]; then
      kill_backend
    else
      echo -e "${YELLOW}Usage: $0 kill backend${NC}"
    fi
    ;;
  *)
    echo -e "${YELLOW}Usage: $0 {verify|build|start|stop|status|logs [backend|frontend]|test [b|f]|kill-backend|kill backend}${NC}"
    exit 1
    ;;
esac