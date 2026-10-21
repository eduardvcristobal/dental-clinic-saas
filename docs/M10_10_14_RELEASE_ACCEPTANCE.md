# M10.10–M10.14 — Production and pilot-readiness acceptance

**Release decision: NO-GO for real patients / production pilot.** The development hardening branch is not a certification artifact and must remain a draft PR until owner approval.

## Delivery matrix
| Scope | Implemented evidence | Remaining release gate |
| --- | --- | --- |
| M10.10 CI and dependency security | Existing Java 17/PostgreSQL, frontend typecheck/lint/Vitest, synthetic/real-backend Playwright, secret scanning; `scripts/m10_quality_gate.sh` and CI shell syntax checks | Mandatory OWASP/NVD scan currently optional; obtain a completed report, triage advisories and verify protected branches |
| M10.11 E2E regression | Existing browser and authenticated synthetic PostgreSQL browser suites are CI-required, including the M9 reports contract | Perform cross-role E2E matrix, responsive/mobile and accessibility acceptance, and genuine external OAuth/provider checks; no real patient data |
| M10.12 performance and concurrency | `scripts/m10_synthetic_query_probe.sh` safely restricts EXPLAIN ANALYZE to disposable synthetic DB names and 5s timeout | Execute on sized synthetic data, benchmark throughput and p95/p99, prove booking/consent races, refund concurrency, tenant fairness and connection-pool performance |
| M10.13 monitoring and incident response | Operational checklist below; existing provider runbook | Instrument and exercise alerts, verify data minimization, owners, escalation and incident drills |
| M10.14 final acceptance | Explicit NO-GO, unresolved-gates checklist | Obtain independent security/privacy/financial/clinical/operations signoffs and verify recovery objectives on deployed infrastructure |

## Operational alerts and SLO checklist
- [ ] App health/readiness: authenticated or private infrastructure probes, 5xx rate, response duration p95/p99, error budget alert owner.
- [ ] PostgreSQL: connections, lock waits, deadlocks, migration success, slow queries, backup age, replication health, disk free.
- [ ] Auth/security: rate-limit 429 trends, rejected JWTs, invalid tenant claims, suspicious role changes, WAF challenge spikes and audit event gaps. **Never include tokens, credentials or PHI in labels.**
- [ ] Financial: failed ledger reconciliation, ambiguous refunds, incorrect receipts, negative balances and reconciliation alerts with finance escalation.
- [ ] Notifications: queue age, provider uncertainty and signed webhook failures. Keep external sending OFF; do not alert on simulated send traffic as if it were live.
- [ ] Backup/restore: successful encrypted offsite backups, checksum validation, independent timed restore drill and documented RPO/RTO.
- [ ] Alert drill: deliberately trigger a synthetic condition, record alert delivery and acknowledgement time, and prove escalation, suppression, rotation, and recovery.

## Incident response
1. Declare severity, identify incident commander and incident log (no patient identities in chat).
2. Disable affected feature flags, isolate credentials and preserve tamper-resistant security/DB evidence.
3. Revoke exposed tokens/rotate secrets using approved runbook; block unsafe outbound retries.
4. Restore from verified backups only after integrity and tenancy checks, using approved release rollback.
5. Notify compliance and affected parties as legally required; record timelines and postmortem corrective actions.

## Mandatory GO gates before M11
- [ ] M7 money and legal receipt signoff and M9 accounting/timezone reporting certification.
- [ ] M8 patient messaging security, provider sandbox, consent and operational signoff; features stay disabled until then.
- [ ] High/critical dependency findings resolved or explicitly accepted by authorized security owner with due dates.
- [ ] Auth and shared edge/IP limits validated across multiple replicas and trusted proxies.
- [ ] Tenant RLS and every business API role isolation verified on a production-equivalent staging environment.
- [ ] Full Java/PostgreSQL/React/Playwright CI green on final head; real browser + mobile + accessibility audits.
- [ ] Encrypted offsite backup and timed restore with auditable RPO/RTO evidence.
- [ ] Synthetic load/performance thresholds approved, measured and achieved.
- [ ] Production TLS/secrets/access controls, vulnerability scan, monitoring/alerting and incident response drills demonstrated.
- [ ] Written business owner, technical, privacy, legal, operations and clinical authorization.

**Do not merge automatically. A code review merge, should the owner choose it, is not a production GO.**
