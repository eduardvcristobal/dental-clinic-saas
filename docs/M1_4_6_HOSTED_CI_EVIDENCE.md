# M1.4.6 — Hosted GitHub Actions Evidence

**Status: PASS for development acceptance.** Hosted workflow run [`39249898417`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39249898417) completed successfully on commit `d9e230fc09fff40a3d11c9315361f86f058555bd` (`ci: fetch history for Gitleaks range scans`) on 2026-10-19.

## Run record

- Event: push to `m1.4.6-acceptance`
- Commit SHA: `d9e230fc09fff40a3d11c9315361f86f058555bd`
- Overall conclusion: `success`
- Workflow: [Java 17, PostgreSQL, frontend, browser, security](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39249898417)

| Job | Conclusion | Actual evidence |
|---|---|---|
| `java17` | **success** | Temurin Java 17; PostgreSQL 16 service; Maven `clean verify`; **33 tests, 0 failures, 0 errors, 0 skipped**; migrations and tenant/RLS integration test passed |
| `frontend` | **success** | Node 24; `npm ci`; TypeScript, lint, Vitest (**25 passed**), production build passed |
| `browser` | **success** | Playwright browser suite: **4 passed** |
| `security` | **success** | Gitleaks reported **No leaks detected**. Dependency Review did not run because this was a push event; the PR-only step remains enabled in the workflow. |
| `owasp` | **skipped** | **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** to M10. This is not a PASS. Complete Maven vulnerability assessment before production deployment. |

Hosted automated totals: **62 passed, 0 failed, 0 skipped test cases, 0 blocked** (33 backend + 25 frontend + 4 Playwright). The optional OWASP job is a skipped workflow job and is reported separately; it is not counted as a test case.

## Failure investigation and correction

The first current-source run, [`39249506226`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39249506226), ran on `982e436421d94a0b2aad76fa15eacaa6b79e466e`. Its Java, frontend, and browser jobs passed, but the security job failed because Gitleaks tried to scan the pushed commit range while the default shallow checkout did not contain the referenced parent commit. Its log said the revision was unknown and the repository scan failed; this was not a secret finding.

The smallest fix sets `fetch-depth: 0` only for the security job checkout, allowing Gitleaks to inspect the intended range. The rerun on `d9e230f` completed successfully and explicitly reported no leaks. No tests, dependency reviews, or secret scanning were disabled.

## Scope and remaining gates

This certifies the committed development regression workflow and M1 acceptance gates. Dependency Review remains PR-only, so it is configured but unexecuted by this push. NVD/Maven dependency assessment remains deferred and required before production deployment. Production security/privacy review, operational controls, and deployment verification are outside this development acceptance result.
