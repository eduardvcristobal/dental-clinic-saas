#!/usr/bin/env bash
# M10.C3: dual-replica negative auth probes. Runs only against explicitly approved loopback services.
set -Eeuo pipefail
[[ "${M10_SYNTHETIC_APPROVED:-}" == YES ]] || exit 2
: "${M10_REPLICA_A:?}"
: "${M10_REPLICA_B:?}"
for url in "$M10_REPLICA_A" "$M10_REPLICA_B"; do
 [[ "$url" =~ ^http://127\.0\.0\.1:[0-9]+$ ]] || { echo 'Only loopback replicas allowed' >&2; exit 2; }
 for path in /api/reports/summary?from=2026-01-01\&to=2026-01-31 /api/billing/dashboard /api/platform-admin/clinics; do
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 -H 'Authorization: Bearer invalid-synthetic-token' "$url$path")"
  [[ "$status" == 401 || "$status" == 403 ]] || { echo "Unsafe unauthenticated result $status" >&2; exit 1; }
 done
done
echo 'Dual-replica unauthenticated access denials passed; tenant RLS and shared throttling need separate authorized concurrency tests.'
