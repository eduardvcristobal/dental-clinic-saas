# M1.4.6 — Final M1 Acceptance Report

**Date:** 2026-10-19  
**Status:** **M1 complete for development acceptance.** M2 has not started. The application is not production-ready; the NVD exception and production gates remain open.

## Result

The live Google invitation workflow, replay rejection, clinic self-registration, and two-clinic tenant boundary were exercised in a disposable local environment. Backend registration now maps duplicate clinic-code conflicts to safe HTTP 409 responses, including a concurrent unique-key race; regression tests cover invalid, duplicate, unverified, and unapproved-origin attempts.

Self-registration was enabled only in the temporary backend process used for testing. The checked-in default and local development setting remain disabled. After testing, the backend was restarted with that default and a registration request returned HTTP 403.

## Evidence summary

| Gate | Result | Evidence |
|---|---|---|
| Real Google Owner sign-in | PASS | Owner authenticated into the first registered test clinic and opened Clinic Settings |
| Real invitation acceptance | PASS | Owner invited the authorized second account as `FRONT_DESK` on `Main Test Branch`; recipient completed Google verification and accepted the invitation. The active membership and branch were verified. The prior M1.4.5 live smoke separately confirmed that this recipient could sign in to the Front Desk workspace. |
| Live invitation replay | PASS | Second in-memory form submitted the same one-time link after acceptance; response was HTTP 403 `Invalid or expired invitation` |
| Replay side effects | PASS | Acceptance timestamp unchanged; one member, one branch assignment, one invitation-created event, one invitation-accepted event |
| Clinic self-registration | PASS (development only) | Two clinics registered with real Google verification; each had one active Owner, one branch, `Asia/Manila`, one Owner assignment, and one creation event |
| Tenant boundary | PASS | Second clinic Owner saw its own Settings; restricted `dental_app` role saw one own branch and zero first-clinic branches (`1 | 0`) |
| Registration disabled after test | PASS | Default/local override false; post-test request returned HTTP 403 `Clinic registration is not open` |
| Backend and PostgreSQL regression | PASS | Java 17.0.20 Maven `clean verify`: 33 tests, 0 failures/errors/skips; fresh PostgreSQL 18.6 auth/migration DBs; V1–V14 and RLS gates |
| Frontend regression | PASS | Node 24.18.0, npm 11.16.0; Oxlint; Vitest 25/25; TypeScript/Vite production build |
| Browser regression | PASS | Playwright 4/4 passed, including role/branch form payload and responsive route checks |
| Hosted CI | PASS | Latest run [`39250767984`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39250767984), SHA `d705257ca701c7c10e12ab5d279acde5631ea6af`: `java17`, `frontend`, `browser`, and `security` succeeded; `owasp` skipped by accepted deferral. Hosted test total: 62 passed, 0 failed/skipped/blocked. |

Total local automated checks: **62 passed, 0 failed, 0 skipped, 0 blocked** (33 backend + 25 frontend + 4 Playwright). Live manual E2E observations are documented separately and are not included in these test totals.

## Registration conflict fix

`POST /api/onboarding/clinics` previously relied on the database’s unique clinic slug constraint. Duplicate codes would fail safely at the database but did not receive a useful conflict response. The controller now maps `DuplicateKeyException` from the authoritative unique constraint to HTTP 409 with a generic clinic-code message. Invalid slug/timezone and invalid identity return safe 400/401 responses; unapproved Origin returns 403. Transaction rollback prevents partial clinic/branch/owner records on failure. The database unique constraint remains the concurrency-safe authority.

## Hosted workflow status

Latest hosted run `39250767984` passed on SHA `d705257ca701c7c10e12ab5d279acde5631ea6af`. An initial run failed because the Gitleaks action lacked the parent commit in a shallow checkout; full history was enabled for that job and the rerun passed with no leaks detected. Dependency Review remains configured for pull requests and did not run on this push. OWASP remains skipped under its accepted deferral. See [hosted CI evidence](M1_4_6_HOSTED_CI_EVIDENCE.md).

## Security and production status

The Maven NVD dependency scan remains **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION**, never PASS. Dependency risk remains unassessed; complete a Maven vulnerability assessment and disposition relevant findings before production deployment. No real patient records were activated. Production security certification, privacy review, operational controls, and deployment validation remain separate work.

## Acceptance recommendation

M1 acceptance criteria are met for development: live Google invitation acceptance/replay protection, isolated clinic self-registration and tenant boundaries, local and hosted regression, and documentation are complete. M1 may be closed for development. Do not start M2 automatically. The NVD exception remains a production gate regardless of M1 status.

Detailed evidence: [live invitation replay](M1_4_6_LIVE_INVITATION_REPLAY.md), [clinic registration E2E](M1_4_6_CLINIC_REGISTRATION_E2E.md), [hosted CI evidence](M1_4_6_HOSTED_CI_EVIDENCE.md), [PROJECT_STATUS.md](../PROJECT_STATUS.md), and the [M1 acceptance checklist](M1_4_ACCEPTANCE_CHECKLIST.md).
