# M3.1–M3.9 Scheduling implementation handoff

## Delivered source

Nine separate sequential milestone commits on top of M2.5.1:

- M3.1 `6af2b99` — appointment types, buffers and scheduling policies
- M3.2 `539bd97` — dentist availability/time off, V17
- M3.3 `9201fc1` — chair maintenance blocks, V18
- M3.4 `28f3039` — authenticated clinic-scoped booking API
- M3.5 `4ed2a04` — calendar and scheduling workspace UI
- M3.6 `f624002` — transitions, rescheduling and appointment events, V19
- M3.7 `067f197` — check-in/queue and access tests
- M3.8 `4888ee4` — waitlist/expiry, V20
- M3.9 `32179f5` — notification-ready outbox, V21; **no external messages sent**

Post-milestone corrections:
- `0ab104d` — synchronize resource edits and validate active resources/timestamps
- `95b6fb4` — fix a syntactically malformed PostgreSQL tenant-RLS policy expression in V17

## Validation executed here

- `git diff --check`: passed.
- `npm ci --offline --ignore-scripts --no-audit --no-fund`: **BLOCKED** (`why-is-node-running@3.2.1` tarball absent from local npm cache).
- Java runtime available here is OpenJDK 21, **not Java 17**; Maven executable and wrapper unavailable. Java 17 Maven tests **NOT RUN**.
- PostgreSQL integration environment/psql unavailable; Flyway V16–V21 and RLS migrations **NOT RUN**.
- Authenticated browser/CI testing **NOT RUN**.
- Existing scheduling unit tests are newly added but **NOT RUN**.

## Required local-agent acceptance checks (before declaring M3.1–M3.9 functional)

1. Run Java 17 `mvn clean verify`, resolve compilation and test failures.
2. Fresh database V1–V21 and V15→V21 upgrades on supported PostgreSQL; restricted-role RLS/two-clinic tests.
3. Authenticated booking, dentist/chair overlap, maintenance blocks, timezone/DST and concurrency tests.
4. Run frontend `npm ci`, lint, Vitest, typecheck/build, authenticated Playwright across roles and viewports.
5. Validate appointment status transitions, cancellation, reschedule, queue, waitlist and outbox event consistency under simultaneous requests.
6. Confirm role/access and branch assignments, and identify gaps in buffer enforcement, walk-in workflow, waitlist offers and event delivery policies.
7. Hosted GitHub Actions on final source commit; capture run and tested SHA.

**Acceptance status: IMPLEMENTED IN SOURCE — NOT RUNTIME CERTIFIED.** M3.10 remains an explicit regression/acceptance gate. Do not use real patient data.

**Working tree note:** a pre-existing change to `docs/evidence/m1_4_1/desktop-command-center.png` was present when the resumed task began. It was not staged or committed as part of M3 work.
