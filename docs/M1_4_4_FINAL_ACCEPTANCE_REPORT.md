# M1.4.4 — NVD Deferral & Final Acceptance Reconciliation

**Date:** 2026-10-19  
**Decision:** **M1 remains OPEN. M2 is not started. The application is not production-ready.**

## NVD / Maven dependency assessment

**Status: DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION. This is not PASS.** The owner accepted deferring OWASP Dependency-Check/NVD to **M10 — Production Security Hardening**. Maven dependency vulnerabilities are not currently assessed. This residual risk remains a mandatory production deployment gate: complete a Maven vulnerability assessment using current advisory data, classify applicability, and document remediation/disposition of all applicable high and critical findings before deployment.

The existing optional `owasp` job in `.github/workflows/backend-java17.yml` remains available behind `vars.RUN_OWASP_SCAN == 'true'` and still requires `NVD_API_KEY`. No backend tests, frontend tests, secret scanning, or pull-request dependency review jobs were disabled or weakened.

## Preflight change and verification

Updated `scripts/m1_4_3_preflight.sh` so NVD key absence prints an advisory and does not fail M1 development preflight. It does not claim the scan passed. Other prerequisites continue to control the exit code.

Executed with NVD unset and matching synthetic OAuth client ID values (values were not real credentials):

```sh
env -u NVD_API_KEY \
  GOOGLE_CLIENT_ID=synthetic-client-id \
  VITE_GOOGLE_CLIENT_ID=synthetic-client-id \
  ./scripts/m1_4_3_preflight.sh
```

Environment: OpenJDK **17.0.20**, Maven **3.9.16**, Node **24.18.0**, npm **11.16.0**.

Result: Git remote, matching client ID placeholders, Java 17, Maven, and npm reported ready; NVD reported `ADVISORY ... deferred to M10`; **exit 0**. `bash -n scripts/m1_4_3_preflight.sh` passed. `git diff --check` passed before commit. These checks verify preflight behavior and patch formatting only; they do not verify OAuth, database, or CI.

## Hosted CI run 39232339581 reconciliation

There is a material record conflict. Commit `07cc18f` and the acceptance checklist state that run `39232339581` passed on `main`, listing Java 17/PostgreSQL 16, frontend, browser, and security jobs. The current checkout does not contain the Actions run JSON, job logs, downloaded artifacts, or an exact SHA tied to that run.

Read-only verification attempts:

- `git remote -v`: `origin` is configured as `https://github.com/eduardvcristobal/dental-clinic-saas.git`; branch `main` points to `07cc18f` and tracks `origin/main` locally.
- `gh auth status`: local GitHub credential is invalid.
- `gh run view 39232339581 --repo eduardvcristobal/dental-clinic-saas --json databaseId,headSha,headBranch,status,conclusion,event,createdAt,updatedAt,jobs,url`: GitHub API connection unavailable.
- `curl --fail --silent --show-error --location https://api.github.com/repos/eduardvcristobal/dental-clinic-saas/actions/runs/39232339581`: HTTP 404 without authenticated access.
- `git ls-remote origin HEAD refs/heads/main`: failed because `github.com` did not resolve in the shell environment.

**Disposition: NOT VERIFIED.** The documentation commit establishes that a green run was reported, but not that the run exists, which SHA it tested, which jobs completed, or their actual test totals. The previous checklist PASS has been corrected. No hosted run was started during M1.4.4.

To close this item, obtain the run page/API response and job logs/artifact manifest through an authenticated GitHub session, or run the workflow again and record the exact run ID, tested SHA, all job conclusions, and artifact checksums.

## Live Google sign-in and invitation acceptance

**Status: NOT COMPLETED.** No real Google token, account password, or identity credential was logged or saved. The required second authorized recipient's email has not yet been supplied in the active session. Local app ports 5173/5174/8080 were not running, and repository-local `.env.local`/OAuth configuration files were absent at inspection. Without the recipient address and a functioning configured OAuth/browser session, the real invite cannot be issued and accepted truthfully.

The existing M1.4.2 browser suite used synthetic credentials and intercepted network calls; it is not evidence of real Google authentication or real invitation acceptance. The live test remains required for M1 closure.

## Test/evidence totals

- M1.4.4 source/preflight change: **1 preflight command passed**, shell syntax passed, whitespace/diff check passed.
- Product test suites were not rerun during this documentation/preflight reconciliation.
- Prior M1.4.2 reports record **30 backend + 24 frontend + 3 Playwright = 57 passed, 0 failed, 0 skipped**. These are historical recorded results, not M1.4.4 executions.
- Real Google/invitation flow: **not run**.
- Hosted CI: **not verified** (reported run unconfirmed).
- Maven NVD scan: **deferred exception**, not a passed or failed scan.

## Acceptance decision and next steps

Keep M1 OPEN and M2 blocked until live Google login plus staff invitation acceptance are verified with authorized test accounts in an isolated clinic, and the hosted CI record is independently confirmed or rerun with preserved evidence. NVD deferral is accepted for development only; complete the Maven vulnerability assessment and finding disposition before production deployment. Do not describe the application as production-ready.
