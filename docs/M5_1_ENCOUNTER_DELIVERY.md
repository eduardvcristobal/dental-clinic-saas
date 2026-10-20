# M5.1 — Clinical Encounter Domain & Visit Lifecycle

Date: 2026-10-20
Branch: `feature/m5-clinical-encounters` (stacked on `feature/m4-odontogram`)
Status: Implemented in source — acceptance requires successful hosted CI.

## Changes
- V25 forward-only migration extends existing V4 `visit` with encounter status, completion/cancellation timestamps, check constraints and patient lookup index.
- New `visit_lifecycle_event` with tenant-composite foreign keys, FORCE ROW LEVEL SECURITY, append-only trigger, actor, reason and timestamps.
- `EncounterController`: GET patient encounters, GET one encounter, GET lifecycle history, POST start, POST transition.
- Only OWNER/ADMIN/DENTIST may access encounter APIs; clinic ID derived from authenticated principal.
- Start validates active patient, dentist membership and optional appointment patient+dentist association; transition requires expected optimistic-lock version.
- POST complete/cancel is one-way from IN_PROGRESS with atomic version check; cancellation requires reason.
- `EncounterSecurityTest` covers unauthorized access, clinic scoping and invalid lifecycle inputs.

## Deliberately deferred
- SOAP authoring, diagnoses and formal signing (M5.2/M5.3)
- Prescriptions and attachments (M5.4)
- Odontogram integration and Patient 360 chart UX (M5.5)
- End-to-end comprehensive clinical acceptance (M5.6)
- Existing V4 visits are preserved. Legacy visits default to IN_PROGRESS; a historical reconciliation plan is needed for old signed/locked records before production release.

## Required checks
- Java 17 Maven verify, fresh V1–V25 PG16 migration and existing upgrade path.
- Clinical role and tenant RLS tests under restricted DB role.
- Real-backend signed-in workflows and concurrency tests.
- Check for M3/M4 branch divergence prior to any merging; this branch depends on M4.
- Production patient data prohibited until separate operational/security/clinical certification.

## Verification
- GitHub source writes: PASS.
- Java 17/PG16 runtime: NOT YET VERIFIED in this report.
- Browser and security acceptance: NOT YET VERIFIED.
- Merge approval: NOT REQUESTED, do not auto-merge.
