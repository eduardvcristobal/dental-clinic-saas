# M1.4 / M1 Final Acceptance Checklist

Status as of 2026-10-19. **M1 is not complete; do not start M2 or activate real patient data.**

| Requirement | Result | Evidence / remaining action |
|---|---|---|
| Java 17 backend compilation and tests | PASS | 26 tests passed on OpenJDK 17.0.20 / Maven 3.9.16 |
| Frontend compilation, lint and tests | PASS WITH WARNINGS | 15 tests passed; TypeScript/Vite build passed; 2 React lint warnings remain |
| PostgreSQL V1–V14 fresh migrations | PASS | Isolated PostgreSQL 18.6 test DB; 14 successful migrations |
| Existing V13 database upgrade | PASS | V14 applied as one migration; synthetic patient row preserved |
| Google authentication smoke test | BLOCKED | Real Google login not performed; verify authorized origin and correct account |
| JWT and refresh session tests | PASS | Auth integration suite covers signature/claims, expiry, rotation, replay, concurrent refresh and logout |
| Clinic onboarding E2E | BLOCKED | Registration UI and disabled endpoint smoke only; no full authenticated registration |
| Invitation acceptance, replay and concurrency | PARTIAL / BLOCKED | DB atomic one-use/replay regression passed; concurrent HTTP acceptance and browser flows not run |
| Dentist and staff management tests | PARTIAL | Controller authorization tests passed; full authenticated E2E not run |
| Two-clinic isolation | PASS (automated scope) | Restricted-role RLS and tenant relation assertions passed; full role/branch/chair scenario remains untested |
| PostgreSQL RLS | PASS | 30 forced-RLS tables observed; cross-clinic and platform-admin denial assertions passed |
| Platform Admin least privilege | PASS (database boundary) | V14 removed broad policies; platform GUC revealed no patient or user rows; unauthenticated API returned 401 |
| Responsive browser regression | BLOCKED | Only registration route inspected; desktop/tablet/mobile suite not completed |
| CI quality gates | PARTIAL | Workflow configuration reviewed; hosted Actions not run (no remote configured) |
| Updated documentation and tracker | PASS | README, setup docs, reports and `PROJECT_STATUS.md` updated |
| No unresolved critical/high security vulnerabilities | UNVERIFIED | No dependency vulnerability scan was available/run |

**Totals:** backend 26 passed; frontend 15 passed; 41 automated tests passed, 0 failed, 0 skipped, 0 blocked on the successful rerun. See [M1.4 validation report](M1_4_VALIDATION_REPORT.md) for commands, environment and full details.
