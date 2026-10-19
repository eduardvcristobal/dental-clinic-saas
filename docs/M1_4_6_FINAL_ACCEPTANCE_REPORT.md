# M1.4.6 — Final M1 Acceptance Report

**Date:** 2026-10-19  
**Status:** M1 remains open pending hosted GitHub Actions for the M1.4.6 commit. M2 has not started. No production-readiness claim is made.

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
| Hosted CI latest commit | BLOCKED / NOT RUN | Saved GitHub CLI token is invalid; remote DNS resolution was unavailable. Older run `39232339581` is not evidence for M1.4.6. |

Total local automated checks: **62 passed, 0 failed, 0 skipped, 0 blocked** (33 backend + 25 frontend + 4 Playwright). Live manual E2E observations are documented separately and are not included in these test totals.

## Registration conflict fix

`POST /api/onboarding/clinics` previously relied on the database’s unique clinic slug constraint. Duplicate codes would fail safely at the database but did not receive a useful conflict response. The controller now maps `DuplicateKeyException` from the authoritative unique constraint to HTTP 409 with a generic clinic-code message. Invalid slug/timezone and invalid identity return safe 400/401 responses; unapproved Origin returns 403. Transaction rollback prevents partial clinic/branch/owner records on failure. The database unique constraint remains the concurrency-safe authority.

## Hosted workflow status

The workflow is configured for Java 17, PostgreSQL 16, Maven verification/migrations/RLS, Node 24 lint/unit/build, Playwright, and Gitleaks. The optional OWASP NVD job remains conditional and deferred. GitHub CLI reported an invalid saved token, and the attempted remote lookup could not resolve GitHub DNS. No workflow run exists for the current M1.4.6 revision, and the historical successful run on SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9` does not satisfy this gate. See [hosted CI evidence](M1_4_6_HOSTED_CI_EVIDENCE.md).

## Security and production status

The Maven NVD dependency scan remains **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION**, never PASS. Dependency risk remains unassessed; complete a Maven vulnerability assessment and disposition relevant findings before production deployment. No real patient records were activated. Production security certification, privacy review, operational controls, and deployment validation remain separate work.

## Acceptance recommendation

Keep M1 open until the current commit passes hosted CI. Do not start M2 automatically. After hosted workflow evidence is recorded, review the complete checklist and decide development M1 closure. The NVD exception remains a production gate regardless of M1 status.

Detailed evidence: [live invitation replay](M1_4_6_LIVE_INVITATION_REPLAY.md), [clinic registration E2E](M1_4_6_CLINIC_REGISTRATION_E2E.md), [hosted CI evidence](M1_4_6_HOSTED_CI_EVIDENCE.md), [PROJECT_STATUS.md](../PROJECT_STATUS.md), and the [M1 acceptance checklist](M1_4_ACCEPTANCE_CHECKLIST.md).
