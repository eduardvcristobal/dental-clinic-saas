# M2.5 — Backend Regression

**Environment:** OpenJDK 17.0.20, Maven, Spring Boot 3.3.5, PostgreSQL 18.6, isolated synthetic databases.
**Command:** from `backend/`, `mvn -B -ntp clean verify`; environment variables supplied isolated auth, migration, and upgrade database URLs and a non-superuser runtime role. Credentials are not recorded here.

## Results

**PASS:** 53 tests, 0 failures, 0 errors, 0 skipped. Maven clean verification and packaged JAR succeeded.

**2026-10-20 rerun:** `mvn -B -ntp verify` also passed 53/53 tests on OpenJDK 17.0.20 with three newly created, isolated PostgreSQL databases for authenticated integration, fresh V1–V15/RLS migration, and V14→V15 upgrade coverage. PostgreSQL 18.6 emitted Flyway's existing notice that its tested maximum is PostgreSQL 16; migrations and assertions passed. The fresh rerun databases contain synthetic test data only.

- Auth security unit tests: 5 passed, including redaction of identity/access/refresh tokens from DTO `toString()`.
- Authenticated HTTP integration: 14 passed, including a check that Spring MVC request/response body and request-parameter loggers are not debug-enabled. It also covers Google test-key signature/issuer/audience/expiry checks, active membership, refresh/replay, clinic registration, invitation flows, concurrent invitation claims, and patient-management HTTP coverage.
- Auth rate limit: 2 passed.
- Sensitive patient logging: 1 passed, verifying patient, guardian, and clinical-history DTO values are redacted from string representations.
- Fresh PostgreSQL migration/RLS: 1 passed with Flyway V1–V15 and restricted runtime role.
- V14→V15 upgrade preservation: 1 passed.
- Clinic onboarding controller/security: 10 passed, including token-redaction behavior.
- Clinic management: 5 passed.
- Patient-care controller/security: 8 passed.
- Patient controller: 6 passed, including missing/stale expected version.

## Patient API coverage

Authenticated HTTP verified clinic-local chart numbering, registration, name/date duplicate candidates, search, update, stale conflict, simultaneous writes (one succeeds, one receives 409), foreign-clinic 404, clinical-history dentist access, Front Desk 403, guardian create/remove, consent create and one-time administrative acknowledgement, unsigned signature fields, owner-only archive, archived-record denial, and audit actor/clinic scoping.

Full test output is retained only in private temporary storage because older development debug logs could serialize test token DTOs. The sanitized result summary is `docs/evidence/m2_5/backend-summary.txt`.
