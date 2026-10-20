# M8.10 — Provider Delivery & Operational Hardening (INCOMPLETE)

Branch: `feature/m8-10-provider-delivery-hardening` (base `main`, 2026-10-20).

## What is implemented
- V36 adds tenant-RLS provider delivery events and hashed patient-action token storage, provider/idempotency references, retry metadata, and notification policy timezone column.
- `ProviderNotificationTransport` supports Resend email and Twilio SMS HTTP requests, only when outbound is explicitly enabled. It distinguishes provider acceptance from recipient delivery.
- Destination E.164 and email syntax checks, timezone-aware quiet-hour calculation, bounded exponential retry intervals, and JUnit tests for the pure rules and fail-closed default.

## What is NOT implemented — release blockers
- **No worker calls the provider adapter.** Existing notification queue remains manual and never externally dispatches. Do not turn on the feature flag.
- No tenant-safe worker claiming, renewal, crash recovery, backoff persistence, delivery-attempt audit, or idempotent replay semantics.
- No cryptographically verified Resend/Twilio delivery callback endpoint, deduplication workflow, delivery-state transitions or webhook replay guard.
- No generated patient links, constant-time hashed token verification, single-use consumption, secure opt-out, or staff-approval booking workflow.
- No appointment mutation synchronization, final-state and consent checks at dispatch time, clinic-zone quiet-hour enforcement in a real worker.
- No rate limiting, per-destination and tenant quotas, credential rotation playbook, provider sandbox integration tests, or production verification.
- Real provider callbacks and consent handling require privacy/compliance signoff. Keep payloads PHI-minimal; never include diagnosis, treatment or other sensitive details in external messages.

## Configuration (leave disabled)
```yaml
app:
  notifications:
    outbound-enabled: false
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
