# M10.C1–C6 automation delivery and evidence boundaries

**GO/NO-GO: NO-GO for real patient deployments and M11.** Automated scripts are tools, not evidence that a deployment has passed.

| Gate | Added automation | What is still necessary |
|---|---|---|
| C1 Encrypted backups | `m10_encrypted_backup.sh` encrypts a pg_dump archive using age/X25519 recipient; `m10_encrypted_restore_disposable.sh` checks SHA256 and measures restore duration to a pre-created disposable DB | Configure and protect age identities, secure disk for temporary plaintext archive, encrypted offsite storage, schedule, real restore drill, validate schema/tenancy and RPO/RTO |
| C2 Dependencies | `m10_dependency_gate.sh` executes production npm audit; `m10-certification.yml` is an explicitly approved OWASP/NVD scan with hard failures on missing key/advisories | Add NVD_API_KEY secret, launch certification manually and review high/critical findings; regular CI’s skipped OWASP job does not qualify |
| C3 Multi-replica | `m10_two_replica_probe.sh` tests unauthenticated denial through two approved localhost instances | Start two backend replicas with one restricted PostgreSQL app role and real synthetic tenant accounts, exercise concurrency/shared edge rate limits |
| C4 Performance | `performance/m10_synthetic_api.js` defines bounded k6 latency/error budgets and refuses non-loopback targets | Run with k6 against populated synthetic infrastructure; test authorized scheduling/finance load in addition to negative unauthenticated probe |
| C5 Monitoring | `monitoring/m10_alert_rules.yml` includes 5xx and scrape-unavailability alerts | Deploy verified backend metrics exporter; test alert delivery/on-call acknowledgment and operational dashboard |
| C6 Certification | `scripts/m10_certification_report.py` emits explicit BLOCKED statuses and unconditional NO-GO pending verified evidence | Independently assess evidence refs, privacy/security/clinical/financial/ops approvals and make an authorized production decision |

## CI results tracking
- M10 PR #13 and initial PR #14 commits passed development CI.
- The new M10.C1–C6 head must pass Java/PostgreSQL, frontend, browser and security gates.
- The explicit dependency-certification workflow is a *separate run* requiring secrets and manual approval. It must not be marked PASS if not executed.
- Existing outbound patient notifications remain disabled; do not enable them through these scripts.

## Operator guidance
Use only synthetic patients and disposable databases for certification. Keep credential URIs and encryption identities out of shell history, logs and GitHub Actions artifacts. Backup scripts briefly stage plaintext dumps in private temporary storage; approve encrypted scratch disks or replace staging with encrypted streaming before use on sensitive production data.

**Merge policy:** Keep PR #14 draft; no automatic merge. Passing developer CI is not production certification.
