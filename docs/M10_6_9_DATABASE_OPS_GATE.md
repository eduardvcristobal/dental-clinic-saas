# M10.6–M10.9 — Database, audit, restore and deployment operations

**Status: implementation delivered for review; production recovery/security certification NOT YET GRANTED.**

## M10.6 — Database and performance
- V41 adds clinic-leading selective indexes for reporting invoices, payments, refunds, scheduling, patient registration, completed treatment items, locked visits and audit chronology.
- PostgreSQL 16 integration migration verifies index presence. Index creation can lock/write-amplify on existing populated installations: test upgrade runtime and EXPLAIN ANALYZE on production-like *synthetic* volumes before applying to a live DB.
- No claim that query throughput/latency targets have been benchmarked. Collect representative query plans and peak-load evidence before GO.

## M10.7 — Immutable audit ledger
- V41 prevents UPDATE/DELETE of `audit_log` with a database trigger and revokes those privileges from `dental_app`. Existing FORCE RLS and clinic-scoped access continue.
- This does not automatically audit every clinical read or system mutation. Perform a gap assessment for access logs, financial events, consent updates, privilege changes, restoration, and platform administration before certification.
- JSON audit details must be PHI-minimal and never contain access credentials or raw token values. Audit retention and administrator access remain compliance decisions.

## M10.8 — Backup and restore
- `scripts/m10_backup.sh`: requires operator approval, DB URL and an existing secure output directory; creates a mode-600 PostgreSQL custom dump and SHA256 file.
- `scripts/m10_restore_disposable.sh`: requires explicit approval and a **pre-created** database named `dental_m10_restore_*`, verifies checksum and restores without owner/privilege recreation. Refuses arbitrary restore targets.
- Neither script provides encryption at rest, replication or offsite durability. Approved KMS-based encryption and storage IAM are deployment prerequisites. No production backup or timed restore has been executed during this milestone.
- **Required human drill:** use a disposable DB, verify SHA256, restore an approved encrypted backup, validate Flyway checksum history, row counts, RLS, representative patient/finance data relationships, application startup, and measure achieved RPO/RTO. Record reviewer, UTC date and evidence in a private secure audit system—no patient data in the repository.
- Restoration script requires `pg_dump`, `pg_restore`, and `sha256sum` installed. Connection URLs must be supplied via secret management, never committed.

## M10.9 — Deployment protection
- Terminate TLS on trusted infrastructure, enforce HTTPS redirects, HSTS after domain validation, modern TLS configuration and protected cookies. Never trust arbitrary client-forwarded headers; explicitly configure trusted proxy boundaries.
- Separate PostgreSQL migration-owner credentials from `dental_app`, using a restricted runtime role without SUPERUSER/BYPASSRLS. Do not expose port 5432 publicly.
- Supply `APP_JWT_SECRET`, DB secrets and external provider credentials from an audited secret manager. Restrict CI and repository access to staging/synthetic data.
- Set `APP_COOKIE_SECURE=true`, exact `APP_ALLOWED_ORIGINS`, production Google OAuth origins, request-size limits and edge rate limiting. Keep `APP_ONBOARDING_SELF_SERVICE=false` without approved onboarding controls.
- Keep `app.notifications.worker-enabled=false`, `outbound-enabled=false` and `patient-actions-enabled=false` until separately accepted. Do not interpret a passed deploy as permission to send.
- Deploy using an unprivileged application service account, immutable built artifacts, log redaction and audited release/rollback procedures.
- Before M10.14, attach evidence of authenticated health checks, database connectivity and schema readiness, zero-downtime or rehearsed maintenance rollback, protected backups, restore timing, edge WAF, security monitoring, and responsible operators.

## Final gate
M10.6–M10.9 are not complete for production until PostgreSQL CI passes, a timed restore drill succeeds with encrypted storage, and deployment controls are independently verified. Keep Draft PR #13 unmerged pending owner's review.
