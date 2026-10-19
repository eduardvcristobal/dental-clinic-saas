# M2.5 — Frontend & Browser Regression

## Frontend checks

- `npm ci --no-audit --no-fund` installed from the committed lockfile.
- `npm run lint`: PASS with two `react(set-state-in-effect)` warnings, in Patient Care asynchronous loading and Patient 360 asynchronous loading; no lint errors.
- `npm test -- --reporter=dot`: PASS, 6 test files and 26 tests.
- `npm run build`: PASS; TypeScript project build and Vite production bundle (51 modules) succeeded.

## Authenticated live Playwright

**PASS — 1 Chromium test** against the actual local Spring Boot backend and a new isolated PostgreSQL database. An ephemeral local RSA/JWKS service issued short-lived synthetic Google-compatible test identities. No real Google account, credential, or identity token was used. Live trace, screenshot, and video capture were disabled; backend debug logging was disabled and token-bearing DTOs redact their `toString()`.

The browser flow created a minor synthetic patient, verified chart number and duplicate review, added/removed a guardian contact, saved synthetic medical history, requested and administratively acknowledged consent without describing it as signed, opened Patient 360°, delayed one patient response and rapidly switched records, rejected a foreign-clinic deep link, checked Front Desk clinical-history denial and cross-clinic 404, edited demographics, and archived the record.

## Mocked browser suite

**PASS — 12 tests** across Chromium, Firefox, and WebKit. The suite exercises clinic and admin routes, Google widget lifecycle, invitation-form selections, and responsive desktop/tablet/mobile layout. All service calls are intercepted; these results are explicitly mocked and are not counted as live backend coverage.

Artifacts from the live run were not retained because they can capture authenticated UI state. Sanitized results: `docs/evidence/m2_5/live-browser-summary.txt` and `docs/evidence/m2_5/mocked-browser-summary.txt`.
