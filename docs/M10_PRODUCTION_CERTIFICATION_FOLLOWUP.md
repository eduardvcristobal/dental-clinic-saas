# M10 Production Certification Follow-up — Release Evidence Register

**Decision: NO-GO for a live single-clinic pilot.** This follow-up must not silently reinterpret merged M10 code as production approval.

## Certification checkpoints
| Gate | Verification required | Current evidence | Status |
|---|---|---|---|
| CI correctness | Main and feature Java 17/PostgreSQL, frontend, browser, security checks on exact SHA | M10 PR #13 merged; merge-commit main CI must be checked independently | Pending |
| Dependency security | Completed OWASP/NVD dependency scan plus npm and transitive dependency audit; security-owner documented exceptions | OWASP optional/skipped in normal workflow | Open |
| Tenant and auth | Production-equivalent restricted runtime role, FORCE RLS, cross-tenant/role end-to-end denial, trusted proxy/IP edge limits | Existing synthetic CI and tenant migration gates | Partial |
| Backup and recovery | Encrypted offsite snapshot, SHA256 verification, isolated timed restore, verified app startup and RPO/RTO, no PHI in evidence | Guarded scripts in repository; no drill executed | Open |
| Performance | Sized synthetic load, p95/p99 latency, concurrency and lock contention thresholds signed off | Synthetic-only EXPLAIN probe supplied; no measured load baseline | Open |
| Live providers | Approved OAuth and Resend/Twilio sandbox credential rotation, callback and revocation tests | No independently verified production credentials or drill | Open |
| Privacy and operations | PHI data-flow review, retention/deletion, logging redaction, incident/alert drill, ownership | Runbooks and inventory, not execution evidence | Open |
| Finance/clinical reporting | Receipt/ledger reconciliations, reporting timezone, clinical service attribution | M7/M9 development shipped, certification limitations remain | Open |
| Pilot | Written approvals by clinical, privacy, operations, finance and security owners | Not granted | NO-GO |

## Next execution sequence
1. Confirm merged `main` GitHub Actions success and certify a full security scan on a synthetic-only branch.
2. Run approved offsite encrypted-backup and isolated timed restore using dedicated infrastructure. The `scripts/m10_backup.sh` script now validates a completed archive before publishing it; it does **not** encrypt by itself.
3. Execute multi-instance tenant/role abuse and API edge-limit tests, with trusted proxy configuration and actual metrics.
4. Execute staging synthetic load/performance and full responsive/accessibility browser suite.
5. Complete operations, privacy, clinical and financial reviews; document RPO/RTO/SLO/incident outcomes.
6. Record explicit GO/NO-GO; do not enable live notifications absent M8 approval.

Never commit backup files, credentials, database URIs, real patient datasets, or private drill screenshots. Do not automatically merge this follow-up PR.
