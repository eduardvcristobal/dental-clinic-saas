# M8.10.24 — Provider operations and incident runbook

**Release posture: NOT APPROVED FOR LIVE PATIENT MESSAGING.** Apply this guide only in isolated staging with synthetic patient data. Both notification worker and outbound sending must remain disabled in production until M8.10.25 blockers are closed.

## Configuration and secrets
- Keep `app.notifications.outbound-enabled=false`, `worker-enabled=false`, `patient-actions-enabled=false` for production.
- Provide Resend API key/from-email, Twilio account SID/auth token/from-phone, Resend `whsec_` webhook signing secret, canonical Twilio HTTPS webhook URL, and an explicit authorized clinic UUID allowlist via managed secrets. Do not log credentials, tokens, message bodies or destination contact values.
- Use provider test/sandbox credentials and synthetic clinic/patient addresses only. Validate from-domain and phone sender ownership before any staging enablement.
- Provision a public HTTPS webhook URL; Twilio signatures depend on the **exact configured URL** (proxy scheme/host/path/query must match). Resend uses `svix-id`, `svix-timestamp` and `svix-signature` over the raw payload.
- On rotation, coordinate dual-secret support or scheduled maintenance first: current verifier accepts one configured secret. Keep sends disabled until callbacks validate again.

## Monitoring and operator queries
All notification tables enforce clinic RLS. Run queries in a dedicated authorized clinic-scoped transaction, never bypass FORCE RLS or scan clinical details across tenants.

```sql
SELECT status, channel, count(*) FROM notification_delivery WHERE clinic_id=:clinic_id GROUP BY status,channel;
SELECT count(*) FROM notification_delivery WHERE clinic_id=:clinic_id AND status='CLAIMED' AND lease_until<now();
SELECT provider_name,event_type,count(*) FROM notification_delivery_event WHERE clinic_id=:clinic_id AND received_at>now()-interval '24 hours' GROUP BY provider_name,event_type;
```

Alert on: expired CLAIMED leases, growth in SUPPRESSED with unknown outcome, callback failures/signature rejection (aggregate count only), provider ACCEPTED but no callback within the agreed SLA, consent withdrawal incidents, abnormal high-volume token redemption 429s, tenant-specific queue age, and unexpected outbound traffic while disabled. Do not emit patient identifiers or message content into metrics labels.

## Incident response / recovery
1. Disable worker and outbound flags immediately; suspend the provider credentials if unauthorized traffic is suspected.
2. Identify impacted clinic with a scoped operator session. Preserve signed event IDs, provider references and timestamps without including patient PHI in incident logs.
3. For unknown provider outcomes, compare the provider's authoritative message record with the local idempotency/reference and event audit. **Never manually reset SUPPRESSED or expired CLAIMED to PENDING solely because delivery is unconfirmed**; that could duplicate SMS.
4. Revalidate appointment time/status, current consent, contact address, quiet hours and tenant membership before considering a new distinct send. Obtain explicit operator approval, record who approved it and why.
5. Reconcile duplicates/out-of-order callbacks against provider dashboard and the unique `notification_delivery_event(provider_name,provider_event_id)` constraint.
6. Revoke or rotate leaked keys and any exposed patient bearer links, confirm signature validation, then rerun PostgreSQL/JUnit/Vitest/Playwright regression in staging before re-enablement.

## Known release blockers
- Last-moment appointment/consent mutation can still race an already in-flight external HTTP send.
- Twilio messages lack provider-guaranteed idempotent retry behavior; uncertain sends are quarantined but durable attempt and manual replay workflows remain incomplete.
- Patient-action rate limiting is process-local, not distributed or IP-aware. Patient links are manually issued and not part of outbound content; expiration/replay and privacy compliance need comprehensive integration tests.
- Callback correlation depends on configured clinic UUIDs; operational provisioning and event reconciliation need sandbox exercise and dead-letter procedures.
- Transaction-level concurrent PostgreSQL tests and complete multi-screen browser acceptance are not yet evidenced.
- No measured production telemetry, SLA, provider sandbox signoff or privacy/legal approval.

## Rollback
Keep flags disabled; revert application changes if required while retaining additive database migrations and immutable event evidence. Do not drop event or action tables during incident response. Recovery must be reviewed and approved prior to enabling transport.
