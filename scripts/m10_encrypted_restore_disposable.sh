#!/usr/bin/env bash
# M10.C1: decrypt age archive only to temporary storage and restore to pre-created disposable DB.
set -Eeuo pipefail
[[ "${M10_RESTORE_APPROVED:-}" == YES ]] || { echo 'Explicit restore approval required' >&2; exit 2; }
: "${M10_RESTORE_DB:?}"
[[ "$M10_RESTORE_DB" =~ ^dental_m10_restore_[A-Za-z0-9_]+$ ]] || exit 2
: "${M10_RESTORE_BASE_URL:?}"
: "${M10_ENCRYPTED_ARCHIVE:?}"
: "${M10_AGE_IDENTITY:?}"
[[ -f "$M10_ENCRYPTED_ARCHIVE" && -f "$M10_ENCRYPTED_ARCHIVE.sha256" ]] || exit 2
command -v age >/dev/null && command -v pg_restore >/dev/null || exit 2
(cd "$(dirname "$M10_ENCRYPTED_ARCHIVE")" && sha256sum -c "$(basename "$M10_ENCRYPTED_ARCHIVE").sha256")
work="$(mktemp -d)"
chmod 700 "$work"
trap 'rm -rf -- "$work"' EXIT
start="$SECONDS"
age -d -i "$M10_AGE_IDENTITY" -o "$work/decrypted.dump" "$M10_ENCRYPTED_ARCHIVE"
pg_restore --list "$work/decrypted.dump" >/dev/null
pg_restore --dbname="${M10_RESTORE_BASE_URL%/}/$M10_RESTORE_DB" --no-owner --no-privileges --exit-on-error "$work/decrypted.dump"
echo "Synthetic restore duration seconds: $((SECONDS-start))"
echo 'Check data, Flyway, tenant RLS and service startup before declaring recovery success.'
