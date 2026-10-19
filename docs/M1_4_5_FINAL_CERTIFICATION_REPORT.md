# M1.4.5 — Invitation Creation Fix & Live E2E Certification

**Date:** 2026-10-19
**Status:** M1 remains open. Do not start M2, activate real patient records, or describe the application as production-ready.
**Scope:** Invitation creation repair, authorized live Google owner-to-staff flow, automated regression, evidence reconciliation.

## Executive result

The authenticated invitation failure is fixed and its root cause is documented below. A real clinic OWNER created an invitation for the owner-authorized test account `smitycristobal@gmail.com`; the invitee completed Google verification, accepted it, signed in, and reached the Front Desk workspace. The assigned branch and audit events were confirmed in the isolated development clinic database.

Automated invitation tests pass for creation, validation, duplicate handling, authorization, tenant boundaries, expiry, revocation, replay, and concurrent acceptance. We did not reuse the live token: successful acceptance clears the bearer token from the page URL and component state, and it was not retained or extracted. Therefore the live replay step is still unverified and M1 remains open under the requested acceptance rule.

## Root cause

The authenticated OWNER request reached the intended invitation API with a valid active branch and the expected email, but an empty role: `Invite[email=smitycristobal@gmail.com, role=, branchId=<valid UUID>]`. Server-side validation rejected the request before any invitation or clinic admin event was written. The response surfaced only a generic `HTTP 400 — ApiError: Bad Request`, so it did not identify the missing role. This was not a tenant-context, branch-identifier, database constraint, or RLS failure. The evidence does not establish why the UI previously allowed the role to be empty.

## Fix and security properties

- Backend validates email, role, and branch explicitly and returns safe, actionable `ProblemDetail` messages; unexpected failures do not reveal SQL or internal exception data.
- Only `ADMIN`, `DENTIST`, and `FRONT_DESK` are accepted as invitation roles. The frontend requires an explicit role selection.
- Branches must be active and belong to the authenticated clinic. Clinic identity continues to come only from the authenticated principal; request data cannot select a tenant.
- Duplicate pending invitations return HTTP 409. A clinic-row lock serializes duplicate creation to close the concurrent-creation gap.
- The token remains random and one-time, is stored only as a hash, and is removed from the URL after opening/acceptance. The UI presents copy/open actions without rendering the raw link as text.

## Live evidence

Environment: isolated development clinic `m1-oauth-acceptance-2026`; branch `Main Test Branch`; local development database `dental_m144_oauth`. No real patient records were activated.

1. Owner signed in to the clinic using real Google authentication.
2. Owner created a `FRONT_DESK` invitation for the authorized invitee `smitycristobal@gmail.com`, assigned to `Main Test Branch`.
3. Invitee completed Google’s verification challenge and accepted the invitation.
4. The invitee then signed in through Google. The app displayed the clinic command center with role label `Reception / Front Desk`; visible navigation was limited to permitted Front Desk workspace links.
5. Read-only database verification showed an active `FRONT_DESK` membership for the invitee, the expected branch assignment, an accepted/unrevoked/unexpired invitation, and exactly one invitation-created and one invitation-accepted event.
6. Live token reuse was not attempted. Acceptance clears the token from the current route and application state. The token was not recovered from browser state, logs, or database storage. Automated HTTP replay rejection passed.

Credentials, passwords, Google identity tokens, and invitation token values were not included in reports or test output.

## Automated validation evidence

| Area | Result |
|---|---|
| Java 17 / Maven `clean verify` | 31 passed, 0 failed, 0 errors, 0 skipped |
| PostgreSQL | Fresh isolated PostgreSQL 18.6 database; V1–V14 migrations and restricted application-role RLS checks passed |
| Invitation API regression | Valid creation/persistence/audit, duplicate pending, invalid role, missing role, foreign-clinic branch, unauthenticated 401, authenticated unauthorized 403; no cross-clinic invitation row |
| Invitation lifecycle | Existing HTTP integration coverage passed for wrong recipient, successful acceptance and branch assignment, replay rejection, expiry, revocation, and concurrent acceptance |
| Frontend | Oxlint passed; Vitest 25/25; TypeScript check and Vite production build passed |
| Playwright | 4/4 passed, including role/branch payload and responsive route regression |
| Total local checks | **60 passed, 0 failed, 0 skipped, 0 blocked** (31 backend + 25 frontend + 4 Playwright) |

Hosted CI evidence is historical run [`39232339581`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39232339581), commit `2791a1c578f19174ea609b53ad84ed656fc9faf9`. Its `java17`, `frontend`, `browser`, and `security` jobs succeeded; optional `owasp` was skipped. It does not validate this M1.4.5 commit. See [hosted CI evidence](M1_4_HOSTED_CI_EVIDENCE.md).

## OWASP NVD dependency assessment

**DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION. Not PASS.** The OWASP Maven/NVD scan remains deferred to M10 — Production Security Hardening. Maven dependency vulnerability exposure remains unassessed. Before production deployment, complete a Maven vulnerability assessment, review applicable findings, and document remediation or disposition. This exception does not disable other security tests, GitHub dependency review, or secret scanning, and does not make the application production-ready.

## Remaining gate and recommendation

M1 remains open until live replay of a consumed invitation is evidenced or the acceptance criterion is explicitly closed through an equivalent approved live validation. Automated replay coverage is green. No M2 work should start, and no real patient data should be activated. Keep the NVD/Maven vulnerability assessment as a mandatory M10 production gate.

See [PROJECT_STATUS.md](../PROJECT_STATUS.md) and the [M1 acceptance checklist](M1_4_ACCEPTANCE_CHECKLIST.md) for current milestone state.
