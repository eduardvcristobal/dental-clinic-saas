# M4 — Odontogram & Dental Charting Blueprint

Status: PLANNING ONLY — NOT IMPLEMENTED
Branch: `planning/m4-odontogram-blueprint`
Date: 2026-10-20

## Coordination / baseline gate
- GitHub `main` at planning time does not show accepted M2.5.1 or M3.1–M3.9; do not create implementation migrations or API contracts from this stale baseline.
- Other agent is finishing M3.10. Preserve that agent's feature branch and changes.
- After M3 passes review and is merged, confirm `main` includes the M2 patient APIs, authenticated clinic tenancy, and Flyway V1–V21.
- Create `feature/m4-odontogram` **from updated main** then, not from this planning branch. Port this plan as needed.
- Reserve V22 onward *provisionally*. Resolve migration numbering against actual merged `main`; never edit previously applied migrations.
- Use isolated worktrees if two agents operate on the same local checkout.
- Never merge an unreviewed planning branch or push to main without approval.

## M4.1 — Foundation
- Model clinic-scoped odontogram chart, tooth identity, tooth surfaces, findings/conditions and provenance with explicit audit metadata.
- Use ISO 3950/FDI numbering: permanent quadrants 1–4, teeth 1–8; primary quadrants 5–8, teeth 1–5.
- Represent absence, unerupted, mixed dentition and supernumerary teeth without forcing incorrect notation.
- Validate permitted surfaces per tooth category, dates, duplicate chart creation and linked active patients.
- Enforce tenant boundaries in API and PostgreSQL RLS, clinical role authorization and FK constraints.
- Add forward-only migrations and integration/security tests; separate successful Git commit.

## M4.2 — Interactive chart
- Render adult and primary dentition, mixed-dentition selection, interactive tooth/surface selection, legends, responsive keyboard-accessible UI and save/error states.
- Load real clinic/patient data; never substitute fictional findings under real patient identity.
- Prevent stale patient cross-selection display and unauthorized clinical history fetches.
- UI/component, accessibility and browser tests; separate successful Git commit.

## M4.3 — Findings and history
- Record tooth/surface conditions, restorations, treatments and dated findings, with author and source.
- Distinguish current clinical state from full chronological history; ensure conflict handling and immutable event history.
- Link encounter/treatment plan/procedure only when genuine accepted domain API exists; otherwise prepare nullable reference and label deferred.
- Define corrections/amendments rather than destructive overwrite; separate successful Git commit.

## M4.4 — Security and audit
- Only authorized clinical staff can create/edit clinical findings; Front Desk must not access protected chart content without explicit authority.
- Enforce per-clinic isolation for patient, tooth, finding, history, files and any related IDs.
- Audit meaningful actions without leaking PHI or credentials into application logs.
- Test concurrent edits, corrections, archived patients, forged IDs, wrong role and cross-clinic paths; separate successful Git commit.

## M4.5 — Regression and development acceptance
- Run Java 17 Maven verification, fresh and upgrade Flyway migration paths, PostgreSQL 16 RLS/restricted-role testing, frontend typecheck/lint/Vitest/build.
- Run authenticated Playwright against Spring Boot/PostgreSQL with synthetic patients; separate any mocks from real backend tests.
- Verify responsive/keyboard behavior and M0–M3 regression; hosted CI on exact application-code commit.
- Preserve documented Maven CVE residual risk; development acceptance != production clinical-data approval.
- Update PROJECT_STATUS.md and evidence reports; commit after successful milestone and open PR for review, not auto-merge.

## Essential invariants
1. Never infer authorization from a URL patient ID; derive clinic identity from authenticated context and enforce in SQL.
2. Never use real patient data before production security/privacy certification.
3. Historical charting must retain who changed what, when, and why, including corrections.
4. ISO tooth notation and surface codes must be validated consistently in API and database.
5. Backend and frontend behavior must reflect exactly what is implemented; no mock chart presented as persisted clinical data.
6. Do not mark tests PASS unless actually executed with reproducible evidence; distinguish PASS/FAIL/BLOCKED/NOT RUN/DEFERRED.
7. Git: separate commit per successful M4.x milestone; never force-push or silently rewrite another agent's work.

## Integration sequence
1. Other agent pushes M3.10 feature branch and opens PR.
2. Review M3 CI/security/migrations; merge into main only with owner approval.
3. Verify main has M2.5.1 accepted source and V1–V21 migrations.
4. Create `feature/m4-odontogram` from then-current main; transfer this roadmap.
5. Implement and test M4.1–M4.5 with milestone commits.
6. Open draft PR; synchronize with main, rerun all tests, request review.
7. Merge only with explicit owner approval and passing required gates.
