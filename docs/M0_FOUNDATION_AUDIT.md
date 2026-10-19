# M0 — Source Audit and Foundation Risks

**Review date:** 2026-10-19  
**Source:** uploaded `dental-clinic-saas.zip` (static source review; no production deployment examined).

## Confirmed inventory

- Backend `pom.xml`: Spring Boot 3.3.5; Java target changed from 21 to 17 for this delivery.
- Flyway migrations: V1 tenancy/users; V2 patients/files; V3 scheduling; V4 clinical; V5 billing; V6 audit; V7 indexes; V8 row-level security.
- Multi-tenancy utilities: `TenantContext`, `TenantFilter`, `TenantIdentifierResolver`, `TenantAwareDataSource` and Hibernate configuration.
- Frontend: React/TypeScript/Vite, Google sign-in UX, protected route, API client, dashboard, placeholder module links.
- Backend: 25 entity classes, 4 Spring Data repositories, `NumberingService`; no REST controller or test source directory found.

## Prioritized findings

| Priority | Finding | Evidence | Required action |
|---|---|---|---|
| P0 | Authentication API missing | Frontend `src/api/auth.ts` specifies endpoints; backend has no controllers | Implement authentication controller/service, verify Google token server-side, refresh/revoke sessions |
| P0 | Tenant isolation unverified | V8 RLS and `TenantAwareDataSource` exist but no tests | Run real PostgreSQL isolation tests as non-owner app role; verify cross-tenant read/write denial |
| P0 | Connection-scoped state risk | `set_config('app.clinic_id', ?, false)` uses session scope | Explicitly validate pooled checkout/reset, failure paths and reuse; consider transaction-scoped RLS with strict transaction boundaries |
| P0 | Security filter order unverified | `TenantFilter` documented to run after auth; no `SecurityFilterChain` configured | Implement explicit ordering; reject unauthenticated writes and missing tenants |
| P0 | No automated backend tests | No `backend/src/test` directory | Add JDK17 tests for auth, RLS, migrations and domain invariants |
| P1 | Database credentials have local defaults | `application.yml` includes `app` and `postgres` fallbacks | Externalize and require secrets in production profile; least-privilege runtime vs owner roles |
| P1 | Operational modules are placeholders | `DashboardPage.tsx` uses “Coming soon” | Implement real APIs/UI in roadmap order rather than remove badges |
| P1 | Audit trail coverage unverified | Audit table/entity exist, no access logging service | Record protected reads/writes and financial corrections; test immutability requirements |
| P1 | Clinical notes and payment lifecycle not implemented | Entity/schema only | Define sign/amend flow and append-only accounting commands before module work |
| P1 | Java 17 build unverified | Target changed, local tool originally reported JDK 21 | Run Maven on JDK 17 and include CI job |

## Decisions retained

- `clinic_id` isolation with PostgreSQL RLS as a backstop; multi-branch support from the start.
- UTC persistence; branch/clinic timezone for presentation.
- `NUMERIC`/`BigDecimal` for money; use immutable correction entries for payments.
- Object storage for patient documents, least-privilege signed access.
- No hard deletion of medical/financial records; auditable corrections and appropriately controlled retention.
- Pilot dentist/front desk review should validate scheduling, chart conventions, language preferences, and installment handling.

## Completion criteria

M0 is **not complete** until Java 17 compilation/tests, PostgreSQL migration + tenant isolation, authenticated session lifecycle, baseline CI, and reproducible local setup pass with evidence. See `PROJECT_STATUS.md` for the authoritative checklist.
