#!/usr/bin/env bash
# M1.4 local verification. Required tools/dependencies are never silently skipped.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail=0
run() {
  local label="$1"; shift
  printf '\n== %s ==\n' "$label"
  if "$@"; then printf '[PASS] %s\n' "$label"; else printf '[FAIL/BLOCKED] %s\n' "$label"; fail=1; fi
}
if ! command -v java >/dev/null || ! java -version 2>&1 | head -1 | grep -q '"17\.'; then
  echo '[BLOCKED] JDK 17 is required'; fail=1
fi
if ! command -v mvn >/dev/null; then echo '[BLOCKED] Maven is required'; fail=1
elif command -v java >/dev/null && java -version 2>&1 | head -1 | grep -q '"17\.'; then
  run 'Java 17 unit/integration and Flyway verification' bash -c 'cd "$1/backend" && mvn -B -ntp clean verify' -- "$ROOT"
fi
if [[ -z "${M04_MIGRATION_TEST_DB_URL:-}" || -z "${M04_MIGRATION_TEST_APP_PASSWORD:-}" ]]; then
  echo '[BLOCKED] Isolated PG migration database variables are not set'; fail=1
fi
if ! command -v npm >/dev/null; then echo '[BLOCKED] npm required'; fail=1
else
  run 'Locked npm dependency install' bash -c 'cd "$1/frontend" && npm ci --no-audit --no-fund' -- "$ROOT"
  if [[ -x "$ROOT/frontend/node_modules/.bin/vite" ]]; then
    run 'Frontend unit tests' bash -c 'cd "$1/frontend" && npm test' -- "$ROOT"
    run 'Frontend production build' bash -c 'cd "$1/frontend" && npm run build' -- "$ROOT"
    run 'Frontend lint' bash -c 'cd "$1/frontend" && npm run lint' -- "$ROOT"
  else echo '[BLOCKED] Frontend checks require installed dependencies'; fail=1; fi
fi
if [[ "$fail" -ne 0 ]]; then echo 'M1.4 CERTIFICATION NOT PASSED'; exit 1; fi
printf 'Local automated checks passed; live Google E2E, hosted CI, concurrency and upgrade-path tests still require independent evidence.\n'
