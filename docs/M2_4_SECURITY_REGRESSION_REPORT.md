# M2.4 — Patient Identity, Guardian and Consent Security Regression

## Changes reviewed and corrected

1. **Patient switching and unauthorized UUIDs**: Patient 360° clears the prior record before loading a new URL-selected UUID and only renders an identity matching the current selector. On 404 or authorization failure, the prior record cannot remain visible.
2. **Clinical request races**: PatientCare uses request-version invalidation on unmount and load so prior medical-history responses cannot overwrite newer selected-patient state.
3. **Archived chart identity review**: Identity review now requires `deleted_at IS NULL` on the primary chart lookup, consistent with the registry and other patient-care accessors.
4. **Guardian error UX**: An unsuccessful guardian creation no longer erases the operator's typed name/relationship. The UI calls out that contact relationships do not establish legal authority for minor consent.
5. **Consent semantics**: UI cautions explicitly that neither `UNSIGNED` nor `ACKNOWLEDGED` authorizes treatment; no signatures or legal authority are implied.
6. **Unit regression source**: Added `PatientCareSecurityTest` to cover forbidden FRONT_DESK clinical reads/writes, archived/foreign patient denial, archived identity query, and acknowledgement transition conflicts.

## M2.5 security regression evidence (2026-10-19)
- Java 17.0.20 Maven clean verification: 53 passed, 0 failed, 0 skipped. PostgreSQL 18.6 fresh V1–V15 migration, V14→V15 upgrade, restricted-role RLS, tenant FK, and authenticated HTTP tests passed.
- Patient updates now require the current record version and use an atomic version predicate. Stale updates return 409; two concurrent updates using the same version return exactly one success and one conflict.
- The live Playwright run used a local synthetic Google JWKS service and isolated PostgreSQL. It passed the patient/guardian/history/consent/archive flow, delayed patient switching, cross-clinic 404, and Front Desk history 403 checks.
- Auth/session/invitation DTO string representations redact Google identity, access, refresh, and invitation tokens. Regression tests cover this. The final local backend log contained no JWT strings; traces/screenshots/video are disabled in the live test config.
- Duplicate candidates remain advisory. There is no automatic merge or duplicate-blocking unique constraint; a concurrent duplicate registration can still create separate charts and needs staff identity review.
- The V14→V15 upgrade preserves legacy signed_at/signed_by columns while the new status defaults to UNSIGNED. Existing legacy consent records need explicit semantic review before any production migration.
- ESLint/Oxlint completed with two set-state-in-effect warnings in the asynchronous Patient 360° and patient-care loading effects; no lint errors.
- Hosted GitHub Actions evidence is BLOCKED because gh reports an invalid stored credential and GitHub API access fails. No hosted result is claimed.

**Decision:** M2 remains open for development acceptance until hosted CI runs on the final committed application SHA. NVD remains DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION to M10; complete a Maven vulnerability assessment before production deployment. No real patient information or production clinical workflows are authorized.
