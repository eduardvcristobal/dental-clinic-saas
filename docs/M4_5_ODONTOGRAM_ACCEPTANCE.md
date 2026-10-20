# M4.1–M4.5 — Odontogram Delivery & Acceptance Report

Date: 2026-10-20
Branch: `feature/m4-odontogram`
Target: `main`
Status: IMPLEMENTED IN SOURCE; acceptance NOT YET CERTIFIED. Do not merge until mandatory CI and end-to-end gates pass.

## M4.1 — Clinical domain and database foundation
- V22 adds `odontogram_chart` and append-only `odontogram_finding_event` with clinic/patient composite foreign keys and FORCE RLS.
- Existing V4 `tooth_record` is preserved without destructive alteration.
- FDI notation: 32 adult and 20 primary teeth; valid surface codes are checked at API and database levels.
- Java `ToothNotation` helper with unit tests.
- No invented supernumerary FDI codes.

## M4.2 — Interactive chart
- Patient 360 selects a real clinic patient before chart access; authorized clinical roles see the Odontogram tab.
- FDI chart shows permanent, primary or mixed dentition; selects tooth and permitted surface; indicates recorded findings.
- Clinical findings are retrieved from `/api/odontogram/patients/{patientId}` and inserted into `.../events`.
- No fictional dental conditions appear under a real patient's record. Front Desk is denied clinical chart access.
- Odontogram navigation routes through Patient 360 to choose patient identity.
- Added `odontogram.test.ts` for dentition and surface controls.

## M4.3 — Clinical findings and history
- Record observation, correction and retraction events with clinician, tooth, surface, condition, status, notes and timestamps.
- Corrections and retractions reference original events. All events are retained, not overwritten.
- UI displays chronological history for the selected tooth and highlights superseded events.
- Existing clinical encounters/procedure execution are not forged or automatically linked; M5 and M6 integrations remain future milestones.

## M4.4 — Security and event integrity
- Clinical role checks on both GET and POST; clinic ID always comes from authenticated principal.
- Active patient validation; chart and events remain bounded by same-clinic foreign keys.
- V23 prevents duplicate supersession atomically; V24 validates original tooth/surface matches corrected entry inside PostgreSQL.
- V22 trigger prevents UPDATE/DELETE of historical events. Auditable event provenance includes `recorded_by` and `recorded_at`.
- Added `OdontogramSecurityTest` and restricted-role integration assertions in `MigrationAndTenantIsolationTest`.

## M4.5 — Full regression / acceptance
| Requirement | Status | Notes |
|---|---|---|
| Separate milestone/fix Git commits | PASS | GitHub feature branch |
| Current branch isolated from main | PASS | Confirm via compare |
| Java 17 clean Maven build/unit tests | NOT RUN | Requires hosted CI or local JDK 17 |
| PostgreSQL fresh V1–V24 migrations | NOT RUN | Requires actual PostgreSQL |
| V21→V24 upgrade with existing records | NOT RUN | Requires disposable upgrade DB |
| PostgreSQL RLS and immutable-history tests | NOT RUN | Added test coverage; execution pending |
| React TypeScript/lint/Vitest/build | NOT RUN | Requires package installation |
| Real authenticated backend/browser odontogram E2E | NOT RUN | Requires test environment and synthetic clinic data |
| Cross-clinic BOLA regression | NOT RUN | Test implementation added; runtime evidence required |
| M0–M3 regression | NOT RUN | Not independently executed |
| Hosted GitHub Actions on exact commit | NOT RUN | Pending PR workflow |
| OWASP/NVD production certification | DEFERRED | Accepted M10 exception, track residual vulnerabilities |

## Outstanding acceptance/security work
1. Run GitHub Actions and repair all failures on this feature branch.
2. Verify fresh V1–V24 and V21→V24 schema upgrade on disposable PG16.
3. Exercise clinical role denial, forged foreign clinic/patient UUIDs, archived patients and unauthorized requests through real HTTP.
4. Test concurrent corrections and row-level isolation with the actual restricted DB role.
5. Complete authenticated Playwright workflow on 3 responsive layouts. Mocked tests cannot substitute for this.
6. Review legacy V4 tooth_record compatibility/backfill before claiming migration of historical data. No historical records are silently modified or backfilled.
7. Confirm chart event correction workflows comply with clinical documentation/amendment policy before any real patient use.
8. Rebase the branch onto newly accepted M3.10 changes and rerun all checks before merge.
9. Do not treat the unrun checks as PASS. Do not use real patient information until production security/privacy certifications.
10. Never auto-merge: require owner authorization after independent PR review.

## Development decision
M4.1–M4.4 source functionality has been delivered. M4.5 development acceptance remains OPEN until CI, PostgreSQL and authenticated browser verification pass.
