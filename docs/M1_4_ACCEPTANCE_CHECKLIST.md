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
| CI quality gates | **PASS (was PARTIAL)** | Hosted run green on `main` (run 39232339581): java17 (Maven verify + PostgreSQL 16), frontend, browser 3/3, gitleaks clean; OWASP job opt-in pending `NVD_API_KEY` |
| Updated documentation and tracker | PASS | README, setup docs, reports and `PROJECT_STATUS.md` updated |
| No unresolved critical/high security vulnerabilities | PARTIAL | npm audit current: 0 vulnerabilities (all severities). OWASP Maven scan still incomplete without an NVD API key; opt-in CI job ready |

**M1.4.1 totals after this session:** backend 30 + frontend 24 + browser 3 = **57 passed, 0 failed, 0 skipped**. Remaining open gates: live Google sign-in, OWASP NVD scan, hosted CI. See [M1.4.1 certification report](M1_4_1_FINAL_CERTIFICATION_REPORT.md).
