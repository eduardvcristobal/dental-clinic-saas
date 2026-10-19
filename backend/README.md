# Backend setup (Spring Boot 3.3.5 / Java 17)

## Requirements

- JDK 17 and Maven 3.9+
- PostgreSQL 16+
- A schema owner for Flyway and a separate application role with `NOSUPERUSER NOBYPASSRLS`
- A disposable empty PostgreSQL database for migration and auth integration tests

## Database setup

Use a disposable local PostgreSQL instance. Create a database and non-bypass runtime role; for example, as a database administrator:

```sql
CREATE ROLE dental_app LOGIN PASSWORD 'local-only-password' NOSUPERUSER NOBYPASSRLS;
CREATE DATABASE dental OWNER postgres;
GRANT CONNECT ON DATABASE dental TO dental_app;
```

Flyway runs as `DB_OWNER_USER`; application queries use `DB_APP_USER`. V8/V9/V10 enforce row-level security and same-clinic relationships. Never use a superuser or `BYPASSRLS` role for application traffic.

Copy `.env.example` values into your shell or a private local environment file (never commit it). Required values include database passwords, a Google web client ID, a unique `APP_JWT_SECRET` of at least 32 UTF-8 bytes, and exact allowed browser origins. Set `APP_COOKIE_SECURE=false` only for local HTTP; production requires HTTPS and secure cookies. Use the frontend Vite proxy for same-site local auth cookies.

## Build, test, run

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17) # macOS example; otherwise select a JDK 17 install
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -B -ntp clean verify
mvn spring-boot:run
```

`MigrationAndTenantIsolationTest` applies every Flyway migration to an empty PostgreSQL database, validates the schema, provisions or verifies a restricted runtime role, and checks cross-tenant reads/writes, missing tenant context, connection reuse, and cross-clinic relationship rejection. Create a disposable empty database first, then run:

```sh
M04_MIGRATION_TEST_DB_URL=jdbc:postgresql://localhost:5432/dental_m04_migration \
M04_MIGRATION_TEST_OWNER_USER=postgres M04_MIGRATION_TEST_OWNER_PASSWORD='owner-password' \
M04_MIGRATION_TEST_APP_PASSWORD='runtime-password' \
mvn -Dtest=MigrationAndTenantIsolationTest test
```

The migration integration test is enabled only when `M04_MIGRATION_TEST_DB_URL` is set; use a newly created, empty database because it expects to apply V1–V10. The CI job creates this database automatically. Inspect `target/surefire-reports` for actual counts.

`AuthIntegrationTest` uses a local JWKS endpoint and real signed test tokens to exercise Google verification, staff admission, JWT validation, refresh replay/concurrency, cookie flags, logout, and Origin checks. Point it at a disposable database (the local app role must be restricted):

```sh
M04_AUTH_TEST_DB_URL=jdbc:postgresql://localhost:5432/dental_m04_validation \
M04_AUTH_TEST_APP_USER=dental_app M04_AUTH_TEST_APP_PASSWORD='local-only-password' \
M04_AUTH_TEST_OWNER_USER=postgres M04_AUTH_TEST_OWNER_PASSWORD='local-owner-password' \
mvn -Dtest=AuthIntegrationTest test
```

Frontend API client checks run with `cd frontend && npm ci && npm test && npm run build && npm run lint`.

## Tenancy and authentication

Hibernate `@TenantId` scopes tenant entities. `TenantFilter` obtains tenant identity only from the verified JWT principal. PostgreSQL RLS provides a second fail-closed layer through `app.clinic_id`, stamped/reset at connection checkout/return. Background tasks must wrap tenant work with `TenantContext.runAs` or `TenantContext.callAs`.

Google sign-in accepts existing active staff only. Access tokens are short-lived and held in frontend memory. Rotating refresh tokens are random, hashed in PostgreSQL, HttpOnly, SameSite=Strict, path-scoped cookies. Reuse of a revoked refresh token revokes active sessions for that user in that clinic. No public registration is provided.
