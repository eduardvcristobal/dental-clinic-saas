# M1.4 Final Regression Report

Date: 2026-10-19. This report describes the final local validation run; see [M1.4 Validation Report](M1_4_VALIDATION_REPORT.md) for exact environment, commands and gate-by-gate evidence.

## Automated results

- Backend (OpenJDK 17.0.20, Maven 3.9.16): 26 passed, 0 failed, 0 skipped.
- Frontend (Node 24.18.0, npm 11.16.0): 15 passed across 4 files, 0 failed, 0 skipped.
- `npm run build`: passed (TypeScript and Vite).
- `npm run lint`: completed with two existing React warnings in `frontend/src/workspace/StaffOnboarding.tsx`.
- Total automated tests: 41 passed, 0 failed, 0 skipped, 0 blocked.

The suite was rerun with local networking after the sandbox denied loopback and test HTTP sockets on the first attempt. The successful run did not suppress or disable tests.

## Database and security regression

Fresh V1–V14 migration passed on a temporary PostgreSQL 18.6 cluster. A separate V13 database was upgraded by applying V14 only. One synthetic patient remained stored after upgrade; as `dental_app` with `app.platform_admin=true`, patient and app_user reads returned zero rows. The app role had no superuser or BYPASSRLS privilege. The upgrade schema reported 30 forced-RLS tables and zero remaining `platform_admin_access` policies.

A browser smoke confirmed registration page copy and default-disabled behavior. An HTTP registration attempt returned 403. An unauthenticated Platform Admin API request returned 401.

## Open acceptance items

Live Google sign-in, full browser onboarding, HTTP invitation acceptance concurrency, dentist/staff browser flows, responsive review, saved screenshots, dependency advisory scan and hosted GitHub Actions remain unverified. M1.4 and M1 remain open; M2 is not authorized by the acceptance gate.
