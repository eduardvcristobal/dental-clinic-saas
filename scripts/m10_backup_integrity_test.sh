#!/usr/bin/env bash
# Synthetic offline test: an invalid/incomplete pg_dump must never publish a backup.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/bin" "$work/backups"
cat > "$work/bin/pg_dump" <<'SH'
#!/usr/bin/env bash
for arg in "$@"; do case "$arg" in --file=*) printf 'incomplete' > "${arg#--file=}" ;; esac; done
SH
cat > "$work/bin/pg_restore" <<'SH'
#!/usr/bin/env bash
exit 7
SH
chmod +x "$work/bin/pg_dump" "$work/bin/pg_restore"
if PATH="$work/bin:$PATH" M10_BACKUP_APPROVED=YES M10_DATABASE_URL='postgresql://synthetic.invalid/ci' \
 M10_BACKUP_DESTINATION="$work/backups" bash "$ROOT/scripts/m10_backup.sh" >/dev/null 2>&1; then
 echo 'Invalid pg_restore validation unexpectedly succeeded' >&2
 exit 1
fi
if find "$work/backups" -type f | grep -q .; then
 echo 'Partial/invalid backup must not be published' >&2
 exit 1
fi
echo 'M10 incomplete archive publication regression passed.'
