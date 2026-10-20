#!/usr/bin/env bash
# Isolated, synthetic-only live odontogram E2E against PostgreSQL + Spring Boot.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_NAME="${M4_DATABASE_NAME:-dental_m4_e2e}"
PGHOST="${M4_PGHOST:-127.0.0.1}" PGPORT="${M4_PGPORT:-5432}" PGUSER="${M4_PG_USER:-postgres}"
OWNER_PASSWORD="${M4_PG_PASSWORD:-ci-owner-password}"
APP_PASSWORD="${M4_APP_PASSWORD:-ci-app-password}"
BACKEND_PORT="${M4_BACKEND_PORT:-8081}" JWKS_PORT="${M4_JWKS_PORT:-17890}" FRONTEND_PORT="${M4_FRONTEND_PORT:-5174}"
PG_BIN="${M4_PG_BIN:-}"
if [[ ! "$DB_NAME" =~ ^dental_m4_[A-Za-z0-9_]+$ ]]; then
  echo "Refusing non-M4 disposable database name" >&2
  exit 2
fi
if [[ ! "$APP_PASSWORD" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Use an alphanumeric-only disposable app password for this test" >&2
  exit 2
fi
pgcmd() { if [[ -n "$PG_BIN" ]]; then "$PG_BIN/$1" "${@:2}"; else "$@"; fi; }
export PGPASSWORD="$OWNER_PASSWORD"
if [[ "$(pgcmd psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -Atqc "SELECT 1 FROM pg_roles WHERE rolname='dental_app'")" != "1" ]]; then
 pgcmd psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -v ON_ERROR_STOP=1 -c "CREATE ROLE dental_app LOGIN PASSWORD '$APP_PASSWORD' NOSUPERUSER NOBYPASSRLS"
fi
pgcmd createdb -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$DB_NAME"
JWKS_LOG="${TMPDIR:-/tmp}/m4-live-jwks.log"
BACKEND_LOG="${TMPDIR:-/tmp}/m4-live-backend.log"
export M25_JWKS_PORT="$JWKS_PORT" M25_TEST_GOOGLE_CLIENT_ID='m25-live-test-client'
node "$ROOT/scripts/m2_5_test_jwks.mjs" >"$JWKS_LOG" 2>&1 &
JWKS_PID=$!
export DB_URL="jdbc:postgresql://$PGHOST:$PGPORT/$DB_NAME"
export DB_OWNER_USER="$PGUSER" DB_OWNER_PASSWORD="$OWNER_PASSWORD" DB_APP_USER=dental_app DB_APP_PASSWORD="$APP_PASSWORD"
export GOOGLE_CLIENT_ID='m25-live-test-client' GOOGLE_JWK_SET_URI="http://127.0.0.1:$JWKS_PORT/keys"
export APP_JWT_SECRET='m4-synthetic-jwt-secret-at-least-32-characters' APP_COOKIE_SECURE=false
export APP_ALLOWED_ORIGINS="http://127.0.0.1:$FRONTEND_PORT" SERVER_PORT="$BACKEND_PORT"
export LOGGING_LEVEL_ROOT=WARN
java -jar "$ROOT/backend/target/dental-backend-0.1.0.jar" --logging.level.root=WARN >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
cleanup() { kill "$BACKEND_PID" "$JWKS_PID" 2>/dev/null || true; wait "$BACKEND_PID" "$JWKS_PID" 2>/dev/null || true; }
trap cleanup EXIT INT TERM
ready=0
for _ in $(seq 1 120); do
 code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 2 "http://127.0.0.1:$BACKEND_PORT/api/auth/refresh" || true)"
 if curl --silent --fail "http://127.0.0.1:$JWKS_PORT/keys" >/dev/null && [[ "$code" != 000 ]]; then ready=1; break; fi
 sleep 1
done
if [[ "$ready" != 1 ]]; then
 echo "Synthetic backend/JWKS failed to start; inspect private runner logs" >&2
 exit 1
fi

pgcmd psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO clinic(id,slug,name) VALUES
 ('44000000-0000-4000-8000-000000000001','m4-live-a','M4 Synthetic Clinic A'),
 ('44000000-0000-4000-8000-000000000002','m4-live-b','M4 Synthetic Clinic B');
INSERT INTO branch(id,clinic_id,name) VALUES
 ('44000000-0000-4000-8000-000000000011','44000000-0000-4000-8000-000000000001','Synthetic Branch A'),
 ('44000000-0000-4000-8000-000000000012','44000000-0000-4000-8000-000000000002','Synthetic Branch B');
INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES
 ('44000000-0000-4000-8000-000000000021','44000000-0000-4000-8000-000000000001','dentist@m25.test','test-only','Synthetic Dentist','DENTIST',true),
 ('44000000-0000-4000-8000-000000000022','44000000-0000-4000-8000-000000000001','frontdesk@m25.test','test-only','Synthetic Front Desk','FRONT_DESK',true),
 ('44000000-0000-4000-8000-000000000023','44000000-0000-4000-8000-000000000002','other-owner@m25.test','test-only','Synthetic Other Owner','OWNER',true);
INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES
 ('44000000-0000-4000-8000-000000000031','44000000-0000-4000-8000-000000000001','M4-A1','Synthetic','Chart Patient'),
 ('44000000-0000-4000-8000-000000000032','44000000-0000-4000-8000-000000000002','M4-B1','Other','Clinic Patient');
SQL
cd "$ROOT/frontend"
M4_BACKEND_URL="http://127.0.0.1:$BACKEND_PORT" M4_JWKS_URL="http://127.0.0.1:$JWKS_PORT" M4_FRONTEND_PORT="$FRONTEND_PORT" \
 VITE_API_URL="http://127.0.0.1:$BACKEND_PORT" npx playwright test --config=playwright.m4.live.config.ts
