#!/usr/bin/env bash
# M10.8 guarded physical evidence backup; encrypt output outside this script using KMS-controlled storage.
set -Eeuo pipefail
if [[ "${M10_BACKUP_APPROVED:-}" != YES ]]; then echo "Set M10_BACKUP_APPROVED=YES after storage and retention approval" >&2; exit 2; fi
: "${M10_DATABASE_URL:?Set connection URI through secret manager}"
: "${M10_BACKUP_DESTINATION:?Set secure backup output directory}"
if [[ ! -d "$M10_BACKUP_DESTINATION" || -L "$M10_BACKUP_DESTINATION" ]]; then echo 'Destination must be an existing real directory' >&2; exit 2; fi
umask 077
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="$M10_BACKUP_DESTINATION/dental-backup-$stamp.dump"
if [[ -e "$out" ]]; then echo 'Refusing to overwrite existing backup' >&2; exit 2; fi
pg_dump --dbname="$M10_DATABASE_URL" --format=custom --no-owner --no-privileges --file="$out"
chmod 600 "$out"
sha256sum "$out" > "$out.sha256"
echo "Backup created locally. NOT certified encrypted or offsite: transfer only through approved encrypted storage."
echo "Backup artifacts contain highly sensitive patient information. Never upload to CI artifacts or version control."
