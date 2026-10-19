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

## Execution results (this environment)
- `git diff --check`: passed.
- `npm ci --offline`: BLOCKED (`why-is-node-running@3.2.1` absent from local cache).
- Java 17 and Maven are unavailable; Spring build and newly authored controller tests are NOT EXECUTED.
- PostgreSQL database unavailable; no fresh RLS or cross-clinic tests executed.
- Frontend production build and Playwright E2E NOT EXECUTED.
