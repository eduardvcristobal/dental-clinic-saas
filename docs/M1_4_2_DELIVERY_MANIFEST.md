# M1.4.2 Delivery Manifest

**Date:** 2026-10-19
**Base commit:** `155387d` (docs(m1.4): record browser smoke console warning) + uncommitted M1.4.1 work preserved
**Head commit:** `05c8488` — fix(m1.4.2): certify browser suite, harden tenant/GSI/mobile defects, refresh gates (a docs-only manifest commit follows it)
**Branch:** `main` (local only; no remote configured, nothing pushed)

## Archives

| File | Purpose | SHA-256 |
|---|---|---|
| `dental-clinic-saas-m1.4.2-full.zip` | Complete project including `.git` history (excludes only `node_modules/`, `backend/target/`, `frontend/dist/`, `frontend/test-results/` — all regenerable) | see `dental-clinic-saas-m1.4.2-SHA256SUMS.txt` |
| `dental-clinic-saas-m1.4.2-changed.zip` | Only the 32 files changed between `155387d` and `05c8488` | `dd4e8dde19f53522a6a2aa71aecec4d6cd7b80c21850094576bec9c1037e695e` |

## Contents of the changed-file set

- Product fixes: `frontend/src/lib/tenant.ts` (IP-literal hosts are never clinic slugs), `frontend/src/auth/AuthProvider.tsx` (sign-out clears the session even if GSI throws), `frontend/src/workspace/workspace.css` (mobile grid blow-out), plus the M1.4.1 GSI init-once files (`GoogleCredentialButton.tsx`, `googleIdentity.ts`) and page updates.
- Browser suite: `frontend/e2e/m1_4_1.spec.ts`, `frontend/playwright.config.ts`, `frontend/vite.config.ts` (vitest scoping), `frontend/package.json`.
- Tests: `frontend/src/lib/tenant.test.ts`, `frontend/src/auth/googleIdentity.test.ts`, `backend/src/test/java/com/dentalapp/auth/AuthIntegrationTest.java`.
- Evidence: `docs/evidence/m1_4_1/` (4 screenshots, 3 Playwright traces from passing runs).
- Reports: `docs/M1_4_1_FINAL_CERTIFICATION_REPORT.md`, `docs/M1_4_1_E2E_RESULTS.md`, `docs/M1_4_1_SECURITY_RESULTS.md`, `docs/M1_4_ACCEPTANCE_CHECKLIST.md`, `PROJECT_STATUS.md`.
- CI: `.github/workflows/backend-java17.yml` (browser job + opt-in OWASP job added).

## Validation totals recorded this session

- Backend (Maven, OpenJDK 21 / target 17): **30 passed, 0 failed, 0 skipped** — on a fresh disposable PostgreSQL 18.6 cluster, reproduced after recreating both test databases.
- Frontend: **24 passed** (vitest), lint clean (Oxlint), production build passed.
- Browser: **3 passed** Playwright tests, twice consecutively, with evidence files.
- npm audit: **0 vulnerabilities** (current tree, registry reachable).
- OWASP dependency-check: NVD catalog download incomplete (anonymous throttle); opt-in CI job prepared. No Maven vulnerability count claimed.
- Hosted CI: **not run** — no Git remote configured; nothing pushed.

## Remaining open gates (M1 stays open)

1. Live Google sign-in / invitation acceptance with an authorized OAuth client ID and test account.
2. OWASP NVD scan completion (set `NVD_API_KEY` + `RUN_OWASP_SCAN=true` in the CI repository for the prepared job).
3. Hosted CI run once a remote exists.
