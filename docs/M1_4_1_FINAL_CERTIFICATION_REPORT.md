# M1.4.1 Final Acceptance Closure & Live E2E Certification

**Date:** 2026-10-19 (updated later the same day after blocker resolution)  
**Source baseline:** `155387d` plus uncommitted M1.4.1 work  
**Decision:** **M1 remains OPEN. Do not start M2 or enable real patient data.**

The browser, responsive-layout and frontend-dependency gates that were previously blocked are now verified. The remaining open gates are live Google sign-in, the OWASP NVD scan and hosted CI.

## Acceptance summary

| Gate | Result | Evidence |
|---|---|---|
| Java 17 backend suite | PASS | 30 tests; 0 failures/errors; **0 skipped** — the migration/RLS gate ran this time on a disposable database |
| Fresh PostgreSQL and upgrade | PASS | Isolated PostgreSQL 18.6 disposable cluster (separate port, existing `dental` DB untouched); fresh V1–V14 run verified; 30/30 reproduced twice on recreated databases |
| Frontend tests/build/lint | PASS | 24 tests (7 new tenant-spoofing tests); TypeScript/Vite production build; Oxlint with no warnings |
| Responsive browser E2E and screenshots | **PASS (was BLOCKED)** | 3/3 Playwright tests green in two consecutive runs; 12 route/role combos × 3 viewports; 4 screenshots + 3 passing-run traces in `docs/evidence/m1_4_1/` |
| GSI repeated initialization | PASS | Unit tests + browser test 3 ("initializes once while navigating between login screens") now green |
| HTTP onboarding and invitation lifecycle | PASS | First owner, invitation acceptance, replay, revocation/expiry, dentist profile, audit event and concurrent claim integration tests |
| Platform Admin authorization | PASS (HTTP + browser) | Directory-only data, clinical API denied; browser asserts no patient/clinical text on the admin screen |
| npm dependency advisory review | **PASS (was BLOCKED)** | `npm audit` on the current tree: 0 vulnerabilities (0 info/low/moderate/high/critical) |
| OWASP dependency scan | OPEN | NVD catalog download (409,901 records) again incomplete without an API key; opt-in CI job added for when `NVD_API_KEY` exists |
| Real Google sign-in | BLOCKED | No live flow performed. No configured OAuth client ID, `frontend/.env`, authorized origin access or authorized test accounts exist in this environment. Exact steps below. |
| Hosted CI | BLOCKED | `git remote -v` is empty. Workflow extended (browser + opt-in OWASP jobs) and YAML-validated; nothing was pushed or run remotely. |

## Root cause correction for the earlier browser failures

The earlier "Vite env did not reach the dev server" diagnosis was wrong. The verified causes:

1. `page.route('**/api/**')` intercepted Vite's own source modules (`/src/api/client.ts` etc.) and served them JSON, blanking the app. Fixed with a pathname predicate.
2. `slugFromHost('127.0.0.1')` returned the clinic slug `'127'`, hiding the clinic-code input. Fixed in `frontend/src/lib/tenant.ts` (IP literals are never tenant slugs) with 7 new unit tests.

Additional product/test hardening done while making the suite green: refresh/logout fixture cookie semantics, single-credential GSI double (no double sign-in race), `signOut` clears the session even if GSI throws, and a mobile grid-blowout CSS fix (`.ws-columns>*{min-width:0}`; document was 606 px wide in a 390 px viewport). Details in [M1_4_1_E2E_RESULTS.md](M1_4_1_E2E_RESULTS.md).

## Executed validation (this session)

- Backend: `mvn test` with disposable PostgreSQL (`M04_AUTH_TEST_*`, `M04_MIGRATION_TEST_*`) — **30 passed, 0 failed, 0 skipped**; reproduced after dropping and recreating both test databases.
- Frontend: `npm test` — **24 passed**; `npm run lint` — no warnings; `npm run build` — production build OK.
- Browser: `npm run test:e2e` — **3 passed** (twice consecutively); evidence screenshots and traces written to `docs/evidence/m1_4_1/`.
- `npm audit` — 0 vulnerabilities on the current tree.
- OWASP dependency-check 12.1.0 — NVD download (409,901 records) started but not completed anonymously; no result claimed.
- Workflow YAML validated after edits.

## Current test totals

- Backend: **30 passed, 0 failed, 0 skipped**.
- Frontend unit: **24 passed, 0 failed, 0 skipped**.
- Browser E2E: **3 passed, 0 failed**.
- **Combined: 57 passed, 0 failed.**
- Hosted CI: **not run** (no remote configured).
- Live Google sign-in: **not run** (no credentials available).

## Google sign-in live check — exactly what is missing

The code paths are complete and verified by tests: the backend (`GoogleIdentityVerifier`) checks signature against Google's JWKS, both documented issuer forms, `aud` equality with `app.auth.google-client-id`, expiry and `email_verified`; the frontend initialises GSI once per client ID. What this environment lacks:

1. An OAuth 2.0 *Web application* client ID from a Google Cloud project the owner controls (APIs & Services → Credentials), with `http://localhost:5173` in *Authorized JavaScript origins*.
2. That client ID placed in `frontend/.env` as `VITE_GOOGLE_CLIENT_ID=...` and in `GOOGLE_CLIENT_ID` for the backend (they must be identical; the backend rejects tokens minted for any other audience).
3. One authorized Google account invited as an active staff member (or allowlisted as Platform Admin) in the clinic under test.
4. A browser signed into that account to complete the consent flow.

None of these can be created or accessed from here; performing the check requires the owner's Google Cloud session.

## Remaining blockers and recommendation

1. Perform the live Google sign-in smoke test once the owner supplies the configured client ID and an authorized test account; record only non-secret outcome evidence.
2. Finish the OWASP NVD scan with an API key (CI job is ready; set `NVD_API_KEY` and `RUN_OWASP_SCAN=true`) and remediate any high/critical findings.
3. Configure a Git remote and run the CI workflow (Java 17 + PostgreSQL 16 + frontend + browser + secret/dependency review) without bypassing required checks.
4. Re-run the M1 acceptance checklist. Keep M2 blocked until every gate passes.

No push was performed. No real Google credential or patient data was used.
