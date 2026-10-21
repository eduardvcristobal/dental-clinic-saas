#!/usr/bin/env bash
# M10.8: restore only into a pre-created disposable test DB; never accepts production DB names.
set -Eeuo pipefail
: "${M10_RESTORE_DB:?Disposable database name required}"
: "${M10_RESTORE_DUMP:?Backup dump path required}"
: "${M10_RESTORE_BASE_URL:?Postgres connection base URL required}"
if [[ "${M10_RESTORE_APPROVED:-}" != YES || ! "$M10_RESTORE_DB" =~ ^dental_m10_restore_[a-zA-Z0-9_]+$ ]]; then
 echo "Refusing restore without approval and dental_m10_restore_* disposable database name" >&2; exit 2
fi
if [[ ! -f "$M10_RESTORE_DUMP" || -L "$M10_RESTORE_DUMP" || ! -f "$M10_RESTORE_DUMP.sha256" ]]; then
 echo "Verified regular backup and checksum files required" >&2; exit 2
fi
(cd "$(dirname "$M10_RESTORE_DUMP")" && sha256sum -c "$(basename "$M10_RESTORE_DUMP").sha256")
pg_restore --dbname="${M10_RESTORE_BASE_URL%/}/$M10_RESTORE_DB" --no-owner --no-privileges --exit-on-error "$M10_RESTORE_DUMP"
echo "Disposable restore completed. Verify Flyway history, schema, row counts, FORCE RLS and restricted role separately."
echo "Measure elapsed restore duration and record approved RPO/RTO; never treat this script as a completed disaster recovery drill."
