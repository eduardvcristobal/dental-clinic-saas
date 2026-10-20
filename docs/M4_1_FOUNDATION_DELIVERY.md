# M4.1 — Odontogram Domain & Database Foundation

Status: IMPLEMENTED IN SOURCE — RUNTIME ACCEPTANCE BLOCKED (NOT RUN)
Branch: `feature/m4-odontogram`
Base: main at branch creation, containing M3.1–M3.9 and V1–V21.

## Delivered
- Forward-only `V22__odontogram_foundation.sql` (preserves existing V4 `tooth_record`).
- One chart per (clinic, patient), clinic-composite foreign keys and clinic-scoped identifiers.
- Append-only event history with observation, correction and retraction events; links correction to original within the same clinic/patient.
- FDI/ISO 3950 permanent (11–48 valid quadrant/position) and primary (51–85 valid quadrant/position) tooth checks.
- Tooth/surface validation for whole-tooth, anterior incisal, and posterior occlusal notation.
- PostgreSQL FORCE RLS and fail-closed `app.clinic_id` policies for new tables; restricted runtime-role grants.
- Java `ToothNotation` validation utility and JUnit tests for numbering/surfaces.

## Important limits
- M4.1 is a domain/schema foundation, NOT a finished clinical chart API or UI. Those are M4.2–M4.4.
- Existing V4 tooth records are not backfilled into the new append-only chart. A reviewed migration/compatibility strategy must precede activation if the database contains existing tooth records.
- No supernumerary numbering model is claimed. Such cases need an explicit clinically reviewed extension rather than fabricated FDI numbers.
- No patient clinical data permitted for production use. Test with synthetic patients only.
- The previous unresolved dependency advisories remain tracked for M10 production security hardening.

## Verification matrix
| Check | Status | Evidence |
|---|---|---|
| GitHub commits on isolated M4 feature branch | PASS | GitHub commit history |
| Current schema inspected (V2/V4/V10 and V15/V21) | PASS | Repository source review |
| V22 fresh migration / upgrade V21→V22 | NOT RUN | Requires Java17 + PostgreSQL test environment |
| FORCE RLS and restricted-role isolation execution | NOT RUN | Requires PostgreSQL |
| Java 17 compilation / JUnit | NOT RUN | Requires Maven with dependencies |
| Full M0–M3 regression | NOT RUN | M4.5 development acceptance |
| Current-code hosted CI | NOT RUN | Run after tests and PR |
| Clinical API authorization | NOT IMPLEMENTED | Planned M4.2–M4.4 |

## Acceptance work for development agent
1. Run V1–V22 on disposable PostgreSQL 16 and V21→V22 upgrade.
2. Insert two isolated synthetic clinics and patients; verify cross-clinic charts and events rejected at FK and RLS boundaries.
3. Attempt to INSERT an event linked to another patient's chart in same clinic; FK must reject it.
4. Try UPDATE/DELETE of odontogram events; append-only trigger must reject both.
5. Verify invalid FDI numbers 19, 49, 56, 86 are rejected; valid 11, 48, 51, 85 accepted.
6. Validate incisal/occlusal surface restrictions.
7. Run `mvn test` on JDK 17; add PostgreSQL integration tests with restricted role and verify runtime behavior.
8. Update `PROJECT_STATUS.md` with verified test evidence before marking M4.1 fully accepted.
9. Do not merge into main without a reviewed PR and explicit owner authorization.
