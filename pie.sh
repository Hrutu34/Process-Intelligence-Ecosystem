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

PROFILE=${SPRING_PROFILE:-local}

verify_env() {
  echo -e "${YELLOW}--- Verifying Prerequisites & Environment ---${NC}"
  local errors=0

  if ! command -v docker &> /dev/null; then
    echo -e "${RED}[X] Docker is not installed or not in PATH${NC}"
    errors=$((errors+1))
  else
    echo -e "${GREEN}[✓] Docker found${NC}"
  fi

  if ! command -v java &> /dev/null; then
    echo -e "${RED}[X] Java is not installed or not in PATH${NC}"
    errors=$((errors+1))
  else
    echo -e "${GREEN}[✓] Java found ($(java -version 2>&1 | head -n 1))${NC}"
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
    echo -e "${GREEN}[✓] Gemini API Key present${NC}"
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
  (cd backend && ./mvnw clean compile)
  
  echo -e "${YELLOW}2. Installing Frontend dependencies (React + Vite)...${NC}"
  (cd frontend && npm install)
  
  echo -e "${GREEN}Build complete!${NC}\n"
}

start_all() {
  verify_env || exit 1

  echo -e "${YELLOW}--- Starting P.I.E. Ecosystem ---${NC}"

  # 1. Start Database
  echo -e "${YELLOW}[1/3] Starting Database (PostgreSQL container)...${NC}"
  docker-compose up -d

  # 2. Start Backend in background
  echo -e "${YELLOW}[2/3] Starting Backend (Spring Boot - Profile: $PROFILE)...${NC}"
  (
    cd backend
    SPRING_PROFILE=$PROFILE ./mvnw spring-boot:run > "../$LOG_DIR/backend.log" 2>&1 &
    echo $! > "../$PID_DIR/backend.pid"
  )
  echo -e "${GREEN}Backend running in background (PID: $(cat $PID_DIR/backend.pid)). Logs: $LOG_DIR/backend.log${NC}"

  # 3. Start Frontend in background
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

  echo -e "${YELLOW}Stopping Docker Infrastructure...${NC}"
  docker-compose down

  echo -e "${GREEN}All services stopped clean.${NC}"
}

status_all() {
  echo -e "${YELLOW}--- P.I.E. Ecosystem Status ---${NC}"
  
  echo -e "\n${YELLOW}Docker Infrastructure:${NC}"
  docker-compose ps

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
  *)
    echo -e "${YELLOW}Usage: $0 {verify|build|start|stop|status|logs [backend|frontend]}${NC}"
    exit 1
    ;;
esac