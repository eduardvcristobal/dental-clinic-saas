# M2.5 — Final Runtime Regression & Patient Management Acceptance

**Date:** 2026-10-19
**Source branch:** main, based on M2.4 commit `be5ee2b`
**Final source commit:** recorded after the M2.5 commit; hosted CI is not available to verify it.
**Decision:** **M2 remains OPEN.** Local development checks passed, but hosted GitHub Actions on the final application SHA is a required gate and is BLOCKED by invalid GitHub CLI credentials/API access.

## Executed acceptance results

| Area | Result | Evidence |
|---|---|---|
| Java 17/Maven backend | PASS — 53 tests, 0 failures, 0 skipped; clean verify and JAR packaging | `docs/evidence/m2_5/backend-summary.txt` |
| PostgreSQL | PASS — fresh V1–V15 migrations, two-clinic tenant isolation, RLS as restricted role, V14→V15 upgrade | `docs/evidence/m2_5/database-summary.txt` |
| Frontend | PASS — 26 Vitest tests, TypeScript/Vite build; lint completed with two React async-loading warnings | `docs/evidence/m2_5/frontend-summary.txt` |
| Authenticated live browser | PASS — 1 Chromium Playwright run against Spring Boot and isolated PostgreSQL; synthetic JWKS only | `docs/evidence/m2_5/live-browser-summary.txt` |
| Mocked browser regression | PASS — 12 tests across Chromium, Firefox and WebKit; responsive desktop/tablet/mobile routes | `docs/evidence/m2_5/mocked-browser-summary.txt` |
| Hosted GitHub Actions | BLOCKED — stored `gh` credential is invalid and GitHub API access failed; no run ID, SHA, or hosted job conclusions verified | `docs/evidence/m2_5/hosted-ci-status.txt` |
| OWASP Maven dependency scan | DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION to M10 | Optional workflow job remains available |

**Combined local test totals:** 92 passed, 0 failed, 0 skipped, 1 blocked release gate (hosted CI). Build and lint checks are not counted as tests. The NVD assessment is deferred separately and is not a pass.

## Fixes made

- Patient updates now require an expected record version and update atomically with `WHERE version=?`; stale edits return 409 rather than overwriting a newer record.
- The Patient Care refresh loads guardians, consent, and medical history together before publishing the new section state, with request-version invalidation on obsolete requests.
- Authentication, session, invitation, patient, guardian, and clinical-history DTO string representations redact sensitive values. Spring MVC request/response body and request-parameter loggers stay at INFO even with ambient DEBUG enabled; regression assertions cover logger levels and DTO redaction.
- Migration/RLS tests now cover V15 records and use savepoints around expected database denials so a deliberate RLS rejection does not abort the remainder of the test transaction.
- Cross-browser fixture now re-enters its clinic code after returning from the rejected sign-in flow.

## Security and data integrity notes

- No critical/high finding remains in the executed local test scope. This is not a production security review.
- Duplicate candidate detection is advisory; parallel same-identity registrations can still create separate records. Do not merge automatically; add a deliberate identity-resolution design before stronger blocking.
- Guardian contacts are self-reported, not proof of legal authority. Administrative acknowledgement is not a signed consent or treatment authorization.
- V14→V15 preserves legacy signed fields. The new status defaults to `UNSIGNED`; legacy consent records need semantic review before any production migration.
- Live Playwright uses an ephemeral local signing key and synthetic test identities. It does not claim a real Google provider login.
- No real patient data or production workflows were used or approved.

## CI and dependency assessment

`gh auth status` reported the local GitHub credential invalid; `gh run list` could not reach the GitHub API. The workflow was not triggered, and no hosted result is claimed. Reauthenticate GitHub CLI, then run the workflow on the final commit and add the run ID, SHA, job conclusions, and artifact evidence here.

The OWASP dependency-check workflow remains present and optional. The Maven vulnerability assessment is **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** to M10. Complete and review it before production deployment. Deferral is not a security pass or production approval.

## Recommended next milestone

Close the hosted CI evidence gate for M2.5 and review the remaining medium-risk/data-semantics notes. Do not start M3 automatically.
