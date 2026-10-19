# M0.4 Database Validation Report

**Date:** 2026-10-19 (Asia/Manila)  
**Status:** **Local PostgreSQL migration and restricted-role RLS checks passed. Hosted PostgreSQL 16 CI remains unrun.**

## Environment and fresh migration

- PostgreSQL 18.6 (Postgres.app), OpenJDK 17.0.20, Spring Boot 3.3.5, Flyway 10.10.0.
- Created disposable databases `dental_m04_validation` and `dental_m04_migration_validation`; created the restricted `dental_app` role because it did not previously exist. Test data is synthetic.
- Ran the packaged backend against the fresh `dental_m04_validation` database with `postgres` as Flyway owner and `dental_app` as runtime role. Spring started and Hibernate `ddl-auto=validate` passed.
- Flyway recorded successful V1–V10 migrations in order. The saved checksums were:

| Version | Checksum | Version | Checksum |
|---|---:|---|---:|
| V1 | 2013763616 | V6 | -1053862219 |
| V2 | -114509875 | V7 | -2001388356 |
| V3 | 1912024387 | V8 | 957945094 |
| V4 | -743660908 | V9 | 1318699455 |
| V5 | 637707931 | V10 | 1607300138 |

- Restarted the application/integration context against the migrated database; Flyway validated all 10 migrations and Hibernate schema validation passed again.
- Catalog inspection found 27 tenant tables with both RLS enabled and forced (the unscoped clinic root is excluded), 48 same-clinic composite foreign keys, and a runtime role with `rolsuper=false`, `rolbypassrls=false`.

## Executed isolation checks

- Ran `MigrationAndTenantIsolationTest` against a separate empty scratch database. The test applied V1–V10 from scratch, validated schema with Hibernate, and passed as `dental_app`.
- As `dental_app`, Clinic A saw its own synthetic patient and could not read, update, or delete Clinic B's patient. A Clinic B `clinic_id` spoofed insert was rejected. An unset tenant returned no patient rows.
- Verified the application `TenantAwareDataSource` stamps a Clinic A connection and clears the tenant value before connection reuse.
- Composite constraints rejected Clinic A records linked to Clinic B's branch and user/refresh session.
- `mvn -o -B -ntp clean verify` result: database migration/RLS test passed; no skipped backend tests in the final run.

## Changes

- Preserved V1–V9 and added forward-only V10 composite `(clinic_id, id)` keys for same-clinic relationships across branches, staff, patients, appointments, clinical records, invoices, payments, and refresh sessions.
- Reworked the automated migration/RLS test to target an explicitly provided clean PostgreSQL database, so it runs without Docker. GitHub Actions creates a separate empty PostgreSQL 16 database for the same test.

## Remaining verification

The local database was PostgreSQL 18.6; the CI workflow targets PostgreSQL 16 but has not run yet. CI results, supported PostgreSQL 16 compatibility, restore/restart beyond the tested app restart, and broad CRUD isolation for future API modules remain to be verified. Do not use the local scratch databases for clinic data.
