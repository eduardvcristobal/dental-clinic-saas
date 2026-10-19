# M0.4 Runtime Validation Report

**Date:** 2026-10-19 (Asia/Manila)  
**Status:** **Local runtime gates passed; M0.4 remains partial pending CI execution and browser/Google-provider verification.**

## Environment

- macOS 15.6.1, Apple Silicon
- OpenJDK 17.0.20, Maven 3.9.16
- PostgreSQL 18.6 (Postgres.app)
- Node 24.18.0, npm 11.16.0, Vite 8.3.4, Vitest 5.0.5

## Commands and results

- `cd backend && mvn -o -B -ntp clean verify` with JDK 17 and the two disposable PostgreSQL databases configured through environment variables.
  - **Passed:** 13 tests, 0 failures, 0 errors, 0 skipped; clean compile of 58 Java source files; JAR packaging and Spring Boot repackage.
  - Covers 4 auth unit tests, 2 rate-limit unit tests, 6 HTTP auth/session integration tests, and 1 fresh migration/RLS test.
- `cd frontend && npm ci`
  - **Passed:** installed locked dependencies; audit reported 0 vulnerabilities.
- `cd frontend && npm test && npm run build && npm run lint`
  - **Passed:** 3 frontend API-client tests; TypeScript project build; Vite production build; Oxlint with no warnings.
- Started the packaged Spring Boot app under JDK 17 against `dental_m04_validation` using restricted `dental_app` for runtime traffic and `postgres` for Flyway.
  - **Passed:** startup, Hibernate `ddl-auto=validate`, Flyway validation of all 10 applied migrations, readiness, and restart against the already migrated schema.
  - Unauthenticated `/` returned HTTP 401.
- First full DB connection attempts from the sandbox were blocked; reran with the approved local-network access. No production or clinic database was modified.

## Authentication coverage

The HTTP integration tests use an in-process JWKS endpoint and cryptographically signed test tokens. They exercise successful Google-token verification; malformed, wrong-signature, expired, wrong-issuer, wrong-audience and unverified-email tokens; unknown/inactive staff; clinic mismatch; access-token tampering and expiry; refresh rotation/replay/concurrent refresh; logout; cookie attributes; and rejected Origin values. They call the real Spring endpoints and PostgreSQL-backed service, not mocked success responses.

This verifies the configured verification logic with controlled test keys. It does not verify Google's live JWKS endpoint or a real Google browser account.

## Remaining verification

- GitHub Actions has been authored but has not yet run; external action availability and hosted PostgreSQL 16 results remain unverified.
- No full browser automation of the Google Identity widget or responsive Command Center was run. Frontend API session restoration and retry behavior passed Vitest tests.
- No live Google identity or production OAuth credentials were used or needed for these tests.

M0.4 should remain **in progress** until CI succeeds and the browser/provider-specific checks are reviewed. This report does not claim pilot or production readiness.

## Subsequent platform-admin implementation (2026-10-19)

- Added an allowlisted Google sign-in that issues a tenantless `PLATFORM_ADMIN` access token and clinic-independent rotating refresh session. The frontend now provides `/platform-admin/login` and a protected `/platform-admin` overview.
- `mvn -q -DskipTests compile` passed; `npm run build` passed. The local Spring Boot app restarted successfully against `dental`, applying V11. The unauthenticated `/api/platform-admin/clinics` endpoint returned HTTP 401.
- The earlier automated totals remain **16 passed, 0 failed, 0 skipped, 0 blocked** (13 backend and 3 frontend). Platform-admin-specific automated tests were not run; a real Google browser sign-in was not verified.
- Local backend and frontend Google client IDs now match. Confirm the Google Console JavaScript origin `http://localhost:5173` before signing in.
