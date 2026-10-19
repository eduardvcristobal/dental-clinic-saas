# M1 — Clinic and Branch Management: initial implementation

Date: 2026-10-19. Scope: configuration of **existing** authenticated clinics. This is a partial M1 delivery.

## Implemented
- Java 17-compatible controller at `/api/clinic-management` uses authenticated `AuthPrincipal` only, never request `clinic_id`.
- Owner/Admin may update basic clinic settings, create/edit/deactivate branches, create/edit/activate chairs, replace weekly opening hours, and assign/unassign **existing active** users across their own branches.
- Any authenticated clinic staff may read clinic, branch, chair and opening hour information. Staff directory and assignments require Owner/Admin.
- Operating hours use ISO weekday numbering (Monday=1 through Sunday=7), in the clinic's named timezone. Cross-midnight intervals are not supported. Closed days have no row.
- Flyway V12 introduces the operating-hour table with tenant-composite FK, uniqueness, CHECK constraints, forced RLS, and runtime-role grants. V11's global platform-admin policy is deliberately not extended to the new table.
- React administration pages replaced four Coming Soon placeholders with connected configuration UI; existing Patient 360 and preview modules remain unchanged.
- Five JUnit/Mockito authorization/input tests added but **not executed** in this environment.

## Not implemented / production blockers
- **Initial SaaS provisioning** (brand-new clinic, first OWNER, secure invite, verification and subscription) is **not implemented**. Existing records are necessary to log in, so this must be designed and tested as a separate flow.
- Staff **invitations**, new users, dentist profiles, role assignment/revocation, chair conflict integration, holidays and opening-hour exceptions remain future M1 increments.
- Platform Admin V11 overly broad cross-clinic access and prior M0.4 verification blockers remain unresolved. Do not claim production readiness.
- No current database, Maven or JDK 17 runtime available here; PostgreSQL V12 migration and all Java tests remain unverified. Local JDK is 21 only.
- `npm ci --offline` failed because `why-is-node-running@3.2.1` is not in the cache. No frontend build, lint or Vitest pass is claimed.
- Endpoint-level tenant isolation must be tested with real PostgreSQL under restricted app role, including staff branch link attacks and pooled connections. Audit events must be introduced for production modifications.

## Suggested M1 continuation
M1.2 — secure clinic/first-owner onboarding, staff invitation lifecycle, dentist-provider mapping, full role/tenant integration tests and audit logging. Revisit compatibility with unfinished M0.4 security hardening before enabling patient records.
