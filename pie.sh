#!/usr/bin/env bash

# P.I.E. Ecosystem - CLI Management Script
# Usage: ./pie.sh [verify|build|start|stop [backend|frontend]|status|logs|test|zip]

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
  elif [ "$PROFILE" = "prod-h2" ] && { [ -z "$VW_LLM_CLIENT_ID" ] || [ -z "$VW_LLM_CLIENT_SECRET" ] || [ -z "$VW_LLM_API_KEY" ]; }; then
    echo -e "${RED}[X] VW_LLM_CLIENT_ID / VW_LLM_CLIENT_SECRET / VW_LLM_API_KEY must all be set in environment / .env file for prod-h2${NC}"
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
      echo -e "${RED}[X] Frontend tests failed. See $LOG_DIR/frontend-tests.log${NC}"
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
  echo "1) Local    (H2 DB + Ollama - Instant start)"
  echo "2) Staging  (PostgreSQL + LLMaaS API Key)"
  echo "3) Prod     (Cloud DB + Gemini API Key)"
  echo "4) Prod-H2  (H2 file DB + VW LLMaaS / OpenAI gpt-4o - no Ollama, no Gemini)"
  
  read -p "Enter choice [1-4] (Default: 1): " choice

  case $choice in
    2) PROFILE="staging" ;;
    3) PROFILE="prod" ;;
    4) PROFILE="prod-h2" ;;
    *) PROFILE="local" ;;
  esac
  
  export SPRING_PROFILE=$PROFILE
  echo -e "${GREEN}Selected Profile: $PROFILE${NC}\n"
}

