# M10.1 — Production security baseline and release gates

Baseline: merged main after PR #12 (M9), GitHub Actions passed. This is an **audit**, not production certification.

## Observed in code
- Spring Security uses JWT validation and stateless Bearer authentication; tenant identity comes from verified principal. TenantFilter runs inside the security chain.
- PostgreSQL Flyway migrations are owner-run, application queries use a non-superuser account, FORCE RLS is present on tenant clinical/financial tables. Restricted-role integration testing exists.
- Auth login/refresh rate limiting is **per JVM instance**, using remote IP; distributed edge throttle is still mandatory.
- Live messaging worker, outbound and patient actions are disabled by default; M8 security acceptance remains NO-GO.
- Read-only M9 summaries have UTC dates and non-certified financial production definitions.
- The current CI includes Java 17/PostgreSQL 16, React build/lint/Vitest, Chromium synthetic and clinical live-browser tests, secrets detection; OWASP scan is optional/disabled by default.
- There is no verified production infrastructure manifest, backup/restore drill evidence, hosted observability or clinic pilot signoff in the current repo snapshot.

## Planned sequence and objective proof
| ID | Hardening focus | Required proof |
|---|---|---|
| M10.1 | Audit and risk register | Inventory, severity, owner, documented blockers |
| M10.2 | Authentication and sessions | JWT/cookie/CORS tests, login abuse & credential rotation drills |
| M10.3 | Multi-tenant isolation | Forced RLS and same-clinic FK tests across API modules |
| M10.4 | Patient privacy and protection | Retention/export/deletion rules, PHI-minimal logs and authorized access |
| M10.5 | API and abuse protection | Request limits, negative API tests, distributed edge quotas |
| M10.6 | Database correctness/performance | Fresh and upgrading migrations, index/query measurements |
| M10.7 | Audit logs | Tamper-evident security/clinical attribution and restricted visibility |
| M10.8 | Backups and recovery | Actual timed encrypted restore drill and RPO/RTO evidence |
| M10.9 | Deployment security | TLS, secrets, least privilege, runtime/readiness checks |
| M10.10 | CI and dependency security | Reproducible quality gates and vulnerability triage |
| M10.11 | Full E2E | Auth roles, real backend/PostgreSQL, mobile layout and negative cases |
| M10.12 | Performance and concurrency | Load thresholds, locks, query plans, tenant fairness |
| M10.13 | Monitoring and response | Alert drills, log hygiene, runbooks, on-call ownership |
| M10.14 | Acceptance | Release checklist, signed security/privacy/ops approval and GO/NO-GO |

## Immediate blockers (do not claim GO)
1. Shared edge/IP abuse defense absent for all replicas; per-process auth bucket insufficient.
2. No verified live Google/provider sandbox and independent production credential handling acceptance.
3. Backup/restore, monitoring, dependency scan and compliance drills not demonstrated.
4. M8 notification sending still disabled pending its own production release gates.
5. M7/M9 fiscal/reconciliation and reporting certification are separate signoff dependencies.

Commit each coherent verified delivery group and run GitHub Actions between groups. PR is draft against main and must not be merged automatically.
