# M1.4 / M1 Final Acceptance Checklist

Status as of 2026-10-19 (after blocker resolution). **M1 is not complete; do not start M2 or activate real patient data.** See M1.4.1 closure report for latest test totals and blockers.

| Requirement | Result | Evidence / remaining action |
|---|---|---|
| Java 17 backend compilation and tests | PASS | 30 tests passed, 0 skipped (OpenJDK 21 runtime per project setup, Maven target Java 17) — includes the previously skipped migration/RLS gate, run on a disposable PostgreSQL |
| Frontend compilation, lint and tests | PASS | 24 tests passed (7 new for clinic-code/tenant resolution); TypeScript/Vite build passed; lint passed with no warnings |
| PostgreSQL V1–V14 fresh migrations | PASS | Isolated disposable PostgreSQL 18.6 cluster (separate port; existing `dental` DB untouched); 14 successful migrations |
| Existing V13 database upgrade | PASS | V14 applied as one migration; synthetic patient row preserved |
| Google authentication smoke test | BLOCKED | Code paths verified by tests (signature/issuer/audience/expiry/email_verified; GSI init-once in browser test 3). Live login needs: owner's OAuth Web client ID with `http://localhost:5173` authorized, same ID in `frontend/.env` + backend `GOOGLE_CLIENT_ID`, one invited test account |
| JWT and refresh session tests | PASS | Auth integration suite covers signature/claims, expiry, rotation, replay, concurrent refresh and logout |
| Clinic onboarding E2E | PARTIAL | Browser flow now certified with synthetic data (registration-disabled state at 3 viewports); live registration with a real Google token still pending |
| Invitation acceptance, replay and concurrency | PASS (HTTP + browser UI) | Replay, revoked/expired, acceptance lifecycle and concurrent HTTP claim tests pass; browser route renders and strips the token from the URL at 3 viewports |
| Dentist and staff management tests | PASS (controller + browser shell) | Controller authorization tests and browser route/role coverage pass; live authenticated run remains with the Google smoke test |
| Two-clinic isolation | PASS (automated scope) | Restricted-role RLS and tenant relation assertions passed |
| PostgreSQL RLS | PASS | Forced-RLS table count and cross-clinic denial assertions passed |
| Platform Admin least privilege | PASS | V14 boundary tests pass; browser asserts directory-only data with no patient/clinical text |
| Responsive browser regression | **PASS (was BLOCKED)** | 3/3 Playwright tests green twice consecutively; 12 route/role combos × desktop/tablet/mobile with overflow assertions; root causes fixed (API-route glob swallowing `/src/api/*` modules; loopback host treated as clinic slug). Screenshots + traces in `docs/evidence/m1_4_1/` |
| CI quality gates | **NOT VERIFIED — conflicting record** | Commit `07cc18f` documents run `39232339581` as green, but the run record, job details, artifacts, and exact tested SHA are not present locally. GitHub API access failed (invalid local `gh` auth / network DNS failure), and the run URL is not publicly accessible. Do not count the run as verified until its actual metadata/logs are available. |
| Updated documentation and tracker | PASS | README, setup docs, reports and `PROJECT_STATUS.md` updated |
| Maven dependency vulnerability assessment | **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** | OWASP NVD scan is not PASS and remains unassessed; deferred to M10. A completed Maven vulnerability assessment with review of applicable high/critical findings is mandatory before production deployment. |
| npm audit | PASS (recorded M1.4.2; not rerun in M1.4.4) | The M1.4.2 report records 0 vulnerabilities across severities. |
| GitHub secret scanning / Gitleaks | NOT VERIFIED | The workflow retains the security job, but run 39232339581 logs are unavailable and no job outcome can be confirmed. |
| GitHub dependency review | NOT VERIFIED | The workflow retains pull-request dependency review; the referenced run evidence is unavailable. |

**M1.4.2 recorded totals (not rerun in M1.4.4):** backend 30 + frontend 24 + browser 3 = **57 passed, 0 failed, 0 skipped**. These are prior-run records, not new M1.4.4 execution results. Open functional/acceptance gates: live Google sign-in and invitation acceptance with authorized test accounts; hosted CI run evidence reconciliation. The Maven NVD scan is deferred by owner-accepted exception, is not PASS, and is required before production deployment. See [M1.4.4 reconciliation report](M1_4_4_FINAL_ACCEPTANCE_REPORT.md).
