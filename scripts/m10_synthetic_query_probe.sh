#!/usr/bin/env bash
# M10.12: safe bounded synthetic database query-plan probe (never execute on live DB).
set -Eeuo pipefail
if [[ "${M10_SYNTHETIC_APPROVED:-}" != YES ]]; then echo 'Synthetic benchmark approval required' >&2; exit 2; fi
: "${M10_PG_URL:?Supply disposable PostgreSQL URI}"
if [[ ! "${M10_PG_URL}" =~ dental_m10_(perf|restore)_[A-Za-z0-9_]+ ]]; then
 echo 'Refusing to probe a database without dental_m10_perf_* or dental_m10_restore_* naming' >&2; exit 2
fi
psql "$M10_PG_URL" -v ON_ERROR_STOP=1 <<'SQL'
BEGIN READ ONLY;
SET LOCAL statement_timeout='5000ms';
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
 SELECT id FROM appointment WHERE clinic_id='00000000-0000-4000-8000-000000000001'
 AND starts_at>=now()-interval '30 days'
 AND starts_at<now()+interval '30 days'
 ORDER BY starts_at LIMIT 100;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
 SELECT id FROM invoice WHERE clinic_id='00000000-0000-4000-8000-000000000001'
 AND status NOT IN ('DRAFT','VOID')
 AND issued_at>=now()-interval '30 days' ORDER BY issued_at LIMIT 100;
ROLLBACK;
SQL
echo 'Only synthetic query plans measured. Record dataset size, hardware, latency and indexes for review.'
