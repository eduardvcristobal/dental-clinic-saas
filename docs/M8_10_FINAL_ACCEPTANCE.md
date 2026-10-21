# M8.10.25 — Final acceptance decision

**Decision: NO-GO for live patient communications. Draft PR #10 must not be merged as a production-ready delivery release.**

Review scope: M8.10 post-PR #8/#9 commits in `feature/m8-10-provider-delivery-hardening`. Previous PRs #8 and #9 have already been merged; this decision applies to the new draft PR #10.

## Evidence present
- GitHub Actions passed for M8.10.23 commit `6dbd164` (frontend, browser, Java 17, security, clinical-browser). M8.10.24 commit `f29c712` CI must be checked separately after execution.
- Resend/Svix timestamped signature tests; Twilio exact-URL signed form tests; unsigned callback Playwright regression.
- Additive V36–V38 migrations and PostgreSQL schema/trigger/RLS checks.
- React status semantics and tests distinguish ACCEPTED from DELIVERED.
- Operator runbook: `docs/M8_10_PROVIDER_OPERATIONS_RUNBOOK.md`.

## Blocking acceptance cases — NOT YET VERIFIED
1. Simultaneous provider dispatch and appointment edit/consent revocation: the pre-send check and database triggers cannot prevent an already-started HTTP request. Requires explicit send-fencing design and real concurrent PostgreSQL test.
2. Twilio uncertain-acceptance handling: fail-closed quarantine prevents automatic retries, but no authoritative reconciliation or audited operator recovery exists. Durable send-attempt audit and sandbox validation required.
3. Callback replay and ordering across tenants and events: table unique constraint and ordering predicate exist, but tenant-isolated concurrent database tests, provider payload variations, and signature replay edge cases are not fully exercised.
4. Patient token lifecycle: process-local rate limit does not protect multiple replicas; needs distributed/IP-aware control, replay/expiry/consumption and CSRF/privacy review, and approved patient-action UX.
5. Operational sign-off: credential rotation, alerting/SLO ownership, provider webhook production URL, failure drills, and legal/privacy authorization outstanding.
6. Release-quality E2E: no complete mobile/browser accessibility audit for actual delivery state, opt-out, errors and admin recovery screens.

## Required before GO
- Complete M8.10.20/M8.10.21/M8.10.22 remaining concurrency, distributed security, and full runtime integration.
- Run end-to-end Resend and Twilio sandbox tests including delayed, duplicated, out-of-order, invalid and genuine signed events.
- Confirm latest branch full CI checks; obtain written operations, security and compliance signoff.
- Re-review draft PR #10; do not automatically merge.

## Production configuration
Keep `app.notifications.outbound-enabled=false`, `app.notifications.worker-enabled=false`, and `app.notifications.patient-actions-enabled=false`. Do not mark ACCEPTED as DELIVERED. Do not use real patient data in testing.

This report is a release gate and does **not** claim that unresolved security work has passed.
