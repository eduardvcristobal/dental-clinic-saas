#!/usr/bin/env bash
# M10.10 repeatable offline-safe quality gate. Requires installed Java 17, Maven, Node/npm and dependencies.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo 'M10 checks: Java, frontend typecheck, lint, unit tests, build and configuration'
java -version 2>&1 | grep -q '17\.' || { echo 'Java 17 required' >&2; exit 2; }
( cd "$ROOT/backend" && mvn -B -ntp verify )
( cd "$ROOT/frontend" && npm ci && npx tsc -b && npm run lint && npm test && npm run build )
bash -n "$ROOT/scripts/m10_backup.sh" "$ROOT/scripts/m10_restore_disposable.sh"
echo 'Static gates passed. Run authenticated PostgreSQL/Playwright CI and vulnerability checks separately.'
