# M2.4 — Patient Identity, Guardian and Consent Security Regression

## Changes reviewed and corrected

1. **Patient switching and unauthorized UUIDs**: Patient 360° clears the prior record before loading a new URL-selected UUID and only renders an identity matching the current selector. On 404 or authorization failure, the prior record cannot remain visible.
2. **Clinical request races**: PatientCare uses request-version invalidation on unmount and load so prior medical-history responses cannot overwrite newer selected-patient state.
3. **Archived chart identity review**: Identity review now requires `deleted_at IS NULL` on the primary chart lookup, consistent with the registry and other patient-care accessors.
4. **Guardian error UX**: An unsuccessful guardian creation no longer erases the operator's typed name/relationship. The UI calls out that contact relationships do not establish legal authority for minor consent.
5. **Consent semantics**: UI cautions explicitly that neither `UNSIGNED` nor `ACKNOWLEDGED` authorizes treatment; no signatures or legal authority are implied.
6. **Unit regression source**: Added `PatientCareSecurityTest` to cover forbidden FRONT_DESK clinical reads/writes, archived/foreign patient denial, archived identity query, and acknowledgement transition conflicts.

## Evidence and remaining acceptance tasks

- `git diff --check`: passed.
- `npm ci --offline --no-audit --no-fund`: **blocked** because registry tarball `why-is-node-running@3.2.1` is not cached.
- Java runtime installed here: OpenJDK **21**, while the project targets **17**. Maven and PostgreSQL server/client were not available. No backend tests or migration checks were run.
- Live Google/browser identity workflows were not rerun. There are no new claims about hosted CI.

### Local certification script (authorized isolated database; fictional patients only)

1. Use Java 17 and PostgreSQL 16 or 18, apply Flyway V1–V15 and run `cd backend && mvn clean verify`.
2. Verify tenant RLS using a restricted database role, creating patients, guardians, and unsigned consents in two distinct disposable clinics, and test both foreign UUID access and archive denial.
3. `cd frontend && npm ci && npm run lint && npm run test && npm run build && npm run test:e2e`.
4. In authenticated Playwright, verify all four clinic roles, including FRONT_DESK 403 on medical history, switch patients during slow responses, direct-link to foreign or archived patient, guardian validation and primary contact, guardian handling for a minor, unsigned→acknowledged transition, and repeated acknowledge refusal.
5. Record every command's exit code and actual totals, then only mark M2.4 accepted once clean.

**Decision:** M2.4 source fixes delivered; regression/certification not complete. M2 remains open. No real patient data until full production privacy and security approval. Maven NVD remains deferred to the production hardening gate.
