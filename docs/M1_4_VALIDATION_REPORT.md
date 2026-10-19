# M1.4 — Runtime regression and security certification evidence

Date: 2026-10-19

## Scope and delivered hardening

- Invitation bearer tokens are stripped from the acceptance URL immediately on mount, including error scenarios; the app uses a `no-referrer` policy to reduce accidental disclosure.
- PostgreSQL regression extended to check same-clinic invitation foreign keys, RLS read/write isolation across two clinics, atomic claim conditional updates, replay denial, and accepted-invitation revocation denial. These assertions require an actual disposable PostgreSQL database.
- `scripts/m1_4_verify.sh` supplies a fail-closed local build/test entrypoint and reports missing dependencies instead of falsely passing.

## Verification carried out in this workspace

| Gate | Result | Evidence |
|---|---|---|
| M1.3 full source ZIP extraction | PASS | Extracted successfully into working tree |
| Git whitespace/error check | PASS | `git diff --check` |
| Frontend dependency install (`npm ci --offline`) | BLOCKED | Missing cache artifact `why-is-node-running@3.2.1` |
| Java 17 build and JUnit | BLOCKED | Only Java 21 present; `mvn` unavailable |
| PostgreSQL fresh V1–V14 and RLS tests | BLOCKED | No postgres/psql server available |
| V13 → V14 upgrade migration | NOT RUN | Disposable seeded upgrade database needed |
| Invitation concurrency on separate HTTP sessions | NOT RUN | Running backend and PG required |
| Real Google signup and invitation E2E | NOT RUN | OAuth client, browser and running backend required |
| GitHub-hosted CI | NOT RUN | Requires user repository push/workflow execution |
| Clinical data enablement | DENIED | Production gate remains open |

## Required follow-up to certify

1. Use JDK 17, Maven and disposable PostgreSQL 16+. Never point migration tests at production databases.
2. Run `scripts/m1_4_verify.sh` with `M04_MIGRATION_TEST_DB_URL`, owner and restricted-app role credentials described in `backend/README.md`.
3. Provision an existing V13 schema separately and apply V14 as an upgrade, checking records and permission changes.
4. Start backend/frontend with test-only Google OAuth configuration; exercise registration, invitation acceptance, rejection, replay and revocation using isolated accounts.
5. Run concurrent acceptance requests from separate connections and verify exactly one success and one staff account.
6. Push to GitHub and verify CI quality gates on the exact commit.
7. Audit Platform Admin endpoints and authorization under an authenticated platform principal, including negative cross-clinic API tests.

**Decision: NOT CERTIFIED.** No passing runtime integration or E2E claims are made for M1.4.
