# Dental Clinic SaaS

Monorepo foundation for a multi-tenant dental clinic management SaaS.

- `backend/`: Spring Boot 3.3.5, Java 17, PostgreSQL, Flyway. See [backend setup](backend/README.md).
- `frontend/`: React, TypeScript and Vite. See [frontend setup](frontend/README.md).
- `docs/`: foundation audit and milestone validation reports.

Current status: **M0.4 local validation passed; the project is not production or pilot ready.** Fresh PostgreSQL migration/RLS, backend auth/session integration, and frontend API/build checks passed locally. GitHub Actions and live Google/browser sign-in still need verification. See [PROJECT_STATUS.md](PROJECT_STATUS.md).
