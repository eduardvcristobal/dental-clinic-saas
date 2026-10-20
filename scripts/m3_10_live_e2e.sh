#!/usr/bin/env bash
# M3.10 live scheduling E2E: real Spring Boot jar + fresh disposable PostgreSQL database.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PGPORT="${M3_10_PGPORT:-55441}"
DB_NAME="${M3_10_DATABASE_NAME:-dental_m310_browser_$(date +%Y%m%d%H%M%S)}"
APP_PASSWORD="${M3_10_APP_PASSWORD:-${DB_APP_PASSWORD:-}}"
CLIENT_ID="m310-live-test-client"
JWKS_PORT="${M3_10_JWKS_PORT:-17892}"
BACKEND_PORT="${M3_10_BACKEND_PORT:-8083}"
FRONTEND_PORT="${M3_10_FRONTEND_PORT:-5176}"

if [[ ! "$DB_NAME" =~ ^dental_m310_[a-zA-Z0-9_]+$ ]]; then
  echo "Database name must start with dental_m310_." >&2
  exit 2
fi
if [[ -z "$APP_PASSWORD" ]]; then
  echo "Set M3_10_APP_PASSWORD to the password for the restricted dental_app role." >&2
  exit 1
fi
for port in "$JWKS_PORT" "$BACKEND_PORT" "$FRONTEND_PORT"; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port $port is already in use; choose another M3_10_*_PORT before running." >&2
    exit 2
  fi
done

PG_BIN="${M3_10_PG_BIN:-/Applications/Postgres.app/Contents/Versions/18/bin}"
export PGPASSWORD="${M3_10_OWNER_PASSWORD:-}"
"$PG_BIN/createdb" -h 127.0.0.1 -p "$PGPORT" -U postgres "$DB_NAME"

JWKS_LOG="${TMPDIR:-/private/tmp}/m310-live-jwks.log"
BACKEND_LOG="${TMPDIR:-/private/tmp}/m310-live-backend.log"
export M25_JWKS_PORT="$JWKS_PORT" M25_TEST_GOOGLE_CLIENT_ID="$CLIENT_ID"
node "$ROOT/scripts/m2_5_test_jwks.mjs" >"$JWKS_LOG" 2>&1 &
JWKS_PID=$!

export DB_URL="jdbc:postgresql://127.0.0.1:${PGPORT}/${DB_NAME}"
export DB_OWNER_USER=postgres DB_OWNER_PASSWORD="${M3_10_OWNER_PASSWORD:-}" DB_APP_USER=dental_app DB_APP_PASSWORD="$APP_PASSWORD"
export GOOGLE_CLIENT_ID="$CLIENT_ID" GOOGLE_JWK_SET_URI="http://127.0.0.1:${JWKS_PORT}/keys"
export APP_JWT_SECRET="$(openssl rand -hex 32)"
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
  if ! kill -0 "$BACKEND_PID" 2>/dev/null || ! kill -0 "$JWKS_PID" 2>/dev/null; then
    echo "Local test service exited; see private logs in $TMPDIR." >&2
    exit 1
  fi
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
 ('31000000-0000-4000-8000-000000000001','m310-live-a','M3.10 Synthetic Clinic A'),
 ('31000000-0000-4000-8000-000000000002','m310-live-b','M3.10 Synthetic Clinic B');
INSERT INTO branch(id,clinic_id,name) VALUES
 ('31000000-0000-4000-8000-000000000011','31000000-0000-4000-8000-000000000001','Synthetic Main A'),
 ('31000000-0000-4000-8000-000000000012','31000000-0000-4000-8000-000000000002','Synthetic Main B');
INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES
 ('31000000-0000-4000-8000-000000000021','31000000-0000-4000-8000-000000000001','owner@m25.test','test-only','M3.10 Owner','OWNER',true),
 ('31000000-0000-4000-8000-000000000022','31000000-0000-4000-8000-000000000001','frontdesk@m25.test','test-only','M3.10 Front Desk','FRONT_DESK',true),
 ('31000000-0000-4000-8000-000000000023','31000000-0000-4000-8000-000000000001','dentist@m25.test','test-only','M3.10 Dentist','DENTIST',true),
 ('31000000-0000-4000-8000-000000000024','31000000-0000-4000-8000-000000000002','other-owner@m25.test','test-only','Other Clinic Owner','OWNER',true);
INSERT INTO dentist(id,clinic_id,full_name,active) VALUES
 ('31000000-0000-4000-8000-000000000031','31000000-0000-4000-8000-000000000001','Dr. Live Fix',true);
INSERT INTO chair(id,clinic_id,branch_id,name,active) VALUES
 ('31000000-0000-4000-8000-000000000032','31000000-0000-4000-8000-000000000001','31000000-0000-4000-8000-000000000011','Fix Chair',true),
 ('31000000-0000-4000-8000-000000000033','31000000-0000-4000-8000-000000000001','31000000-0000-4000-8000-000000000011','Spare Chair',true);
INSERT INTO branch_operating_hour(clinic_id,branch_id,day_of_week,opens_at,closes_at)
SELECT '31000000-0000-4000-8000-000000000001','31000000-0000-4000-8000-000000000011',gs,'00:00','23:59' FROM generate_series(1,7) gs;
INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name,birth_date) VALUES
 ('31000000-0000-4000-8000-000000000051','31000000-0000-4000-8000-000000000001','M3-000001','Live','Fix','1990-05-05'),
 ('31000000-0000-4000-8000-000000000053','31000000-0000-4000-8000-000000000002','M3-000002','Other','Fix','1985-01-01');
SQL

cd "$ROOT/frontend"
M3_10_BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}" M3_10_JWKS_URL="http://127.0.0.1:${JWKS_PORT}" M3_10_FRONTEND_PORT="$FRONTEND_PORT" M3_10_PGPORT="$PGPORT" M3_10_DB_NAME="$DB_NAME" \
  VITE_API_URL="http://127.0.0.1:${BACKEND_PORT}" M3_10_TEST_GOOGLE_CLIENT_ID="$CLIENT_ID" \
  npx playwright test --config=playwright.m3_10.live.config.ts
