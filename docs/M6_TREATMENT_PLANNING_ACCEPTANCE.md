# M6.1–M6.7 — Treatment Planning Delivery & Acceptance

Date: 2026-10-20
Branch: `feature/m6-treatment-planning` (stacked on M5, not merged into main)
Status: SOURCE IMPLEMENTED — final hosted CI review required

## M6.1 — Existing domain reuse + migration
- Reuses V4 `procedure_catalog`, `treatment_plan`, `treatment_plan_item`, and `visit_procedure`; does not create parallel plan tables.
- V30 adds append-only `treatment_plan_event`, RLS, clinic-scoped consent evidence reference and staff attribution.
- V31 adds patient-to-plan audit FK, no-delete item protection, accepted-price integrity, FDI and net amount checks.
- No old V4 or V5 migration altered.

## M6.2 — Treatment builder
- Clinic-filtered procedure catalog and active dentist lists, patient-linked plan create/read/list, scoped plan item insertion.
- React Treatment Plans tab in Patient 360 offers plan selection, procedure catalog, tooth selection, quantity, price, discount and phase-1 item addition.
- Only OWNER/ADMIN/DENTIST may access clinical treatment plan APIs; clinic always derived from authenticated principal.
- No fabricated clinical findings under real patient identity.

## M6.3 — Estimate and pricing
- Net estimate `sum(unit_price * quantity - discount)` uses BigDecimal in Spring Boot and database constraints.
- Per-plan billing preview and clinic currency lookup are READ-ONLY. No invoice/payment is created.
- Discount <= line gross, positive quantities and nonnegative financial figures.
- Tax/VAT and insurance coverage estimation, financing and price-tier approvals deferred to M7.

## M6.4 — Consent / acceptance and versioning
- Plan lifecycle: DRAFT → PROPOSED → ACCEPTED or DECLINED → IN_PROGRESS → COMPLETED.
- Optimistic version match for status changes and auditable actor-event history.
- Acceptance requires an explicit consent-evidence reference; does NOT constitute consent capture, e-signature or legal verification.
- Accepted scope/prices cannot be changed by existing item updates (V31 database trigger).
- Full plan revisions must use a separate plan; rich revision/consent digital-signature UX is future work.

## M6.5 — Progress and encounter evidence
- Treatment items progress PLANNED → SCHEDULED or COMPLETED, or SCHEDULED → COMPLETED; linked completed encounter must belong to same patient/clinic.
- Patient 360 allows marking an item as scheduled and marking completed with encounter evidence.
- IMPORTANT: SCHEDULED is a progress flag, NOT an appointment reservation. Appointment booking linkage and dentist/chair availability confirmation are not implemented in this milestone.
- No procedure ledger event is invented merely by changing the planned item's status.

## M6.6 — Odontogram / encounters / billing preview
- FDI tooth validation on plan items, treatment completion visit FK to existing clinical visits.
- Financial preview uses tenant-configured clinic currency, not a hardcoded PHP assumption.
- Actual bill generation/payments and claims belong to M7, not M6.

## M6.7 — Verification matrix
| Gate | Status |
|---|---|
| Isolated stacked branch; main untouched | PASS |
| Procedure-domain implementation commits | PASS |
| V1–V31 fresh PostgreSQL schema | PENDING CI |
| Java 17 Maven tests | PENDING CI |
| React typecheck/lint/Vitest/build | PENDING CI |
| Legacy Chromium browser suite | PENDING CI |
| Real Spring Boot + PG16 M4–M6 browser tests | PENDING CI |
| M6 security: cross-clinic, FRONT_DESK, invalid money, missing consent | PENDING CI |
| Gitleaks | PENDING CI |
| OWASP/NVD risk review | DEFERRED to M10 |
| Production real-patient clinical authorization | BLOCKED |
| Legal consent/e-signature workflow | NOT IMPLEMENTED |
| Actual scheduled appointment links | NOT IMPLEMENTED |
| Invoice, tax, insurance and payment settlement | NOT IMPLEMENTED (M7) |

## Integration discipline
1. Finish M3.10 independently. Review and merge after owner's approval.
2. Reconcile M4, rerun CI, merge M4 after owner's approval.
3. Reconcile M5, rerun CI, merge M5 after owner's approval.
4. PR #3 (M6) targets M5. Retarget to main only when upstream is accepted; run CI against final merge commit.
5. Never auto-merge. Avoid uploading real patient data or secrets.
