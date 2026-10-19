#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PGPORT="${M25_PGPORT:-55439}"
DB_NAME="${M25_DATABASE_NAME:-dental_m25_browser}"
APP_PASSWORD="${M25_APP_PASSWORD:-${DB_APP_PASSWORD:-}}"
CLIENT_ID="m25-live-test-client"
JWKS_PORT="${M25_JWKS_PORT:-17890}"
BACKEND_PORT="${M25_BACKEND_PORT:-8081}"
FRONTEND_PORT="${M25_FRONTEND_PORT:-5174}"

if [[ ! "$DB_NAME" =~ ^dental_m25_[a-zA-Z0-9_]+$ ]]; then
  echo "Database name must start with dental_m25_." >&2
  exit 2
fi
if [[ -z "$APP_PASSWORD" ]]; then
  echo "Set M25_APP_PASSWORD to the password for the restricted dental_app role." >&2
  exit 2
fi

PG_BIN="${M25_PG_BIN:-/Applications/Postgres.app/Contents/Versions/18/bin}"
"$PG_BIN/createdb" -h 127.0.0.1 -p "$PGPORT" -U postgres "$DB_NAME"

JWKS_LOG="${TMPDIR:-/private/tmp}/m25-live-jwks.log"
BACKEND_LOG="${TMPDIR:-/private/tmp}/m25-live-backend.log"
export M25_JWKS_PORT="$JWKS_PORT" M25_TEST_GOOGLE_CLIENT_ID="$CLIENT_ID"
node "$ROOT/scripts/m2_5_test_jwks.mjs" >"$JWKS_LOG" 2>&1 &
JWKS_PID=$!

export DB_URL="jdbc:postgresql://127.0.0.1:${PGPORT}/${DB_NAME}"
export DB_OWNER_USER=postgres DB_OWNER_PASSWORD= DB_APP_USER=dental_app DB_APP_PASSWORD="$APP_PASSWORD"
export GOOGLE_CLIENT_ID="$CLIENT_ID" GOOGLE_JWK_SET_URI="http://127.0.0.1:${JWKS_PORT}/keys"
export APP_JWT_SECRET='m25-local-e2e-jwt-secret-at-least-32-bytes'
export APP_COOKIE_SECURE=false APP_ALLOWED_ORIGINS="http://127.0.0.1:${FRONTEND_PORT}" APP_ONBOARDING_SELF_SERVICE=true
export LOGGING_LEVEL_ROOT=WARN
export SERVER_PORT="$BACKEND_PORT"
unset DEBUG
java -jar "$ROOT/backend/target/dental-backend-0.1.0.jar" --debug=false --logging.level.root=WARN --logging.level.org.springframework.web=WARN --logging.level.org.springframework.http.converter=OFF --spring.main.log-startup-info=false >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
cleanup() {
  kill "$BACKEND_PID" "$JWKS_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$JWKS_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 120); do
  if curl --silent --fail "http://127.0.0.1:${JWKS_PORT}/keys" >/dev/null && curl --silent "http://127.0.0.1:${BACKEND_PORT}/api/auth/refresh" >/dev/null; then
    break
  fi
  sleep 1
done
if ! curl --silent --fail "http://127.0.0.1:${JWKS_PORT}/keys" >/dev/null || ! curl --silent "http://127.0.0.1:${BACKEND_PORT}/api/auth/refresh" >/dev/null; then
  echo "Local test services failed to become ready; see private logs in $TMPDIR." >&2
  exit 1
fi

"$PG_BIN/psql" -h 127.0.0.1 -p "$PGPORT" -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO clinic(id,slug,name) VALUES
 ('25000000-0000-4000-8000-000000000001','m25-live-a','M2.5 Synthetic Clinic A'),
 ('25000000-0000-4000-8000-000000000002','m25-live-b','M2.5 Synthetic Clinic B');
INSERT INTO branch(id,clinic_id,name) VALUES
 ('25000000-0000-4000-8000-000000000011','25000000-0000-4000-8000-000000000001','Synthetic Main A'),
 ('25000000-0000-4000-8000-000000000012','25000000-0000-4000-8000-000000000002','Synthetic Main B');
INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES
 ('25000000-0000-4000-8000-000000000021','25000000-0000-4000-8000-000000000001','owner@m25.test','test-only','M2.5 Owner','OWNER',true),
 ('25000000-0000-4000-8000-000000000022','25000000-0000-4000-8000-000000000001','frontdesk@m25.test','test-only','M2.5 Front Desk','FRONT_DESK',true),
 ('25000000-0000-4000-8000-000000000023','25000000-0000-4000-8000-000000000002','other-owner@m25.test','test-only','Other Clinic Owner','OWNER',true);
INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name,birth_date) VALUES
 ('25000000-0000-4000-8000-000000000031','25000000-0000-4000-8000-000000000002','B-000001','Other','Synthetic','1990-01-01'),
 ('25000000-0000-4000-8000-000000000032','25000000-0000-4000-8000-000000000001','A-000002','Alternate','Synthetic','1992-02-02');
SQL

cd "$ROOT/frontend"
M25_BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}" M25_JWKS_URL="http://127.0.0.1:${JWKS_PORT}" M25_FRONTEND_PORT="$FRONTEND_PORT" \
  VITE_API_URL="http://127.0.0.1:${BACKEND_PORT}" M25_TEST_GOOGLE_CLIENT_ID="$CLIENT_ID" \
  npx playwright test --config=playwright.m2_5.live.config.ts