start_all() {
  choose_profile
  verify_env || exit 1

  echo -e "${YELLOW}--- Starting P.I.E. Ecosystem (Profile: $PROFILE) ---${NC}"

  if [ "$PROFILE" = "staging" ]; then
    echo -e "${YELLOW}[1/3] Starting Database (Docker PostgreSQL if local staging)...${NC}"
    docker-compose up -d 2>/dev/null || true
  else
    echo -e "${YELLOW}[1/3] Skipping Docker (Using H2 or Cloud DB)...${NC}"
  fi

  echo -e "${YELLOW}[2/3] Starting Backend (Spring Boot)...${NC}"
  (
    cd backend
    SPRING_PROFILE=$PROFILE mvn spring-boot:run > "../$LOG_DIR/backend.log" 2>&1 &
    echo $! > "../$PID_DIR/backend.pid"
  )
  echo -e "${GREEN}Backend running in background (PID: $(cat $PID_DIR/backend.pid)). Logs: $LOG_DIR/backend.log${NC}"

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

kill_process_and_port() {
  local service_name=$1
  local pid_file="$PID_DIR/$service_name.pid"
  local port=$2

  if [ -f "$pid_file" ]; then
    local pid=$(cat "$pid_file")
    echo -e "${YELLOW}Stopping $service_name (PID: $pid)...${NC}"
    kill -TERM -$pid 2>/dev/null || kill -TERM $pid 2>/dev/null || true
    sleep 1
    if kill -0 $pid 2>/dev/null; then
      kill -9 -$pid 2>/dev/null || kill -9 $pid 2>/dev/null || true
    fi
    rm -f "$pid_file"
  fi

  # Purge process on the assigned port
  if [ -n "$port" ]; then
    local found_pid=""
    if command -v lsof >/dev/null 2>&1; then
      found_pid=$(lsof -ti :"$port" 2>/dev/null | head -n1 || true)
    elif command -v netstat >/dev/null 2>&1; then
      found_pid=$(netstat -ano 2>/dev/null | grep -E ":$port\s" | awk '{print $NF}' | head -n1 || true)
    elif command -v ss >/dev/null 2>&1; then
      found_pid=$(ss -ltnp 2>/dev/null | grep ":$port" | sed -E 's/.*pid=([0-9]+),.*/\1/' | head -n1 || true)
    fi

    if [ -n "$found_pid" ] && [ "$found_pid" != "0" ]; then
      if command -v taskkill &> /dev/null; then
        taskkill //F //PID "$found_pid" 2>/dev/null || kill -9 "$found_pid" 2>/dev/null || true
      else
        kill -9 "$found_pid" 2>/dev/null || true
      fi
    fi
  fi
}

stop_services() {
  local target=$1
  echo -e "${YELLOW}--- Stopping P.I.E. Ecosystem ($target) ---${NC}"

  if [ "$target" = "backend" ] || [ "$target" = "all" ]; then
    kill_process_and_port "backend" "8080"
    echo -e "${GREEN}[✓] Backend stopped and port 8080 cleared.${NC}"
  fi

  if [ "$target" = "frontend" ] || [ "$target" = "all" ]; then
    kill_process_and_port "frontend" "5173"
    echo -e "${GREEN}[✓] Frontend stopped and port 5173 cleared.${NC}"
  fi

  if [ "$target" = "all" ] && command -v docker &> /dev/null && docker-compose ps &> /dev/null; then
    echo -e "${YELLOW}Stopping Docker Infrastructure...${NC}"
    docker-compose down 2>/dev/null || true
  fi

  echo -e "${GREEN}All requested services stopped cleanly.${NC}"
}

status_all() {
  echo -e "${YELLOW}--- P.I.E. Ecosystem Status ---${NC}"
  
  if command -v docker &> /dev/null; then
    echo -e "\n${YELLOW}Docker Infrastructure:${NC}"
    docker-compose ps 2>/dev/null || echo "No containers running."
  fi

  echo -e "\n${YELLOW}Backend Status (Port 8080):${NC}"
  if [ -f "$PID_DIR/backend.pid" ] && kill -0 $(cat "$PID_DIR/backend.pid") 2>/dev/null; then
    echo -e "${GREEN}Running (PID: $(cat $PID_DIR/backend.pid))${NC}"
  else
    echo -e "${RED}Stopped${NC}"
  fi

  echo -e "\n${YELLOW}Frontend Status (Port 5173):${NC}"
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

zip_submission() {
  local out_name="${1:-submission.zip}"
  echo -e "${YELLOW}--- Packaging source into ${out_name} ---${NC}"

  # Write plain-text run instructions (source-only, no build artifacts included)
  cat > RUN_INSTRUCTIONS.txt <<'EOF'
================================================================
P.I.E. Ecosystem — Source Submission
================================================================

This archive contains SOURCE CODE ONLY. Build artifacts, caches,
node_modules, target/, .git/, logs, storage snapshots, and IDE
files are excluded to keep the archive small.

--------------------------------------
1. Prerequisites
--------------------------------------
- Java 17+
- Maven 3.9+   (or use the bundled ./mvnw wrapper)
- Node.js 18+ and npm
- Docker (optional, only for docker-compose profile)

--------------------------------------
2. Unpack & Configure
--------------------------------------
  unzip submission.zip -d pie-ecosystem
  cd pie-ecosystem
  cp .env.example .env    # if provided; otherwise create .env
  # Set at minimum ONE of:
  #   GEMINI_API_KEY=...                          (prod profile)
  #   VW_LLM_CLIENT_ID / VW_LLM_CLIENT_SECRET /
  #   VW_LLM_API_KEY=...                          (prod-h2 profile)

--------------------------------------
3. Install & Build
--------------------------------------
  chmod +x pie.sh
  ./pie.sh verify        # sanity-check toolchain + env vars
  ./pie.sh build         # mvn compile + npm install

--------------------------------------
4. Run
--------------------------------------
  ./pie.sh start         # prompts for profile (local / staging / prod / prod-h2)

  Frontend:  http://localhost:5173
  Backend:   http://localhost:8080
  H2 Console (local profile): http://localhost:8080/h2-console
             JDBC URL: jdbc:h2:mem:pie_db

--------------------------------------
5. Manage
--------------------------------------
  ./pie.sh status                 # show running services
  ./pie.sh logs [backend|frontend]
  ./pie.sh stop [backend|frontend|all]
  ./pie.sh test [b|f]             # run backend/frontend tests

--------------------------------------
6. Docker (optional, staging profile)
--------------------------------------
  docker-compose up -d            # PostgreSQL for staging profile

================================================================
EOF

  # Prefer native zip; fall back to PowerShell Compress-Archive on Windows.
  rm -f "$out_name"

  local excludes=(
    ".git/*" ".git/**"
    ".logs/*" ".pids/*"
    ".idea/*" ".vscode/*"
    ".env" ".env.*"
    "*.log" "*.pid" "*.tmp" "*.swp"
    "backend/target/*" "backend/target/**"
    "backend/storage/*" "backend/storage/**"
    "backend/.mvn/wrapper/maven-wrapper.jar"
    "frontend/node_modules/*" "frontend/node_modules/**"
    "frontend/dist/*" "frontend/dist/**"
    "frontend/build/*" "frontend/build/**"
    "frontend/.vite/*" "frontend/.vite/**"
    "frontend/coverage/*" "frontend/coverage/**"
    "storage/*" "storage/**"
    "*.zip" "*.tar" "*.tar.gz" "*.7z"
    "**/__pycache__/*" "**/*.pyc"
    ".DS_Store" "Thumbs.db"
  )

  if command -v zip >/dev/null 2>&1; then
    zip -r -9 -q "$out_name" . "${excludes[@]/#/-x}" \
      || zip -r -9 -q "$out_name" . -x "${excludes[@]}"
  elif command -v powershell >/dev/null 2>&1; then
    echo -e "${YELLOW}zip not found — using robocopy + PowerShell Compress-Archive${NC}"

    # Stage OUTSIDE the project so a stray copy can't recurse into itself,
    # and use robocopy to skip node_modules/target/etc. at O(1) per dir.
    local src_win stage_win abs_out out_win
    if command -v cygpath >/dev/null 2>&1; then
      src_win=$(cygpath -w "$(pwd)")
      stage_win=$(cygpath -w "$HOME")"\\.pie-zip-staging\\pie-source"
      abs_out="$(pwd)/$out_name"
      out_win=$(cygpath -w "$abs_out")
    else
      src_win="$(pwd)"
      stage_win="$HOME\\.pie-zip-staging\\pie-source"
      out_win="$(pwd)/$out_name"
    fi

    powershell -NoProfile -Command "
      \$src = '$src_win'
      \$stage = '$stage_win'
      \$out = '$out_win'
      if (Test-Path \$stage) { Remove-Item -Recurse -Force \$stage }
      New-Item -ItemType Directory -Force -Path \$stage | Out-Null
      \$xd = @('.git','.logs','.pids','.idea','.vscode','node_modules','target','dist','build','.vite','coverage','storage','.pie-zip-staging')
      \$xf = @('*.log','*.pid','*.tmp','*.swp','*.zip','.env','.env.*','.DS_Store','Thumbs.db','maven-wrapper.jar')
      \$args = @(\$src, \$stage, '/E','/NFL','/NDL','/NJH','/NJS','/NP','/R:1','/W:1','/XD') + \$xd + @('/XF') + \$xf
      & robocopy @args | Out-Null
      if (Test-Path \$out) { Remove-Item -Force \$out }
      Compress-Archive -Path (Join-Path \$stage '*') -DestinationPath \$out -CompressionLevel Optimal -Force
      Remove-Item -Recurse -Force \$stage
    " 2>&1 | grep -v '^$' || true
  else
    echo -e "${RED}[X] Neither 'zip' nor PowerShell available. Install zip or run on Windows.${NC}"
    return 1
  fi

  if [ -f "$out_name" ]; then
    local size
    if command -v du >/dev/null 2>&1; then
      size=$(du -h "$out_name" | awk '{print $1}')
    else
      size=$(ls -lh "$out_name" | awk '{print $5}')
    fi
    echo -e "${GREEN}[✓] Created ${out_name} (${size})${NC}"
    echo -e "${GREEN}    Includes RUN_INSTRUCTIONS.txt at root.${NC}"
  else
    echo -e "${RED}[X] Zip creation failed.${NC}"
    return 1
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
    stop_services "${2:-all}"
    ;;
  status)
    status_all
    ;;
  logs)
    logs_all "$2"
    ;;
  test)
    run_tests "$2"
    ;;
  zip)
    zip_submission "$2"
    ;;
  *)
    echo -e "${YELLOW}Usage: $0 {verify|build|start|stop [backend|frontend]|status|logs [backend|frontend]|test [b|f]|zip [output.zip]}${NC}"
    exit 1
    ;;
esac