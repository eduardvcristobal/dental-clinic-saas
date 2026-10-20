# M7.1–M7.9 Billing & Collections — Development Acceptance

Date: 2026-10-20
Branch: `feature/m7-billing-collections`; base `main`
Disposition: DEVELOPMENT IMPLEMENTATION, with explicit feature gaps and production/security gates.

## Milestone coverage

| ID | Delivered | Limitations |
|---|---|---|
| M7.1 | Reused V5 invoice, lines, payments and schedule; V32 adds money checks, clinic-scoped billing event table with forced RLS, item-charge uniqueness and idempotency keys | Legacy financial records must be examined before applying V32 in environments containing historical invoices |
| M7.2 | Draft invoices, item capture, issue transition/versioning, clinic currency snapshot, protected roles | Automated invoice sequencing/tax calculation and credit notes not implemented |
| M7.3 | Transactional receipt of partial/full payments, row lock on invoice, balance limits, idempotent request replay | No external payment gateway capture/settlement, no chargeback workflow |
| M7.4 | Invoice installment due dates and bounded scheduled amounts | Payment-to-installment allocation and automatic paid/overdue markers not implemented; `paid` flag must not be used to infer automatic reconciliation |
| M7.5 | Payment history, patient ledger statements, printable-in-browser payment acknowledgment data | Not a compliant tax/fiscal official receipt; dedicated PDF/print layout is pending |
| M7.6 | Manager-only payment voids and invoice voids, reasoned audit events; invoice void requires active collections resolved | Actual external refund disbursement and accounting credit notes not implemented; a void here is not proof of funds returned |
| M7.7 | Completed treatment plan item import at immutable recorded plan price, once-only constraint, patient/clinic/plan match | Automatic bill generation from completion events not implemented |
| M7.8 | Tenant-scoped finance summary, payment-method totals, live Patient 360 billing tab and front-desk-authorized treatment charge lookup | No accounting-period closes, timezone-specific clinic day, branch segmentation or export |
| M7.9 | Hosted Java/PG16, frontend, standard browser, existing real-backend clinical workflows, and synthetic M7 billing workflow | OWASP/NVD not run; no sustained load/stress/concurrency testing or production payment-certification |

## Financial controls
- Actor clinic ID is derived from authentication. Cross-clinic objects return not found or validation failure.
- DENTIST is not a cashier; financial reversals require OWNER or ADMIN.
- Financial records remain append-only except for explicit auditable status/void metadata changes.
- Concurrent payments lock invoice rows; requests require UUID idempotency keys.
- Billing plan item import derives price, quantity and discount on the server from the completed clinical plan.
- V32 unique index prevents charging a treatment plan item more than once across invoices within a clinic.
- Money is stored as PostgreSQL NUMERIC(12,2) and calculated using Java BigDecimal.
- Payment acknowledgment API disclaims official fiscal receipt status.

## Regression evidence
- Hosted CI runs were required after each implementation stage before proceeding.
- [Java17 + PostgreSQL + frontend + standard browser + live M4–M7 tests + Gitleaks](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39420766324): ALL ENABLED JOBS PASSED, OWASP skipped.
- Earlier M7 billing runner regression found HTTP 429 from shared synthetic login sessions. The isolated test runner now uses an elevated **test-only** login budget; production limiter defaults are unchanged.
- M7 live browser checks invoice creation, invalid pricing, cross-clinic denial, overpayment rejection, idempotent replay, partial/full collection, receipt metadata, protected void request, statement and dashboard.

## Required follow-ups before pilot or real financial use
1. Implement disbursed refunds, credit notes, reversal ledger and settlement evidence (M7 financial closure).
2. Automatic installment allocation, overdue aging, timezone-correct daily closeout and formal reconciliation.
3. Fiscal/tax/VAT/invoice numbering and official receipts according to target jurisdictions; verify legal/finance requirements.
4. Separate VAT, HMO/insurance claims and mixed tender where required.
5. Broader tests: simultaneous payment race, idempotency conflicts across multiple invoices, cancellation flows, browser UI finance interactions, migrations with real historical data.
6. Run OWASP/NVD, vulnerability remediation, multi-tenant penetration tests and M10 production/operational gates.
7. Do not merge this PR into `main` without owner approval. No production certification is claimed.
