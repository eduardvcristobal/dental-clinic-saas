# M8.4 External SMS/Email transport gates

NotificationTransport is fail-closed. No external messages are sent by this implementation; provider integration requires separate approval.

Before enabling:
1. Verify opted-in patient channel and destination for every delivery and every retry.
2. Validate international E.164 phone numbers at the service boundary, enforce country-specific sender policy and anti-spam requirements.
3. Use signed provider callbacks, replay/idempotency protection, allowlisted webhook origins, and tenant-scoped provider message IDs.
4. Use encrypted secret storage, least-privilege provider accounts, budget limits and rate limiting. Never log raw message content, email addresses, phone numbers, tokens or protected clinical details.
5. Support terminal delivery statuses and exponential retry backoff with leased workers; suppress pending sends on consent withdrawal or appointment cancellation.
6. Keep messages generic: do not include diagnosis, treatment plan or protected clinical information.
7. Verify timezone-aware quiet hours and appointment edits before sending.
8. Simulate delivery, bounce, opt-out, duplicate callbacks, retry exhaustion, provider outages and cross-tenant attacks with synthetic recipients.
9. Do not treat the staff-recorded patient response as authenticated patient confirmation. Public response tokens require signed, expiring, one-time purpose-scoped capabilities and abuse protection.

**Status:** Provider sending, SMS delivery callbacks and true patient replies are NOT IMPLEMENTED. M8 schema and APIs are dry-run foundations only.
