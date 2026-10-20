# M8.10 — Provider Delivery & Operational Hardening (INCOMPLETE)

Branch: `feature/m8-10-provider-delivery-hardening` (base `main`, 2026-10-20).

## What is implemented
- V36 adds tenant-RLS provider delivery events and hashed patient-action token storage, provider/idempotency references, retry metadata, and notification policy timezone column.
- `ProviderNotificationTransport` supports Resend email and Twilio SMS HTTP requests, only when outbound is explicitly enabled. It distinguishes provider acceptance from recipient delivery.
- Destination E.164 and email syntax checks, timezone-aware quiet-hour calculation, bounded exponential retry intervals, and JUnit tests for the pure rules and fail-closed default.

## What is NOT implemented — release blockers
- A feature-gated worker now calls the provider adapter for explicitly configured tenant UUIDs; both worker and outbound flags remain disabled by default. **Do not enable either feature flag until all remaining safety gates have passed.**
- Worker has transactional SKIP LOCKED claiming, lease timeout, bounded backoff and final revalidation, but multi-worker lease fencing, renewal, delivery-attempt audit, and safe Twilio idempotent replay still need verification and hardening.
- No cryptographically verified Resend/Twilio delivery callback endpoint, deduplication workflow, delivery-state transitions or webhook replay guard.
- Feature-gated patient action endpoints issue and redeem expiring single-use tokens; opt-out suppresses pending/failed work and non-opt-out actions record a clinic-scoped staff work item. No staff-approval booking workflow exists, tokens are not embedded in outgoing messages, and end-to-end token replay/expiration/RLS security tests remain incomplete.
- V37 suppresses pending/failed reminders on appointment changes; dispatch revalidates consent, policy and active booking, and postpones messages during clinic-zone quiet hours. **CLAIMED/in-flight invalidation and concurrent appointment edits still need a race-safe send fence.**
- No rate limiting, per-destination and tenant quotas, credential rotation playbook, provider sandbox integration tests, or production verification.
- Real provider callbacks and consent handling require privacy/compliance signoff. Keep payloads PHI-minimal; never include diagnosis, treatment or other sensitive details in external messages.

## Configuration (leave disabled)
```yaml
app:
  notifications:
    outbound-enabled: false
    worker-enabled: false
    patient-actions-enabled: false
    worker-clinic-ids: ${NOTIFICATION_WORKER_CLINIC_IDS:}
    resend-api-key: ${RESEND_API_KEY:}
    from-email: ${NOTIFICATION_FROM_EMAIL:}
    twilio-account-sid: ${TWILIO_ACCOUNT_SID:}
    twilio-auth-token: ${TWILIO_AUTH_TOKEN:}
    from-phone: ${NOTIFICATION_FROM_PHONE:}
```
Do not commit real secrets. Provider keys must be supplied through secret management. Neither HTTP acceptance nor a queued event should be shown as DELIVERED.

## Validation
Tests have been added but **not run locally**: direct repository access is through GitHub connector only in this session. Confirm GitHub Actions and a fresh PostgreSQL migration from V35 to V36 before review. Draft PR must not merge until remaining blockers are implemented and full Java/PostgreSQL/React/Playwright regression is green.

## Reviewer checklist
- [ ] Fresh and upgrading PostgreSQL migration tests, least-privilege RLS
- [ ] Queue worker with advisory lease/skip-locked, tenanted revalidation and retries
- [ ] Signed webhook verification and replay/idempotency tests
- [ ] Tokenized response and opt-out, clinic approval for appointment changes
- [ ] Reschedule/cancel reminder synchronization and time-zone DST acceptance
- [ ] Full CI and provider sandbox tests, compliance and production readiness signoff

## M8.10.20 lease safety gate
Expired `CLAIMED` rows are intentionally **not** automatically retried: a crash after an accepted SMS request cannot be distinguished from a failed request without provider lookup. Operations must reconcile these stranded claims against provider records before explicit recovery. This is a fail-closed interim policy, not finished concurrency safety. Worker and outbound switches must remain OFF pending appointment-change send fencing, provider-side retry safety, and real PostgreSQL concurrency tests.

## M8.10.20 additional unknown-outcome protection
Provider exceptions (including timeouts and malformed acknowledgments) now quarantine the attempted delivery as SUPPRESSED with a generic reconciliation reason and no automatic retry. A callback may still reconcile a provider-accepted event. This is deliberate availability loss to avoid duplicate patient messages; implement provider-specific confirmed-nonacceptance classification and a protected manual replay workflow before enabling outbound traffic. **Sending can still race an appointment/consent edit after preflight; do not enable production delivery.**
