# M1.4 / M1 Final Acceptance Checklist

Status as of 2026-10-19 (after blocker resolution). **M1 is not complete; do not start M2 or activate real patient data.** See M1.4.1 closure report for latest test totals and blockers.

| Requirement | Result | Evidence / remaining action |
|---|---|---|
| Java 17 backend compilation and tests | PASS | 30 tests passed, 0 skipped (OpenJDK 21 runtime per project setup, Maven target Java 17) — includes the previously skipped migration/RLS gate, run on a disposable PostgreSQL |
| Frontend compilation, lint and tests | PASS | 24 tests passed (7 new for clinic-code/tenant resolution); TypeScript/Vite build passed; lint passed with no warnings |
| PostgreSQL V1–V14 fresh migrations | PASS | Isolated disposable PostgreSQL 18.6 cluster (separate port; existing `dental` DB untouched); 14 successful migrations |
| Existing V13 database upgrade | PASS | V14 applied as one migration; synthetic patient row preserved |
| Google authentication smoke test | PARTIAL | Owner completed real Google Platform Admin sign-in and real clinic staff sign-in; the isolated clinic OWNER workspace loaded. Invitation creation through the staff UI returned `ApiError: Bad Request` and persisted no invitation, so recipient sign-in and acceptance remain blocked. Invitee confirmed as `smitycristobal@gmail.com`. See M1.4.4 reconciliation report. |
| JWT and refresh session tests | PASS | Auth integration suite covers signature/claims, expiry, rotation, replay, concurrent refresh and logout |
| Clinic onboarding E2E | PARTIAL | Browser flow now certified with synthetic data (registration-disabled state at 3 viewports); live registration with a real Google token still pending |
| Invitation acceptance, replay and concurrency | PASS (HTTP + browser UI) | Replay, revoked/expired, acceptance lifecycle and concurrent HTTP claim tests pass; browser route renders and strips the token from the URL at 3 viewports |
| Dentist and staff management tests | PASS (controller + browser shell) | Controller authorization tests and browser route/role coverage pass; live authenticated run remains with the Google smoke test |
| Two-clinic isolation | PASS (automated scope) | Restricted-role RLS and tenant relation assertions passed |
| PostgreSQL RLS | PASS | Forced-RLS table count and cross-clinic denial assertions passed |
| Platform Admin least privilege | PASS | V14 boundary tests pass; browser asserts directory-only data with no patient/clinical text |
| Responsive browser regression | **PASS (was BLOCKED)** | 3/3 Playwright tests green twice consecutively; 12 route/role combos × desktop/tablet/mobile with overflow assertions; root causes fixed (API-route glob swallowing `/src/api/*` modules; loopback host treated as clinic slug). Screenshots + traces in `docs/evidence/m1_4_1/` |
| CI quality gates | **PASS — hosted run verified** | Authenticated GitHub Actions page confirms run [`39232339581`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39232339581), push to `main`, SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`: `java17`, `frontend`, `browser`, `security` succeeded; optional `owasp` skipped. Backend 30/30, frontend 24/24, browser suite succeeded; see [hosted CI evidence](M1_4_HOSTED_CI_EVIDENCE.md). This does not cover live OAuth. |
| Updated documentation and tracker | PASS | README, setup docs, reports and `PROJECT_STATUS.md` updated |
| Maven dependency vulnerability assessment | **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** | OWASP NVD scan is not PASS and remains unassessed; deferred to M10. A completed Maven vulnerability assessment with review of applicable high/critical findings is mandatory before production deployment. |
| npm audit | PASS (recorded M1.4.2; not rerun in M1.4.4) | The M1.4.2 report records 0 vulnerabilities across severities. |
| GitHub secret scanning / Gitleaks | PASS | Run 39232339581 security job succeeded; its summary states “No leaks detected.” |
| GitHub dependency review | NOT RUN (push event) | The action remains in the workflow for pull requests. Run 39232339581 was a push, so the PR-only dependency review step did not execute. |

**M1.4.2 local recorded totals (not rerun in M1.4.4):** backend 30 + frontend 24 + browser 3 = **57 passed, 0 failed, 0 skipped**. Hosted run `39232339581` is separately verified with backend 30 + frontend 24 and a successful synthetic browser suite; see [hosted CI evidence](M1_4_HOSTED_CI_EVIDENCE.md). Remaining functional gate: live Google sign-in and invitation acceptance with authorized test accounts. The Maven NVD scan is deferred by owner-accepted exception, is not PASS, and is required before production deployment. See [M1.4.4 reconciliation report](M1_4_4_FINAL_ACCEPTANCE_REPORT.md).
