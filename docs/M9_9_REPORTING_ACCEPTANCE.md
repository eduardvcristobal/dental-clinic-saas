# M9.9 — Reports acceptance and limitations

**Development release verdict: CONDITIONAL / NOT ACCOUNTING-CERTIFIED.** The reporting module surfaces real, clinic-scoped aggregates, but additional accounting, historical clinical attribution and timezone validation is required before financial decision-making or regulated reporting.

## Delivered and currently under CI
- M9.2–M9.7: `GET /api/reports/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`, restricted to OWNER/ADMIN authenticated clinic principals. Enforced two-year date bound, parameterized tenant predicates, read-only Spring transaction and database FORCE RLS.
- M9.3: completed treatment plan item counts and last-updated item values; explicitly **not** certified historical procedure production.
- M9.4: collections and refunded amounts from actual immutable records, excluding voided payments and using separate disbursement dates.
- M9.5: appointment completed, no-show and all-scheduled counts.
- M9.6: accounts receivable based on issued/nonvoid invoices and nonvoid payments less refunds.
- M9.7: patient registrations (not first clinical attendance).
- M9.8: React reporting workspace, bounded date filters, role-gated navigation, real API fetch, and loading/error/empty behavior.
- M9.9: Java authorization checks and Playwright API boundary checks; CI evidence to be reviewed before completion.

## Important limitations and gates
1. Reporting date windows are UTC. Convert to clinic IANA timezone and validate DST before enabling localized close-of-day reports.
2. Mixed-currency amounts must never be combined or formatted as one currency. Introduce explicit per-currency aggregates and display units before finance acceptance.
3. Historical production cannot use mutable treatment item `updated_at` as a reliable service-completion timestamp. Require signed encounter/procedure event timestamps and reversals attribution.
4. No-show denominators need an agreed definition for cancelled/rescheduled appointments and historical status transitions.
5. Accounts receivable reporting requires point-in-time snapshots, aging buckets and parity checks against M7 financial closure.
6. Improve query performance on large tenancy datasets and verify EXPLAIN plans and paging of drilldown datasets.
7. Verify seeded source-of-truth fixtures with signed clinical, refunds, voids, partial payments, multiple branches, and cross-tenant RLS.
8. Ensure new Playwright test is actually selected by CI configuration; green unrelated browser suites do not prove reports coverage.

## Operational safety
M8 notification feature flags must stay OFF until separately approved. M9 is read-only and never sends patient contact messages.
No real patient information in CI fixtures, metrics logs or screenshots.
