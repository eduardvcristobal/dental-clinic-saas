# M2.5.1 — Final M2 Acceptance Checklist (evidence-reconciled)

Date: 2026-10-20 · Decision: **M2 COMPLETE FOR DEVELOPMENT** (see Project Status). Not production-ready; M10 mandatory before any real clinical data use.

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | All mandatory runtime tests pass | **PASS** | Backend 53/53 (0 fail, 0 error, 0 skip, JDK 17 + PostgreSQL 18.6, `mvn clean verify`), frontend 26/26 Vitest + tsc + oxlint + Vite build, Playwright Chromium 4/4 local and hosted — `docs/M2_5_1_REMEDIATION_REGRESSION.md` |
| 2 | Fresh migrations V1–V15 from empty DB | **PASS** | `MigrationAndTenantIsolationTest` on disposable `dental_m04_migration`; V14→V15 upgrade test passes on `dental_m25_upgrade` |
| 3 | PostgreSQL tenant isolation / RLS | **PASS** | RLS fail-closed checks, cross-tenant read/write rejection, role checks in `MigrationAndTenantIsolationTest`; duplicated in CI service postgres:16 |
| 4 | Backend auth/authz regression | **PASS** | `AuthIntegrationTest` (Google verification, rotating refresh, replay revocation, cookie/Origin rules) + rate-limit and security unit tests |
| 5 | Patient Registry / Patient 360° regression | **PASS** | Patient controller, care, onboarding, identity review suites all green; live browser scope documented separately in M2.5 |
| 6 | Current-code hosted GitHub Actions passes | **PASS** | Run 39348777480, SHA a5894c2fe765d3d9b18df3f1c3d3d9894aba364e: java17/frontend/browser/security success, owasp skipped (opt-in) — `docs/M2_5_1_HOSTED_CI_EVIDENCE.md` |
| 7 | No known unreviewed critical/high app-security finding | **PASS with documented residual risk** | Confirmed-applicable critical/high matches remediated (Tomcat →10.1.61, Jackson →2.21.8, log4j-api →2.26.1 inside BOM); residual Spring Framework 6.2.19 / Security 6.5.11 / Data JPA 3.5.13 advisories are upstream-unfixed, applicability-analyzed (§4), tracked as M10 re-scan items — `docs/M2_5_1_DEPENDENCY_SECURITY_TRIAGE.md` |
| 8 | No test skipped or suppressed to force green | **PASS** | All env-guarded tests were enabled via required env vars; no suppression XML was introduced; scanner still exits nonzero on residual set (honest result) |
| 9 | React code-quality findings resolved | **PASS** | Both `react(set-state-in-effect)` warnings cleared; no lint warnings remain; race-safety design documented in regression report |
| 10 | Synthetic data only | **PASS** | Disposable local DBs recreated empty; no real patient data introduced anywhere |

## Residual risks / carry-forward

1. **M10 production gate stands**: direct-NVD dependency re-scan with the residual Spring Framework/Security/Data JPA advisories re-checked for upstream fixes (triage doc §5 R1–R4).
2. Live invitation replay checkbox from M1.4.6 remains closed as recorded then; no regression re-run required by M2 scope.
3. Sonatype OSS Index not configured — NVD/KEV-only SCA coverage remains a documented limitation.

## What "complete for development" means

- Safe to continue feature development (M3+) against this codebase and its CI.
- **Not authorized for real patients or production**: clinical/priv/operational certification is explicitly M10. No live patient record was touched in any M2 step.
