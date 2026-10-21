#!/usr/bin/env bash
# Verify production-facing scripts refuse unsafe invocations without touching any database.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
reject() {
 if "$@" >/dev/null 2>&1; then
   echo "Unsafe command unexpectedly succeeded: $*" >&2
   exit 1
 fi
}
reject env -u M10_BACKUP_APPROVED bash "$ROOT/scripts/m10_backup.sh"
reject env M10_BACKUP_APPROVED=YES -u M10_DATABASE_URL bash "$ROOT/scripts/m10_backup.sh"
reject env -u M10_RESTORE_APPROVED bash "$ROOT/scripts/m10_restore_disposable.sh"
reject env M10_RESTORE_APPROVED=YES M10_RESTORE_DB=production -u M10_RESTORE_DUMP bash "$ROOT/scripts/m10_restore_disposable.sh"
reject env -u M10_SYNTHETIC_APPROVED bash "$ROOT/scripts/m10_synthetic_query_probe.sh"
reject env M10_SYNTHETIC_APPROVED=YES M10_PG_URL=postgresql://localhost/prod bash "$ROOT/scripts/m10_synthetic_query_probe.sh"
echo 'M10 recovery/performance safety denial tests passed.'
