# M2.5 — PostgreSQL & Tenant Isolation

**PostgreSQL:** 18.6 (Postgres.app). All databases were disposable and synthetic-only; the user’s local `dental` database and ports 5432/55432 were not used.

## Fresh schema PASS

- Flyway applied V1–V15 on a clean database.
- Migration test asserted all 15 successful versions and forced RLS coverage.
- Runtime role `dental_app` was checked as `NOSUPERUSER NOBYPASSRLS`.
- Two synthetic clinics had independent patient/history/guardian/consent fixtures.
- Cross-clinic patient/history/guardian/consent reads returned zero rows under RLS; cross-clinic update/delete affected zero rows; cross-clinic inserts were rejected by RLS/composite foreign keys.
- Setting the platform-admin session GUC did not grant clinical rows.
- Same-clinic branch, patient, actor and invitation relationships were checked.

## Upgrade PASS

A separate empty database migrated to V14, received synthetic patient/history/consent rows, then applied V15. The patient and medical-history values remained; legacy `signed_at`/`signed_by` values remained; a new guardian row could be added. Flyway history ended at V15.

**Data-semantics caveat:** V15 adds `consent_status` with default `UNSIGNED`; legacy `signed_at`/`signed_by` columns are preserved but not treated as validated signature evidence or automatically mapped to `ACKNOWLEDGED`. Review legacy consent interpretation before production migration. No production patient data was available or used.

## Actual runtime role

`MigrationAndTenantIsolationTest` connects through the restricted application role with tenant GUC settings and verifies row filtering, write denial, cross-clinic relationship constraints, tenant-state reset on pooled connection reuse, and platform-admin fail-closed behavior. Expected SQL denials use savepoints and do not weaken RLS.

Sanitized evidence: `docs/evidence/m2_5/database-summary.txt`.
