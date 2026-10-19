#!/usr/bin/env bash
# Read-only readiness check. No credentials or token values are printed.
set -u
cd "$(dirname "$0")/.."
failed=0
check() { if "$@" >/dev/null 2>&1; then printf 'READY   %s\n' "$1"; else printf 'BLOCKED %s\n' "$1"; failed=1; fi; }
command -v git >/dev/null 2>&1 || { echo 'BLOCKED git'; exit 1; }
if git remote | grep -q .; then echo 'READY   Git remote configured'; else echo 'BLOCKED Git remote missing (hosted CI not run)'; failed=1; fi
if [ -n "${NVD_API_KEY:-}" ]; then
  echo 'ADVISORY NVD_API_KEY is present, but no Maven scan is implied; the OWASP NVD assessment is deferred to M10 by owner-accepted security exception.'
else
  echo 'ADVISORY OWASP NVD Maven scan deferred to M10 — Production Security Hardening (owner-accepted security exception); this is not a PASS and does not block M1 development preflight.'
fi
if [ -n "${GOOGLE_CLIENT_ID:-}" ] && [ -n "${VITE_GOOGLE_CLIENT_ID:-}" ] && [ "$GOOGLE_CLIENT_ID" = "$VITE_GOOGLE_CLIENT_ID" ]; then
  echo 'READY   Frontend/backend Google OAuth client IDs match'
else
  echo 'BLOCKED Matching GOOGLE_CLIENT_ID and VITE_GOOGLE_CLIENT_ID required for live test'
  failed=1
fi
if command -v java >/dev/null 2>&1 && java -version 2>&1 | head -1 | grep -q '"17\.'; then echo 'READY   Java 17 runtime'; else echo 'BLOCKED Java 17 runtime unavailable'; failed=1; fi
if command -v mvn >/dev/null 2>&1; then echo 'READY   Maven'; else echo 'BLOCKED Maven unavailable'; failed=1; fi
if command -v npm >/dev/null 2>&1; then echo 'READY   npm'; else echo 'BLOCKED npm unavailable'; failed=1; fi
exit "$failed"
