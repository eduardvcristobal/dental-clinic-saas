# M2.5.1 — Remediation & Full Regression Report

Date: 2026-10-20 · Branch: `main` · Remediation-and-React commit: `a5894c2fe765d3d9b18df3f1c3d3d9894aba364e`

## Changes applied

| File | Change |
|---|---|
| `backend/pom.xml` | Spring Boot parent 3.3.5 → **3.5.16** (latest 3.5 patch); PostgreSQL driver pinned **42.7.14**; patch pins inside the Boot BOM: `tomcat.version=10.1.61`, `jackson-bom.version=2.21.8`, `log4j2.version=2.26.1` |
| `frontend/src/workspace/ClinicWorkspace.tsx` | Removed `setPatient`-in-render/effect churn: patient fetch state now tracked as `loaded = {id, patient}` keyed to the selected id; stale-patient races eliminated by only rendering patient data whose `loaded.id === selectedId`; scoped error display prevents cross-patient error bleed |
| `frontend/src/workspace/PatientRegistry.tsx` | Removed `setLoading` state mutation from effect/render flow; replaced with `activeSearch` request-version tracking; degenerate set-state cycles removed from load/save/archive; duplicating debounce timer kept with version guard so out-of-order responses cannot overwrite the registry |

Rationale detail in `docs/M2_5_1_DEPENDENCY_SECURITY_TRIAGE.md` (§3) and lifecycle notes above. No feature was disabled and no test was skipped or suppressed.

## Regression results (all run 2026-10-20, Asia/Manila, host macOS arm64)

### Backend — Java 17 + PostgreSQL 18.6 (Postgres.app, localhost:5432)

`mvn -B -ntp clean verify` with the M04/M25 disposable-database environment variables set (replicating CI):

- **53 tests run, 0 failures, 0 errors, 0 skipped** — BUILD SUCCESS.
- Databases: production-path `dental`, plus fresh disposable `dental_m04_migration` (V1–V15 from empty), `dental_m04_validation` (auth integration), `dental_m25_upgrade` (V14→V15 data-upgrade test, 1/1 passed). All were new or recreated empty; only synthetic test data.
- Coverage: fresh-schema Flyway V1–V15 + RLS + tenant relationships (`MigrationAndTenantIsolationTest`), auth integration (Google verification, token rotation, refresh replay, cookie flags, Origin checks), patient management controller/security/integration tests, patient-sensitive-logging test.
- Environment note: the 17 env-guarded tests are skipped **only** when `M04_*`/`M25_*` variables are absent; they were enabled here, not disabled. CI always sets them.

### Frontend

- `npx tsc -b`: clean.
- `npm run lint` (oxlint): **clean — the two `react(set-state-in-effect)` warnings from M2.5 are gone.**
- `npm test` (Vitest): **26/26 passed** (6 files).
- `npm run build` (Vite): success.

### Playwright browser

- Local Chromium workflow suite `npm run test:e2e`: **4/4 passed** (43.8 s).
- The 12-engine mocked cross-browser matrix and the 1 live authenticated browser test remain documented in `docs/M2_5_FRONTEND_BROWSER_REGRESSION.md` and were not re-run in this increment (unchanged browser scope rules).

### Totals for this pass

- Local donut: 53 backend + 26 frontend unit + 4 browser = **83 passed, 0 failed, 0 skipped** (+1 V14→V15 upgrade test as part of backend 53).
- Uplift from the M2.5 report: the dependency scan now clears all confirmed-applicable critical/high findings except the upstream-unfixed Spring Framework/Security/Data JPA set (6 artifacts, 23 CVEs; see triage doc §4–§5).

## Out of scope / deferred

- Spring Boot 4.x / Spring Framework 7 migration (major upgrade — requires separate regression cycle).
- Direct-NVD re-scan (prior local feed-stall issue remains; scanner uses the maintained mirror; M10 requires a direct-NVD re-run at deploy time).
