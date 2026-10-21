#!/usr/bin/env bash
# M10.C1: synthetic/disposable-only AES-256-GCM envelope via OpenSSL enc? OpenSSL enc lacks authenticated GCM.
# Use age (authenticated X25519 encryption) with an approved public recipient.
set -Eeuo pipefail
: "${M10_BACKUP_APPROVED:?Set M10_BACKUP_APPROVED=YES}"
[[ "$M10_BACKUP_APPROVED" == YES ]] || exit 2
: "${M10_DATABASE_URL:?Secret-managed PostgreSQL connection URI required}"
: "${M10_BACKUP_DESTINATION:?Approved encrypted destination required}"
: "${M10_AGE_RECIPIENT:?Approved age public recipient required}"
command -v age >/dev/null || { echo 'age encryption tool required' >&2; exit 2; }
command -v pg_dump >/dev/null || exit 2
command -v pg_restore >/dev/null || exit 2
[[ -d "$M10_BACKUP_DESTINATION" && ! -L "$M10_BACKUP_DESTINATION" ]] || exit 2
[[ "$M10_AGE_RECIPIENT" == age1* ]] || { echo 'Invalid age recipient' >&2; exit 2; }
umask 077
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
plain="$work/archive.dump"
pg_dump --dbname="$M10_DATABASE_URL" --format=custom --no-owner --no-privileges --file="$plain"
pg_restore --list "$plain" >/dev/null
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="$M10_BACKUP_DESTINATION/dental-$stamp.dump.age"
[[ ! -e "$out" ]] || exit 2
partial="$out.partial"
trap 'rm -f -- "$partial"; rm -rf -- "$work"' EXIT
age -r "$M10_AGE_RECIPIENT" -o "$partial" "$plain"
[[ -s "$partial" ]] || exit 2
mv -- "$partial" "$out"
sha256sum "$out" > "$out.sha256"
echo 'Encrypted archive published. Offsite replication, KMS ownership and timed restore still require certification.'
