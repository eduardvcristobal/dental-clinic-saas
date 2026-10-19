# Dental Clinic SaaS (monorepo)

| Folder | What |
|---|---|
| `backend/` | Spring Boot 3.3 / Java 17 / PostgreSQL / Flyway. Multi-tenant (Hibernate `@TenantId` + PostgreSQL RLS). See `backend/README.md`. |
| `frontend/` | React + TypeScript + Vite. Google sign-in. |
| `docs/` | Original schema design (`dental_schema.sql`) and ERD (`dental_erd.mermaid`). Flyway migrations in `backend/` are the source of truth. |
