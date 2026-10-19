# M1.4 / M1 Final Acceptance Checklist

Status as of 2026-10-19 (M1.4.5 regression run). **M1 remains open; do not start M2 or activate real patient data.** The live Google invitation and recipient workspace flow succeeded. Live replay of the consumed bearer link was not attempted because the application cleared the one-time token after acceptance; replay rejection is covered by automated regression tests.

| Requirement | Result | Evidence / remaining action |
|---|---|---|
| Java 17 backend compilation and tests | PASS | M1.4.5: OpenJDK 17.0.20 Maven `clean verify`, 31 tests passed, 0 failed/errors/skipped; isolated PostgreSQL V1–V14 migration and RLS gate included |
| Frontend compilation, lint and tests | PASS | M1.4.5: Oxlint passed; Vitest 25/25; TypeScript/Vite production build passed |
| PostgreSQL V1–V14 fresh migrations | PASS | Isolated disposable PostgreSQL 18.6 cluster (separate port; existing `dental` DB untouched); 14 successful migrations |
| Existing V13 database upgrade | PASS | V14 applied as one migration; synthetic patient row preserved |
| Google authentication smoke test | PASS | Real clinic OWNER signed in. The invitee completed Google verification for `smitycristobal@gmail.com`; the recipient then signed in with Google and reached the clinic workspace as `Reception / Front Desk`. No credentials or identity tokens were recorded. |
| JWT and refresh session tests | PASS | Auth integration suite covers signature/claims, expiry, rotation, replay, concurrent refresh and logout |
| Clinic onboarding E2E | PARTIAL | Browser flow now certified with synthetic data (registration-disabled state at 3 viewports); live registration with a real Google token still pending |
| Invitation acceptance, replay and concurrency | PARTIAL — live acceptance plus automated lifecycle regression | Live invitation accepted by the Google-verified recipient; active `FRONT_DESK` membership, assigned Main Test Branch, and one created/accepted audit event verified in `dental_m144_oauth`. Automated tests pass for replay, expiry, revocation, and concurrent acceptance. The live token was cleared after use, so live replay was not attempted. |
| Dentist and staff management tests | PASS (controller + browser shell) | Controller authorization tests and browser route/role coverage pass. The real recipient workspace exposed only Front Desk permitted navigation and loaded the Front Desk command center. |
| Two-clinic isolation | PASS (automated scope) | Restricted-role RLS and tenant relation assertions passed |
| PostgreSQL RLS | PASS | Forced-RLS table count and cross-clinic denial assertions passed |
| Platform Admin least privilege | PASS | V14 boundary tests pass; browser asserts directory-only data with no patient/clinical text |
| Responsive browser regression | PASS | M1.4.5 Playwright 4/4 passed, including OWNER invitation payload and selected role/branch; 12 route/role combos × desktop/tablet/mobile. |
| CI quality gates | **PASS — hosted run verified** | Authenticated GitHub Actions page confirms run [`39232339581`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39232339581), push to `main`, SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`: `java17`, `frontend`, `browser`, `security` succeeded; optional `owasp` skipped. Backend 30/30, frontend 24/24, browser suite succeeded; see [hosted CI evidence](M1_4_HOSTED_CI_EVIDENCE.md). This does not cover live OAuth. |
| Updated documentation and tracker | PASS | README, setup docs, reports and `PROJECT_STATUS.md` updated |
| Maven dependency vulnerability assessment | **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** | OWASP NVD scan is not PASS and remains unassessed; deferred to M10. A completed Maven vulnerability assessment with review of applicable high/critical findings is mandatory before production deployment. |
| npm audit | PASS (recorded M1.4.2; not rerun in M1.4.4) | The M1.4.2 report records 0 vulnerabilities across severities. |
| GitHub secret scanning / Gitleaks | PASS | Run 39232339581 security job succeeded; its summary states “No leaks detected.” |
| GitHub dependency review | NOT RUN (push event) | The action remains in the workflow for pull requests. Run 39232339581 was a push, so the PR-only dependency review step did not execute. |

**M1.4.5 local totals:** backend 31 + frontend 25 + Playwright 4 = **60 passed, 0 failed, 0 skipped, 0 blocked**. Hosted run `39232339581` is separately verified; see [hosted CI evidence](M1_4_HOSTED_CI_EVIDENCE.md). The required live owner-to-recipient sign-in, invitation acceptance, branch assignment, and permitted workspace access succeeded. Live replay of the consumed link remains unverified; automated replay regression passed. Keep M1 open until the remaining live replay evidence is resolved. The Maven NVD scan is deferred by owner-accepted exception, is not PASS, and is required before production deployment. See [M1.4.5 certification report](M1_4_5_FINAL_CERTIFICATION_REPORT.md).
