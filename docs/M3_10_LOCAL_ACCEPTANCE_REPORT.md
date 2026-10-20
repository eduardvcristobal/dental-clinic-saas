# M3.10 scheduling local acceptance

**Date:** 2026-10-20

**Source:** working tree based on `0b913898310451d86e027cdab8972ce2a5df7b55`

**Decision:** Local acceptance checks passed. Hosted CI on the final commit remains open; M3 is not yet closed for development acceptance.

## Results

| Check | Result |
|---|---|
| Java 17 Maven `clean verify` | **PASS:** 70 tests, 0 failures, 0 errors, 0 skipped; JAR packaged |
| Fresh PostgreSQL migration and RLS | **PASS:** Flyway V1–V21 on separate disposable databases; restricted `dental_app` role (`NOSUPERUSER NOBYPASSRLS`) and cross-clinic isolation exercised |
| Upgrade migration | **PASS:** V14→V15 and V15→V21 tests ran against separate disposable databases, preserving synthetic patient history and consent data |
| Scheduling integration | **PASS:** 10 PostgreSQL tests covering concurrent bookings, exclusion constraints, roles, resource blocks, lifecycle events, waitlist, outbox, tenant isolation, time zones and daylight saving transitions |
| Frontend | **PASS:** 38 Vitest tests, TypeScript/Vite production build and Oxlint |
| Mocked browser | **PASS:** 10 Chromium Playwright tests, including six M3.10 calendar and check-in cases |
| Live browser | **PASS:** one Chromium Playwright workflow against Spring Boot, fresh PostgreSQL and synthetic local signing keys |
| Hosted CI on final source | **OPEN:** workflow is prepared to run M3.10 database tests on PostgreSQL 16 after a commit is pushed; no hosted result is claimed here |

The live workflow covered owner appointment type, dentist hours and chair block configuration; front desk patient search, booking, confirmation, rescheduling, check-in, completion, conflict rejection and reasoned cancellation; dentist calendar visibility; chair block rejection; cross-clinic calendar and appointment isolation; waitlist validation; and pending notification outbox entries. No notification was sent.

The local database engine was PostgreSQL 18.6. Flyway emitted a warning that this version is newer than its tested maximum of PostgreSQL 17. The GitHub workflow uses PostgreSQL 16, so its final run is required before closing M3. All clinic and patient identities in these checks were synthetic.

## Corrections made during acceptance

- The V15→V21 upgrade test now has its own environment gate and runs independently of the older M2.5 upgrade test.
- The live test checks tenant isolation over the date that contains the created appointment. A foreign branch calendar returns an empty list; foreign appointment read and status update return 404.
- The live workflow creates the `CONFIRMED` and `CANCELLED` events that it asserts in the outbox, and uses the owner UI to create a chair block before testing rejection of a booking on that chair.
- The live runner creates a uniquely named database, refuses to overwrite an existing one, and exits if a required local port is occupied or a service dies during startup.
- GitHub Actions now provisions separate M2.5 and M3.10 upgrade and scheduling databases so those tests are included in hosted Java 17 verification.

## Remaining gate

Commit the M3.10 changes, push the final source SHA, then record the GitHub Actions run ID and each job conclusion. Do not use real patient data or treat this local result as production certification.
