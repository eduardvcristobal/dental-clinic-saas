#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
ENV_FILE="$BACKEND_DIR/.env.local"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_STARTUP_TIMEOUT="${BACKEND_STARTUP_TIMEOUT:-120}"

if [[ ! -f "$ENV_FILE" ]]; then
  cat >&2 <<EOF
Missing $ENV_FILE

Create it from backend/.env.example and set your local database credentials,
Google client ID, and a unique APP_JWT_SECRET (at least 32 characters).
EOF
  exit 1
fi

# The private local env file is shell syntax, so values with special characters
# can be quoted normally. It is ignored by git.
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

required_vars=(DB_URL DB_APP_USER DB_APP_PASSWORD DB_OWNER_USER DB_OWNER_PASSWORD GOOGLE_CLIENT_ID APP_JWT_SECRET APP_ALLOWED_ORIGINS)
missing_vars=()
for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    missing_vars+=("$var")
  fi
done
if ((${#missing_vars[@]})); then
  printf 'Missing required settings in %s: %s\n' "$ENV_FILE" "${missing_vars[*]}" >&2
  exit 1
fi
if ((${#APP_JWT_SECRET} < 32)); then
  echo "APP_JWT_SECRET must contain at least 32 characters." >&2
  exit 1
fi

export APP_COOKIE_SECURE="${APP_COOKIE_SECURE:-false}"
export APP_ALLOWED_ORIGINS="${APP_ALLOWED_ORIGINS:-http://localhost:5173}"
export DB_URL DB_APP_USER DB_APP_PASSWORD DB_OWNER_USER DB_OWNER_PASSWORD GOOGLE_CLIENT_ID APP_JWT_SECRET

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "Frontend dependencies are missing. Run 'npm ci' in $FRONTEND_DIR first." >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is required to start the backend." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required to check backend startup." >&2
  exit 1
fi

stop_port_listener() {
  local port="$1" pids pid
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  [[ -z "$pids" ]] && return 0
  printf 'Stopping listener(s) on port %s: %s\n' "$port" "$(printf '%s' "$pids" | tr '\n' ' ')"
  while IFS= read -r pid; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done <<< "$pids"
  for _ in {1..30}; do
    pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    [[ -z "$pids" ]] && return 0
    sleep 0.2
  done
  while IFS= read -r pid; do
    [[ -n "$pid" ]] && kill -KILL "$pid" 2>/dev/null || true
  done <<< "$pids"
}

command -v lsof >/dev/null 2>&1 || { echo "lsof is required to clear existing app ports." >&2; exit 1; }
stop_port_listener "$BACKEND_PORT"
stop_port_listener "$FRONTEND_PORT"

backend_pid=""
frontend_pid=""
cleanup() {
  trap - EXIT INT TERM
  for pid in "$frontend_pid" "$backend_pid"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Starting backend on http://localhost:$BACKEND_PORT"
(cd "$BACKEND_DIR" && mvn -q spring-boot:run -Dspring-boot.run.arguments="--server.port=$BACKEND_PORT") &
backend_pid=$!

echo "Waiting for backend to accept HTTP requests (up to ${BACKEND_STARTUP_TIMEOUT}s)..."
backend_ready=false
for ((elapsed=0; elapsed<BACKEND_STARTUP_TIMEOUT; elapsed++)); do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "Backend exited before it became ready. See the backend startup output above." >&2
    exit 1
  fi
  http_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 2 "http://127.0.0.1:$BACKEND_PORT/api/auth/refresh" || true)"
  if [[ "$http_status" =~ ^[1-5][0-9][0-9]$ ]]; then
    backend_ready=true
    break
  fi
  sleep 1
done

if [[ "$backend_ready" != true ]]; then
  echo "Backend did not become ready within ${BACKEND_STARTUP_TIMEOUT}s; frontend was not started." >&2
  exit 1
fi
echo "Backend is ready."

echo "Starting frontend on http://localhost:$FRONTEND_PORT"
(cd "$FRONTEND_DIR" && npm run dev -- --host 127.0.0.1 --port "$FRONTEND_PORT" --strictPort) &
frontend_pid=$!

echo "Both services are running. Press Ctrl+C to stop them."
while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$frontend_pid" 2>/dev/null; do
  sleep 1
done
echo "A service exited; stopping the other service."
