# M2.5 — Final Runtime Regression & Patient Management Acceptance

**Date:** 2026-10-20 (CI/NVD rerun)
**Source branch:** main, based on M2.4 commit `be5ee2b`
**Application-code baseline:** `42c3ffbe9b7ff6239125ad98e903b1952c258eeb` (`test(m2.5): complete patient management runtime regression`); optional scan workflow updated in the current follow-up commit.
**Decision:** **M2 remains OPEN.** Local CI-equivalent tests passed, but hosted Actions is BLOCKED by invalid GitHub CLI credentials. The completed NVD scan found critical/high dependency findings and failed the configured CVSS threshold.

## Executed acceptance results

| Area | Result | Evidence |
|---|---|---|
| Java 17/Maven backend | PASS — 53 tests, 0 failures, 0 skipped; clean verify and JAR packaging | `docs/evidence/m2_5/backend-summary.txt` |
| PostgreSQL | PASS — fresh V1–V15 migrations, two-clinic tenant isolation, RLS as restricted role, V14→V15 upgrade | `docs/evidence/m2_5/database-summary.txt` |
| Frontend | PASS — 26 Vitest tests, TypeScript/Vite build; lint completed with two React async-loading warnings | `docs/evidence/m2_5/frontend-summary.txt` |
| Authenticated live browser | PASS — 1 Chromium Playwright run against Spring Boot and isolated PostgreSQL; synthetic JWKS only | `docs/evidence/m2_5/live-browser-summary.txt` |
| Mocked browser regression | PASS — 12 tests across Chromium, Firefox and WebKit; responsive desktop/tablet/mobile routes | `docs/evidence/m2_5/mocked-browser-summary.txt` |
| CI-equivalent rerun (2026-10-20) | PASS — 53 backend + 26 frontend unit + 4 Chromium browser tests; lint/build/typecheck passed; Gitleaks found no leaks | `docs/evidence/m2_5/ci-rerun-2026-10-20.txt` |
| Hosted GitHub Actions | BLOCKED — stored `gh` credential is invalid and GitHub API access failed; no run ID, SHA, or hosted job conclusions verified | `docs/evidence/m2_5/hosted-ci-status.txt` |
| OWASP Maven dependency scan | FAIL — 84 scanned (54 unique), 13 vulnerable, 179 dependency/CVE matches, 114 unique CVEs; CVSS ≥7 fails the configured policy | `docs/evidence/m2_5/nvd-scan-summary.txt`; full local HTML/JSON reports are in ignored `backend/target/` |

**Latest rerun test totals:** 83 passed (53 backend + 26 frontend unit + 4 Chromium browser), 0 failed, 0 skipped. The NVD policy check is a separate failed security gate; hosted CI is a separate blocked gate. Earlier M2.5 regression totals remain 92 passed across the larger live and cross-browser suites.

## Fixes made

- Patient updates now require an expected record version and update atomically with `WHERE version=?`; stale edits return 409 rather than overwriting a newer record.
- The Patient Care refresh loads guardians, consent, and medical history together before publishing the new section state, with request-version invalidation on obsolete requests.
- Authentication, session, invitation, patient, guardian, and clinical-history DTO string representations redact sensitive values. Spring MVC request/response body and request-parameter loggers stay at INFO even with ambient DEBUG enabled; regression assertions cover logger levels and DTO redaction.
- Migration/RLS tests now cover V15 records and use savepoints around expected database denials so a deliberate RLS rejection does not abort the remainder of the test transaction.
- Cross-browser fixture now re-enters its clinic code after returning from the rejected sign-in flow.

## Security and data integrity notes

- Dependency-Check reported 25 unique critical and 44 unique high CVEs across 13 dependencies. The scanner is a best-effort matcher; affected-version applicability has not been manually verified, and no suppressions were applied. Treat these findings as unresolved pending triage and upgrades.
- Duplicate candidate detection is advisory; parallel same-identity registrations can still create separate records. Do not merge automatically; add a deliberate identity-resolution design before stronger blocking.
- Guardian contacts are self-reported, not proof of legal authority. Administrative acknowledgement is not a signed consent or treatment authorization.
- V14→V15 preserves legacy signed fields. The new status defaults to `UNSIGNED`; legacy consent records need semantic review before any production migration.
- Live Playwright uses an ephemeral local signing key and synthetic test identities. It does not claim a real Google provider login.
- No real patient data or production workflows were used or approved.

## CI and dependency assessment

`gh auth status` reported the local GitHub credential invalid; `gh run list` could not reach the GitHub API. The workflow was not triggered, and no hosted result is claimed. Reauthenticate GitHub CLI, then run the workflow on the final commit and add the run ID, SHA, job conclusions, and artifact evidence here. Local CI-equivalent commands and results are captured in `docs/evidence/m2_5/ci-rerun-2026-10-20.txt`.

The OWASP dependency-check workflow remains present and optional; its plugin was updated to 12.1.6 with explicit connect/read timeouts and a retry limit. The NVD key in the private config file is present and a minimal authenticated request returned HTTP 200; the full direct API update hung under Dependency-Check 12.1.0 and again with 12.1.1. The final complete scan used the Dependency-Check maintained NVD 2.0 feed mirror (best-effort, updated daily), and produced HTML/JSON reports. Dependency-Check disabled the Sonatype OSS Index analyzer because no OSS Index credentials were configured. The scan exceeded the CVSS 7 threshold; triage and remediation are required before M2 development acceptance and production deployment. Re-run against direct NVD data and the full advisory sources after remediation.

## Recommended next milestone

Triage and remediate the dependency findings, rerun the full scan, and close hosted CI evidence for the final commit. Do not start M3 automatically.
