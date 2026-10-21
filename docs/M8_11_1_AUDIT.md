# M8.11.1 — Notification production readiness audit

Baseline: `main` at `fe859caa332e9cbe4ce49ce5ea2be5c8a3a732ac`.

## Evidence reviewed
- M8.10 final acceptance report: explicit **NO-GO** for live patient communications.
- `NotificationDeliveryWorker`: tenant-scoped queue, transactional claims and pre-send recheck; expired CLAIMED excluded from automatic resend, provider exception quarantines work. However eligibility is committed before a nontransactional provider call, leaving a change-vs-send race and no durable provider request intent.
- `NotificationWebhookReconciler`: signature verification is delegated to the controller, provider reference is looked up by iterating configured tenant UUIDs, and events are inserted with uniqueness. Out-of-order provider-event timestamp checks exist but concurrent behavior and callback loss before reference commit need tests.
- `PatientActionController`: single-use expiring SHA-256 bearer tokens, per-delivery issuance cap, opt-out suppression, but patient action remains a request requiring staff adjudication.
- `PatientRedemptionThrottle`: process-local ConcurrentHashMap, therefore not shared by replicas and potentially unfair for all patients in one clinic.
- Existing M8.10 browser, React and PostgreSQL checks exist but cannot substitute for multithreaded/transaction-level live delivery tests.

## Ordered gates (no automatic merge)
1. M8.11.2 — explicit dispatch fencing and appointment/consent change design, verified with concurrency tests.
2. M8.11.3 — provider outcome audit and safe recovery, provider-specific idempotency guarantees.
3. M8.11.4 — distributed public redemption controls and comprehensive bearer-token lifecycle tests.
4. M8.11.5 — signed provider sandbox integration and replay/out-of-order callback tests.
5. M8.11.6 — PostgreSQL concurrency, tenancy/RLS tests across fresh and upgrade migrations.
6. M8.11.7 — React/Playwright responsive, accessibility, and endpoint safety regressions.
7. M8.11.8 — observability, incident, credentials and compliance acceptance.
8. M8.11.9 — full CI and **explicit GO/NO-GO** release decision.

## Current release state
**NO-GO.** No change in this audit authorizes enabling `app.notifications.worker-enabled`, `outbound-enabled`, or `patient-actions-enabled`. Leave all false; use synthetic data for test/sandbox verification. Gate subsequent milestones on successful CI for the previous commit.

## Risk register
| Risk | Impact | Interim control | Closure proof |
| --- | --- | --- | --- |
| Concurrent appointment/consent mutation during HTTP send | Stale or unwanted patient contact | Both feature flags disabled | Transactional race test + reviewed dispatch fence |
| Unknown Twilio request acceptance | Duplicated messages or missed reminders | Quarantine, no automatic retries | Provider-side reconciliation test and audited recovery |
| Multi-replica redemption abuse | Service exhaustion / token brute-force | Disabled endpoint, local bucket | Shared store rate limiter with IP-aware budgets |
| Late or duplicated provider callbacks | Incorrect delivery status | Signatures, event key uniqueness | Signed webhook contract and real DB concurrency tests |
| PHI and provider privacy | Exposure | Generic messages; flags off | Security/legal review and approved templates |
