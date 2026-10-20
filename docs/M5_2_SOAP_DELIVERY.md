# M5.2 — SOAP Notes, Clinical Findings & Diagnosis

Branch: `feature/m5-clinical-encounters` (stacked on M4)
Scope: **draft clinical documentation only**. Signed notes, amendments, prescriptions, and attachments remain M5.3–M5.4.

## Implemented
- V26 adds a clinic-scoped SOAP draft, append-only SOAP revision history, and append-only structured diagnosis records.
- All three tables use FORCE ROW LEVEL SECURITY and clinic-composite foreign keys.
- Draft updates require optimistic `expectedVersion`; every successful save records the complete revision and authenticated author.
- Clinical endpoints: `GET /api/encounters/{visitId}/clinical/soap`, `PUT .../soap`, `GET .../revisions`, `GET .../diagnoses`, `POST .../diagnoses`.
- OWNER/ADMIN/DENTIST roles only. FRONT_DESK is denied.
- Save and diagnosis insertion lock the associated visit row to prevent racing a finalized encounter.
- Enforces 20,000-character limit per SOAP field; structured diagnosis codes must specify ICD10, SNODENT or LOCAL, with code and human-readable description.
- Zero implicit coding or automated diagnosis; clinicians manually enter findings. No patient data fabricated.
- Unit tests for role checks, missing encounter, invalid version, invalid input, and length limits.
- PostgreSQL integration checks verify required FORCE RLS, tenant policies, and restricted-role grants.

## Important boundaries
- Old V4 SOAP columns remain unchanged. V26 does **not** migrate historical SOAP text to the new draft table.
- Draft records are editable; complete revision snapshots persist. No assertion that drafts are legally signed.
- Diagnoses are append-only, with corrections/amendment UX deferred to M5.3. Do not use in production clinical care.
- No automatic encounter completion, prescription signing, or coding system vocabulary lookup.
- Existing M3/M4 acceptance and security restrictions still apply.
- Merge only after M3→M4 integration and owner approval; no automatic merge.

## Acceptance checks
- Fresh V1–V26 migration, restricted PostgreSQL 16 role, and clinical RLS.
- Java 17 Maven unit/integration regression; frontend TS/lint/Vitest/build.
- Standard and M4 real-backend browser suites; M5-specific real-backend SOAP workflow is still a separate acceptance gate.
- OWASP/NVD and production privacy/clinical validation remain deferred.
