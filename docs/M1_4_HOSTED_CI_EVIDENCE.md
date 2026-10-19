# M1 Hosted GitHub Actions Evidence

**Captured:** 2026-10-19 from the authenticated GitHub Actions web page in Chrome.  
**Workflow:** Backend Java 17 (`.github/workflows/backend-java17.yml`)  
**Run:** [39232339581](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39232339581)  
**Event/branch:** `push` on `main`  
**Commit:** [`2791a1c578f19174ea609b53ad84ed656fc9faf9`](https://github.com/eduardvcristobal/dental-clinic-saas/commit/2791a1c578f19174ea609b53ad84ed656fc9faf9)  
**Run result:** Success; total duration 1m 37s; completed 2026-10-19 17:44 GMT+8.

## Job conclusions

| Job | Conclusion | Duration | Evidence |
|---|---|---:|---|
| `java17` | success | 1m 14s | `mvn -B -ntp verify`; 30 tests, 0 failures, 0 errors, 0 skipped. Flyway applied all 14 migrations to the disposable PostgreSQL service; migration/tenant-isolation test passed. |
| `frontend` | success | 18s | TypeScript build check, lint, Vitest, and production build completed; Vitest summary: 6 files and 24 tests passed. |
| `browser` | success | 1m 34s | `npm run test:e2e` completed successfully. This suite uses synthetic Google credentials and mocked network calls; it is not evidence of live Google sign-in or invitation acceptance. |
| `security` | success | 6s | Gitleaks summary: no leaks detected. Dependency Review is conditional on pull-request events, so its step did not execute on this push run. |
| `owasp` | skipped | 0s | Optional job condition was not enabled. This matches the owner-accepted NVD deferral; it is not a scan pass. |

## Artifacts

The run page listed two artifacts:

- `gitleaks-results.sarif` — SHA-256 `42505cfeebacfcb66ce3f1539658144c88a41170feac3f570ef342f732899a41`
- `playwright-evidence` — SHA-256 `f8df09c19474632005db8338ecdf63409b92d952f2d8cedada5a81b30c9877fc`

## Acceptance interpretation

This confirms the hosted workflow record cited by the prior checklist and corrects the earlier “unverified” status. The five job conclusions are captured above, including the intentionally skipped OWASP job. The CI pass covers the source at SHA `2791a1c…`; it does not prove that the current local checkout at `c18b9f8…` was tested, nor does it replace the still-required real Google login and staff invitation acceptance test. M1 remains open until those live-account checks are completed. The Maven vulnerability assessment remains deferred to M10 and mandatory before production deployment.
