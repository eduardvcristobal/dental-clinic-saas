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

## Final M9 delivery checklist (development scope)
- [x] M9.2 read-only clinic report API, ISO date validation, two-year range bounds, role gating and parameterized tenant predicates
- [x] M9.3 treatment plan completed-item operational summary (historical signed production attribution remains a known limitation)
- [x] M9.4 issued invoice sums, actual nonvoid payment receipts, recorded refunds and method breakdown; multi-currency totals fail closed
- [x] M9.5 completed/no-show appointment counts and a rate excluding cancelled appointments from the denominator
- [x] M9.6 invoice receivables net of payments and refund disbursements with transaction-date upper bounds; detailed historical A/R aging is not yet certified
- [x] M9.7 registrations and separate first locked-visit cohort
- [x] M9.8 real React clinic reporting route, role navigation, date filters, safe empty/error states and currency disclosure
- [x] M9.9 Java role/date security tests and authenticated synthetic Playwright API contract registered in live PostgreSQL browser CI
- [ ] Provider/financial/legal signoff and locale-specific accounting/historical analytics certification (separate mandatory production acceptance)

## Release interpretation
M9 is suitable for **development demonstration and synthetic-data regression**, not a certified financial or clinical production reporting release. No dashboard values should be represented as legally compliant receipts or official statements. No production authorization is implied by a green GitHub Actions run. Reviewers must verify Java, PostgreSQL, React, security and the M9-authenticated Playwright test actually passed on the final PR head before merge.
