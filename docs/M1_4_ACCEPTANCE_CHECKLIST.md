# M1.4 / M1 Final Acceptance Checklist

Status as of 2026-10-19 (M1.4.6). **M1 remains open pending hosted CI for the current commit; do not start M2 or activate real patient data.** Live Google invitation acceptance/replay and two-clinic self-registration were verified in a disposable database.

| Requirement | Result | Evidence / remaining action |
|---|---|---|
| Java 17 backend compilation and tests | PASS | M1.4.5: OpenJDK 17.0.20 Maven `clean verify`, 31 tests passed, 0 failed/errors/skipped; isolated PostgreSQL V1–V14 migration and RLS gate included |
| Frontend compilation, lint and tests | PASS | M1.4.5: Oxlint passed; Vitest 25/25; TypeScript/Vite production build passed |
| PostgreSQL V1–V14 fresh migrations | PASS | Isolated disposable PostgreSQL 18.6 cluster (separate port; existing `dental` DB untouched); 14 successful migrations |
| Existing V13 database upgrade | PASS | V14 applied as one migration; synthetic patient row preserved |
| Google authentication smoke test | PASS | Real authorized clinic OWNER and invitee accounts signed in. The recipient reached the first clinic workspace as `Reception / Front Desk`. No credentials or identity tokens were recorded. |
| JWT and refresh session tests | PASS | Auth integration suite covers signature/claims, expiry, rotation, replay, concurrent refresh and logout |
| Clinic onboarding E2E | PASS (isolated development) | Two clinics were registered with real Google verification in `dental_m146_e2e`. Both had one active OWNER, initial branch, `Asia/Manila` timezone, owner-branch assignment, and `CLINIC_CREATED` audit event. Owner login and Clinic Settings succeeded. Registration was disabled again after testing. |
| Invitation acceptance, replay and concurrency | PASS | Live Google-verified acceptance and same-link replay: replay returned HTTP 403 `Invalid or expired invitation`. Before/after database checks confirm unchanged acceptance timestamp and exactly one staff membership, branch assignment, invitation-created event, and invitation-accepted event. Automated expiry, revocation, replay, and concurrent acceptance tests passed. |
| Dentist and staff management tests | PASS (controller + browser shell) | Controller authorization tests and browser route/role coverage pass. The real recipient workspace exposed only Front Desk permitted navigation and loaded the Front Desk command center. |
| Two-clinic isolation | PASS | Live `dental_app` role scoped to clinic two saw its one branch and zero clinic-one branches (`1 | 0`); automated tenant/RLS assertions also passed |
| PostgreSQL RLS | PASS | Forced-RLS table count and cross-clinic denial assertions passed |
| Platform Admin least privilege | PASS | V14 boundary tests pass; browser asserts directory-only data with no patient/clinical text |
| Responsive browser regression | PASS | M1.4.5 Playwright 4/4 passed, including OWNER invitation payload and selected role/branch; 12 route/role combos × desktop/tablet/mobile. |
| CI quality gates | **BLOCKED — latest commit unverified** | Historical run [`39232339581`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39232339581) passed on older SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`; it does not certify M1.4.6. CLI token is invalid and GitHub DNS was unavailable during the remote check. No new run is claimed. See [M1.4.6 hosted CI evidence](M1_4_6_HOSTED_CI_EVIDENCE.md). |
| Updated documentation and tracker | PASS | README, setup docs, reports and `PROJECT_STATUS.md` updated |
| Maven dependency vulnerability assessment | **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** | OWASP NVD scan is not PASS and remains unassessed; deferred to M10. A completed Maven vulnerability assessment with review of applicable high/critical findings is mandatory before production deployment. |
| npm audit | PASS (recorded M1.4.2; not rerun in M1.4.4) | The M1.4.2 report records 0 vulnerabilities across severities. |
| GitHub secret scanning / Gitleaks | PASS | Run 39232339581 security job succeeded; its summary states “No leaks detected.” |
| GitHub dependency review | NOT RUN (push event) | The action remains in the workflow for pull requests. Run 39232339581 was a push, so the PR-only dependency review step did not execute. |

**M1.4.6 local totals:** backend 33 + frontend 25 + Playwright 4 = **62 passed, 0 failed, 0 skipped, 0 blocked**. Live Google invitation acceptance and replay rejection passed, including no duplicate membership or assignment. Two-clinic registration and RLS isolation passed. Keep M1 open until hosted CI passes on the M1.4.6 commit. Maven NVD remains **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION**, not PASS, and a Maven vulnerability assessment is required before production deployment. See [M1.4.6 final acceptance report](M1_4_6_FINAL_ACCEPTANCE_REPORT.md).
