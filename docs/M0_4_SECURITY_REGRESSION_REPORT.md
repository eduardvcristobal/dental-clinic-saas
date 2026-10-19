# M0.4 Security Regression Report

**Date:** 2026-10-19 (Asia/Manila)  
**Status:** **Core auth and tenant regressions passed locally; CI and production review remain open.**

## Changes and executed evidence

- **JWT validation:** HMAC-SHA256 signature and issuer validation; subject and clinic UUIDs plus supported staff role are required. HTTP tests reject tampering, expiry, and unauthenticated protected routes.
- **Google identity verification:** HTTP tests exercise actual verifier code with a local JWKS endpoint and signed test keys. Signature, expiry, issuer, audience, email verification, active staff, unknown staff, and clinic membership cases passed.
- **Refresh lifecycle:** rotating random credentials remain hashed in PostgreSQL and row-locked. A reused revoked credential now commits revocation of remaining user sessions before returning 401. HTTP tests cover rotation, replay, two concurrent requests, and logout.
- **Cookie/Origin controls:** HttpOnly, SameSite=Strict, path and max-age attributes were checked. Disallowed Origin requests were rejected. Production `Secure` defaults to true; integration tests use HTTP and explicitly disable it.
- **Tenant context and RLS:** request and scoped tenant context cleanup passed unit/integration checks. PostgreSQL RLS passed as `NOSUPERUSER NOBYPASSRLS`; cross-clinic read/update/delete/spoof tests and pool reset passed. V10 same-clinic foreign keys rejected manipulated branch and refresh-session relationships.
- **Rate limiting:** added bounded per-process fixed-window limits for login (10/minute) and refresh (30/minute), with deterministic unit tests. Multi-instance deployments still need shared edge or gateway throttling.
- **Configuration/logging:** blank fallback DB password removed; JWT secret minimum enforced; examples/reports contain placeholders only. No credential or test token values were written to project docs.
- **HTTP error handling:** integration testing found error dispatches converting some controller errors to 401; `DispatcherType.ERROR` is now permitted so intended 403 responses are preserved. The regression tests pass.

Final local Maven verification on JDK 17: **13 passed, 0 failed, 0 skipped**. Frontend API tests: **3 passed**. Full commands and environment are in the runtime and database reports.

## Remaining risks

- Rate limiting is in-memory per process; a horizontally scaled deployment needs shared throttling at a trusted gateway.
- There are no business API routes yet, so role/branch authorization and clinical/financial audit coverage must be implemented and tested before those modules ship.
- Controlled test JWKS passed, but Google live provider/browser sign-in and production cookie behavior were not exercised.
- GitHub Actions and automated secret/dependency scans have not yet run.

Do not claim production or pilot readiness. M0.4 remains in progress until CI succeeds and remaining provider/browser checks are complete.
