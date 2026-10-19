# M2.1 — Patient registry, demographics and search

## Scope
- Authenticated, clinic-scoped API for registration, listing/search, detail, edit and soft archive.
- Atomic clinic-local chart numbers via existing `NumberingService`.
- Exact first/last/date-of-birth duplicate candidate check, plus an explicit UI review stop.
- API audit events for create/update/archive. Archive restricted to owner/administrator.
- Working React directory and demographic form. Patient 360° clinical content remains a fictional preview separate from real patient data.

## Limitations / release gates
- Duplicate detection is advisory and does not prevent concurrent duplicates; review identity before registering.
- No clinical data, consent collection, imaging, billing or odontogram writes in M2.1.
- Before patient data use: run Java 17 build/tests, PostgreSQL RLS + two-clinic CRUD tests, frontend tests/build, Playwright coverage and privacy review.
- No production patient data authorized; use synthetic records for evaluation.

## M2.5 regression evidence (2026-10-19)
- Java 17.0.20 Maven clean verification passed: the full backend suite passed 53/53, including authenticated HTTP patient CRUD, duplicate candidates, archive, stale/concurrent edits, tenant scoping, and audit assertions.
- PostgreSQL 18.6 fresh V1–V15 migration/RLS validation passed using a restricted NOSUPERUSER NOBYPASSRLS application role and synthetic two-clinic patients.
- The V14→V15 upgrade preserved patient history and legacy consent fields; see docs/M2_5_POSTGRESQL_TENANT_ISOLATION.md for the legacy consent semantics caveat.
- Authenticated local Playwright with a synthetic Google JWKS and disposable PostgreSQL passed patient creation, search, duplicate review, edit, guardian, medical history, consent acknowledgement, archive, Patient 360°, cross-clinic denial, and delayed patient-switch checks.
- Duplicate candidate detection remains advisory. No automatic merges or uniqueness constraint are enabled because legitimate twins and same-name/same-date records need staff review.

See the final M2.5 reports and sanitized evidence files under docs/evidence/m2_5/.
