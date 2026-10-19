# M1.4.6 — Hosted GitHub Actions Evidence

**Status: BLOCKED — no current-commit hosted run exists.** Do not treat the previous successful run as certification of M1.4.6.

## Current revision and attempt

- Current M1.4.6 source and certification reports are included in this delivery's milestone commit; the exact SHA is the Git `HEAD` reported alongside this evidence. The run ID is not created.
- Workflow run ID: **NOT CREATED**.
- `gh auth status` on 2026-10-19 reported the saved `github.com` token for the active `eduardvcristobal` account is invalid and instructed reauthentication with `gh auth login -h github.com`.
- A read-only `git ls-remote --heads origin main` attempt failed with `Could not resolve host: github.com` in this environment. No workflow was triggered and no job results are claimed.
- The historical run [`39232339581`](https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39232339581) passed on older SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`. It does not certify the M1.4.6 commit.

## Workflow checks configured

`.github/workflows/backend-java17.yml` runs on `push` and `pull_request` and defines:

| Job | Configured checks | M1.4.6 hosted conclusion |
|---|---|---|
| `java17` | Temurin Java 17, PostgreSQL 16 service, Maven verification, clean migration DB, integration/RLS tests | NOT RUN |
| `frontend` | Node 24, npm ci, TypeScript, Oxlint, Vitest, production build | NOT RUN |
| `browser` | Playwright install and full browser suite; screenshots/traces uploaded on failure | NOT RUN |
| `security` | Gitleaks; dependency review when event is a pull request | NOT RUN |
| `owasp` | Optional Maven OWASP/NVD scan gated by repository variable `RUN_OWASP_SCAN` | DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION; not PASS |

## Evidence needed to close this item

After GitHub CLI authentication and network access are restored, push the committed M1.4.6 revision to a temporary branch (or run the repository workflow for the exact SHA). Save its run ID and SHA, inspect each job conclusion and test totals, and update this report. Do not cite a run against another commit. Do not enable the optional OWASP scan as part of M1; the Maven vulnerability assessment remains required before production deployment.
