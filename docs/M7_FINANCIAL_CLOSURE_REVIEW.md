# M7 Financial Closure Follow-up — Development Review

Date: 2026-10-20
Branch: `feature/m7-financial-closure` (from the M7 merge on main)

## Implemented
- **M7.10:** V33 creates forced-RLS, tenant-keyed, immutable `payment_refund` events with disbursement reference, actor, reason, and idempotency key. OWNER/ADMIN manually records an **already-disbursed** refund. This is **not** an instruction to a bank, card network, payment gateway or wallet provider and cannot be presented as proof of external settlement without independently verified reference evidence.
- **M7.10:** Existing balances, collections, statements, invoice statuses and payment acknowledgments account for refund amounts. Payment void after refund is blocked to avoid double reversal.
- **M7.11:** Read-only FIFO allocation of net collected funds to installment due dates and deterministic overdue amounts for an explicitly supplied as-of clinic-local date. This is derived accounting, not settlement posting or automated reminders.
- **M7.12:** V34 stores tenant-isolated seller fiscal registration readiness metadata. OWNER/ADMIN may configure; the API always returns `officialReceiptEnabled=false` and `documentStatus=NOT_AUTHORIZED`. Payment acknowledgments stay non-official.
- **M7.13:** Fresh migration version assertions and role/input protection unit tests.

## Mandatory jurisdictional compliance gate

Philippines as example jurisdiction, **not an assumption for every clinic**:
- The BIR Ease of Paying Taxes implementation, RR 7-2024, treats an invoice as the primary document evidencing sale of services; receipts are generally supplementary documents. See [BIR RR 7-2024](https://bir-cdn.bir.gov.ph/BIR/pdf/RR%20No.%207-%202024.pdf) and [BIR RR 11-2024](https://bir-cdn.bir.gov.ph/BIR/pdf/RR%2011-2024.pdf).
- BIR RMC 77-2024 clarifies VAT/non-VAT invoice issuance. Registered names, registered addresses, TIN/branch codes, serial numbers, invoice particulars, authorized issuance medium/permit, taxable/exempt/zero-rated breakdown, statutory discounts and registrations require jurisdiction-specific review. See [BIR RMC 77-2024](https://bir-cdn.bir.gov.ph/BIR/pdf/RMC%20No.%2077-2024%20Digest.pdf).
- A tax practitioner and the clinic's finance owner must confirm up-to-date applicability (including newer BIR/e-invoicing issuances), tax status, exemptions, printing/e-invoicing authorization, authority to print or permit, invoice serials and delivery/retention requirements before the platform issues any official fiscal document. Do not infer validity from the seller profile alone.
- Real external refund payouts, proof validation, provider settlements and provider failure/retry workflows are **not** yet integrated. A manual `reference` is operator-entered evidence only.

## Explicit gaps to resolve before financial production/pilot certification
- Refund balance ledger has only manually recorded payout evidence, not online disbursement or provider-confirmed settlement.
- FIFO installment allocation is a derived view and does not persist payment-allocation provenance. It must be tested with concurrency, date-boundary, refund and mid-plan schedule changes.
- Credit/debit notes and invoice correction workflows remain absent. Tax calculations, serial numbering, invoice authorization, official receipts and fiscal PDF templates remain intentionally disabled.
- Dashboard today's collections need independent local-day/timezone-aware cashflow definitions, especially for refunds occurring on a different date from original payment.
- Backfill/upgrade of real historical payment data must be verified before deployment. No real patient data should be used in tests.
- Finish M10 dependency scan, high-risk findings, backups, data retention, tenant isolation, multibrowser, and full clinical/finance E2E.

## Verification
To be filled from CI run after code closure. PR remains draft; do not merge automatically.
