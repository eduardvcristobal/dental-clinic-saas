# Dental Clinic SaaS — Project Status

**Last updated:** 2026-10-19  
**Authoritative baseline:** user-provided `dental-clinic-saas.zip`, updated by this M0 delivery  
**Backend:** Java **17 LTS**, Spring Boot 3.3.5, PostgreSQL, Flyway  
**Frontend:** React, TypeScript, Vite  
**Product:** Multi-tenant dental practice management SaaS; multiple branches per clinic.  
**Overall status:** Foundation in progress; **NOT pilot-ready**.

## Progress conventions

- `[x]` Completed and supported by reviewed source or recorded validation.
- `[ ]` Not yet complete or not verified. Never infer feature completion from a database entity, menu, or scaffold alone.
- Mark milestone **Complete** only after its acceptance criteria and relevant tests pass; include actual commands/results below.
- Update this file with each milestone or patch, preserving dated findings and blockers. Record a Git commit per successfully completed feature/milestone.
- Avoid putting patient data, access tokens, passwords, or clinic secrets in project documentation or test fixtures.

## Milestone roadmap

| ID | Milestone | Status | Acceptance gate |
|---|---|---|---|
| M0 | Foundation audit & setup | **In progress** | Java 17 build; verified migrations/auth/RLS; baseline tests and blockers documented |
| M1 | Clinics, branches, dentists, chairs, staff | Not started | Complete tenant/branch onboarding, permissions and CRUD with tests |
| M2 | Patients, medical history, consent, search | Not started | Registration to searchable protected patient chart |
| M3 | Multi-dentist/chair scheduling | Not started | Appointments, statuses, waitlist, conflict prevention |
| M4 | Odontogram | Not started | Adult/primary tooth surfaces, statuses, historical entries |
| M5 | Visits and clinical documentation | Not started | SOAP notes, signed addenda, files, prescriptions |
| M6 | Treatment plans | Not started | Procedures, phases, estimates, acceptance and completion |
| M7 | Billing and collections | Not started | Invoices, partial/installment payments, ledger, receipts |
| M8 | Reminders and recalls | Not started | Configurable notifications, reply handling and opt-out |
| M9 | Reports | Not started | Production, collections, no-shows, balances, new patients |
| M10 | Production and security hardening | Not started | Tenant isolation, audit, restore drill, end-to-end regression |
| M11 | Single-clinic pilot | Not started | Clinic operates one full week of live appointments and payments |

## M0 task checklist

### M0.1 — Existing source and requirements inventory
- [x] Inspect monorepo structure, backend dependencies, frontend entry points and existing migrations.
- [x] Identify domain scaffolding vs. functional HTTP endpoints.
- [x] Record architectural and security concerns in `docs/M0_FOUNDATION_AUDIT.md`.
- [ ] Validate workflows with a pilot dentist/front desk (in parallel with development).

### M0.2 — Java 17 standardization
- [x] Set Maven `java.version` to **17**.
- [x] Update root/backend documentation to **Java 17**.
- [ ] Run a clean Maven build and tests using an actual **JDK 17** runtime.
- [x] Add GitHub Actions JDK 17 Maven unit-test workflow (not yet executed).

### M0.3 — Backend/application security completion
- [x] Add Spring Security Bearer filter chain and Google ID-token verification source; runtime/integration verification pending.
- [x] Add `/api/auth/google`, `/api/auth/refresh`, `/api/auth/logout`, short-lived JWTs, rotating hashed refresh tokens and scoped HttpOnly cookies; execution pending.
- [ ] Add staff authorization, clinic/branch permissions, audit of clinical/financial access.
- [x] Add pool-return tenant reset to `TenantAwareDataSource` and place `TenantFilter` after JWT authentication only; require runtime pool-reuse tests.
- [ ] Run non-owner/non-`BYPASSRLS` RLS tests with seeded multi-clinic fixtures (basic SQL verification script added).
- [ ] Exercise Flyway V1–V9 against fresh PostgreSQL; reconcile schema/entity validation.
- [ ] Add integration tests for tenant isolation, RBAC, appointments, monetary ledger.

### M0.4 — Delivery and baseline validation
- [ ] Backend build, migrations, integration tests and health endpoints pass.
- [ ] Frontend build/lint pass; login behavior verified against real backend.
- [ ] Document repeatable local setup and environment-secret handling.
- [ ] Add automated CI quality gates and commit a validated milestone.

## Initial observations (2026-10-19)

- 9 Flyway migrations (`V1`–`V9`) present; source includes RLS policies and Hibernate tenant entities. **Runtime enforcement not yet validated.**
- 25 entity classes, 4 repositories, and tenancy utilities present. Entity presence does not mean a working feature.
- Frontend Google login expects `/api/auth/*` contracts but backend controllers and authentication implementation are absent from this uploaded archive.
- `TenantFilter` is a component, but its placement after successful authentication has not been demonstrated by a configured security chain.
- There are no `backend/src/test` files in the provided source.
- Dashboard feature links currently say “Coming soon”.
- `backend/src/main/resources/application.yml` has local fallback database secrets; remove permissive defaults before any production deployment.

## Validation log

| Date | Check | Result |
|---|---|---|
| 2026-10-19 | Maven Java version declaration | **Updated to 17** (source-level check) |
| 2026-10-19 | Backend JDK 17 runtime build | **Not verified**; runner only reported JDK 21 initially |
| 2026-10-19 | PostgreSQL migration + RLS runtime | **Not verified** |
| 2026-10-19 | Real Google authentication | **Not implemented** |
| 2026-10-19 | M0.3 auth/RLS code and tests | **Source implemented; tests NOT executed** (Maven, JDK17, PostgreSQL unavailable) |
| 2026-10-19 | Frontend `npm ci --offline` | **Blocked**: uncached `vite-8.3.4.tgz`; build/lint not run |

## Next recommended task

**M0.3 validation continuation**. Run Maven/JDK 17 and PostgreSQL 16+ Flyway migration tests, verify two-clinic RLS and refresh rotation in actual HTTP calls, and reconcile failed tests. Next: M0.4 runtime/CI and frontend integration before M1.

## Change log

- **2026-10-19 — M0 started:** Inventory and audit; Java 17 target; project status tracker created. No feature completion or runtime test success claimed.

- **2026-10-19 — M0.3 implementation delivered:** Google ID-token verification, server-issued Bearer JWT, rotating RLS-scoped refresh session table (V9), database tenant reset, Java 17 CI workflow, small unit tests, and basic RLS checklist. **No runtime tests passed or were run in this environment; milestone remains in progress.**
