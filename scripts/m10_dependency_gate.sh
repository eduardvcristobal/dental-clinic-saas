#!/usr/bin/env bash
# M10.C2: enforce npm production dependency audit; use Maven OWASP separately with an NVD key.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
(cd "$ROOT/frontend" && npm audit --omit=dev --audit-level=high)
echo 'Production npm dependency audit passed. Maven OWASP check is a separate mandatory CI job.'
