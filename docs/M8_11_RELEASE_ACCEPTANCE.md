# M8.11 release acceptance ledger

**Release verdict: NO-GO for real patient outbound communications as of 2026-10-21.**

This branch was created from `main` at `fe859caa` and targets `main` via Draft PR #11. Do not merge automatically.

## Engineering progress and acceptance scope
| Sub-milestone | Evidence | Status |
|---|---|---|
| M8.11.1 Audit | docs/M8_11_1_AUDIT.md | Completed, CI passed |
| M8.11.2 Dispatch fencing | NotificationDeliveryWorker now holds the delivery lock through the bounded provider request, serializing consent and booking invalidation updates | Implemented, CI passed; real race/concurrency qualification outstanding |
| M8.11.3 Provider reconciliation | V40 and worker now record a tenant-RLS durable attempt ledger (CLAIMED/ACCEPTED/UNKNOWN); Twilio authoritative recovery and replay drill remain missing | Open |
| M8.11.4 Distributed patient actions | V39 PostgreSQL tenant-scoped atomic redemption budget, controller integration, runtime migration regression | Implemented, CI pending at report authoring; edge/IP protections open |
| M8.11.5 Provider sandbox | No actual Resend/Twilio sandbox delivery evidence or credential configuration supplied | Open |
| M8.11.6 Database concurrency | Migrated schema tests and atomic quota upsert test exist; full simultaneous send/reschedule/consent/callback tests remain | Open |
| M8.11.7 React/Playwright | M8.10 suite previously passed; production notification UX and recovery e2e incomplete | Open |
| M8.11.8 Monitoring/compliance | M8.10 runbook exists; instrumented production monitoring, privacy review and incident drill approval outstanding | Open |
| M8.11.9 Full acceptance | Must require ALL preceding high-priority safety evidence | **NO-GO** |

## Safety controls — mandatory until GO
- Keep `app.notifications.outbound-enabled=false`, `app.notifications.worker-enabled=false`, `app.notifications.patient-actions-enabled=false`.
- Do not configure credentials with live traffic before approved sandbox and privacy tests.
- A signed provider webhook's ACCEPTED status is not recipient DELIVERED status.
- Never automatically replay UNKNOWN/ambiguous Twilio attempts; reconcile via provider authoritative reference first.
- Never merge this draft PR without explicit user approval.

## Detailed remaining gates
1. Demonstrate transaction-level PostgreSQL concurrency of booking and consent invalidation with active send, and measure bounded transaction lock time. Deadlock/connection-pool starvation needs load testing because an HTTP request currently holds a database lock.
2. Review durable provider-attempt audit (V40), then implement safe manual recovery authorization and provider status retrieval before any unknown send replay.
3. Verify callback duplicate/replay/out-of-order behavior with valid provider sandbox signatures and payloads, including callbacks arriving before provider reference is persisted.
4. Add deployment-wide network/edge per-IP protection, shared token throttling lifecycle and expiry cleanup, and tenant-safe failed-auth metrics.
5. Complete responsive React/Playwright acceptance for provider and opt-out error states, and operational monitoring, SLO and compliance approvals.
6. Rerun GitHub/CircleCI full gates on final head and obtain explicit documented GO signoff.

**A green CI check is necessary but not sufficient for live notification release.**

## Operational provider attempt investigation (M8.11.3)
Within an authenticated tenant-scoped database session, review `notification_delivery_attempt` for `CLAIMED` older than the bounded provider timeout and for `UNKNOWN`. Do not reset any entry to retry solely based on elapsed time. Reconcile against provider authoritative records and audit the operator decision. The table deliberately excludes message content and destination addresses.

```sql
SELECT status, count(*) FROM notification_delivery_attempt WHERE clinic_id=:clinic_id GROUP BY status;
SELECT delivery_id, idempotency_key, status, created_at, concluded_at FROM notification_delivery_attempt WHERE clinic_id=:clinic_id AND status IN ('CLAIMED','UNKNOWN') ORDER BY created_at;
```
