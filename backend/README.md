# Dental Clinic SaaS: Backend (Spring Boot 3.3 / Java 17 / PostgreSQL)

## Multi-tenancy design (shared database, shared schema)
Two independent layers, so one bug cannot leak another clinic's data:
1. **Hibernate `@TenantId`** on every tenant entity (`TenantEntity`). Hibernate adds `clinic_id = ?` to every
   query/find/update/delete and fills it on insert. Tenant comes from `TenantContext` (set by `TenantFilter`
   from the authenticated principal).
2. **PostgreSQL row-level security** (migration V8). `TenantAwareDataSource` sets `app.clinic_id` on every
   connection checkout. No tenant set = no rows visible, no rows writable.

## One-time database setup
```sql
CREATE ROLE dental_app LOGIN PASSWORD '...' NOSUPERUSER NOBYPASSRLS;  -- runtime role (RLS applies)
CREATE DATABASE dental;                                               -- migrations run as the owner
```
Flyway runs as the owner (`DB_OWNER_USER`), the app runs as `dental_app`. **Never run the app as a superuser or a
BYPASSRLS role**: PostgreSQL skips RLS for them.

## Rules for working in this codebase
- Every new tenant table: `clinic_id UUID NOT NULL REFERENCES clinic(id)`, entity extends `TenantEntity`, and it is
  picked up by the V8 policy loop only if added BEFORE V8 runs. For later tables add the same policy in their own
  migration (copy the `CREATE POLICY` pattern).
- Background jobs (reminders, recalls) have no request: loop over clinics and wrap work in `TenantContext.runAs(id, ...)`.
- Never take the tenant from a header or request body; only from the authenticated principal.
- Money: `BigDecimal` / `NUMERIC(12,2)`. Payments are append-only: void, never edit or delete.
- Double-booking is rejected by DB exclusion constraints; translate the `DataIntegrityViolationException`
  (constraint name contains `_excl`) into HTTP 409 in your exception handler.
- Allocate human numbers with `NumberingService` (`nextChartNo()`, `nextInvoiceNo()`).

## Run
`mvn spring-boot:run` (needs PostgreSQL and the setup above). Hibernate is set to `ddl-auto: validate`; Flyway owns the schema.

## M0.3 authentication configuration (Java 17)

Required environment variables: `DB_URL`, `DB_APP_PASSWORD`, `DB_OWNER_PASSWORD`,
`GOOGLE_CLIENT_ID`, `APP_JWT_SECRET` (random, at least 32 UTF-8 bytes), and
`APP_ALLOWED_ORIGINS` (comma-separated exact browser origins). `DB_APP_USER`
(default `dental_app`) must NOT be a schema owner, superuser or have `BYPASSRLS`.
`DB_OWNER_USER` controls Flyway only. In local HTTP development, set
`APP_COOKIE_SECURE=false`; production MUST use HTTPS and the default `true`.

`POST /api/auth/google` accepts `{clinicSlug,idToken}` and admits *existing active*
clinic staff only. `POST /api/auth/refresh` rotates an HttpOnly, SameSite=Strict,
path-scoped refresh cookie; `POST /api/auth/logout` revokes it. Access JWTs expire
in ten minutes and carry a server-issued clinic id and role. Google identity is
verified against Google's public signing keys and configured OAuth client ID.
There is no public self-registration; staff invitations are part of M1.

Run Flyway migrations using the database owner and `auth_refresh_session` V9.
For local authentication, ensure the frontend and API are **same-site** (CORS
alone cannot override SameSite cookie rules). NEVER deploy with sample credentials.
