# M9 — Reports & Analytics implementation blueprint

Baseline: `main` after merged M8.11 PR #11. This document starts M9.1; no report API or UI is asserted to be implemented.

## Purpose
Provide tenant-isolated clinic reports that reconcile to actual appointments, treatments and financial ledgers, without deriving figures from demo content or exposing data across clinics.

## Sequential sub-milestones
- **M9.1 — Report definitions and contract audit:** identify existing appointment, treatment, invoice, payment and refund data; define report dimensions, date range, timezone, permissions, accounting definitions and source-of-truth tables.
- **M9.2 — Reporting query foundation:** reusable clinic-scoped parameters, stable pagination, bounded date range, indexes, tenant enforcement and defensive input validation.
- **M9.3 — Treatment production:** completed and signed treatment measures, provider attribution, net production and reversed/voided exclusions.
- **M9.4 — Collections:** settled payments less refunds, method breakdown and reconciliation against M7 financial ledger.
- **M9.5 — No-shows and scheduling:** appointment outcomes and rate denominators clearly specified, timezone-correct filters.
- **M9.6 — Accounts receivable:** aging and outstanding balances reconciled to invoices/allocations/credits.
- **M9.7 — New-patient analytics:** unique first-seen patients, registration vs first visit distinctions, retention-friendly aggregates.
- **M9.8 — React reports workspace:** filters, loading/empty/error UI, summary figures, drill-downs, accessibility and mobile layout.
- **M9.9 — Validation and draft release:** PostgreSQL tenant isolation and totals parity, Java integration, React Vitest, Playwright E2E, permissions, performance, documentation and acceptance.

## Rules
- Run successful CI/test gates before advancing each implementation phase.
- Commit significant verified delivery units; final commit is acceptable where the grouping stays reviewable.
- Draft PR against `main`, no automatic merging.
- Synthetic fixtures only; no PHI in snapshots, logs or CI artifacts.
- Reports are read-only, operate under clinic FORCE RLS, and do not bypass role authorization.
- Continue M8.11 `outbound-enabled=false`, `worker-enabled=false`, `patient-actions-enabled=false` until separate release authorization.
- Final financial definitions and clinical attribution need actual schema review before SQL implementation.
