# Dental Clinic SaaS: Backend (Spring Boot 3.3 / Java 21 / PostgreSQL)

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
