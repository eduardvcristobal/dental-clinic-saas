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
- `gh auth status` still reports the local CLI credential as invalid even after the browser device-flow page confirmed connection.
- GitHub API and `git ls-remote` attempts from the shell remained unavailable; the run metadata and conclusions were therefore captured from the authenticated browser session instead.

**Disposition: VERIFIED — PASS for required hosted CI jobs.** The earlier report recorded a not-verified status because shell/API access was unavailable. On 2026-10-19, the run page was inspected in the already authenticated GitHub browser session. Run `39232339581` exists, ran on `main` at SHA `2791a1c578f19174ea609b53ad84ed656fc9faf9`, and completed successfully. `java17`, `frontend`, `browser`, and `security` all succeeded; optional `owasp` was skipped under its condition. Backend reported 30 tests with no failures/errors/skips; frontend reported 24 tests across 6 files; the synthetic Playwright job succeeded. Gitleaks reported no leaks. Dependency Review did not run because this was a push event and that step is PR-only. Artifact digests and job evidence are captured in [M1 hosted CI evidence](M1_4_HOSTED_CI_EVIDENCE.md). This resolves the run conflict, but the run SHA is not the current local HEAD and CI does not substitute for the live Google account flow.

This item is closed as hosted CI evidence. The independent local GitHub CLI credential mismatch remains an environment issue only; it does not invalidate the run page evidence.

## Live Google sign-in and invitation acceptance

**Status: BLOCKED — manual account sign-in and recipient identity confirmation remain.** The owner supplied separate clinic OWNER and invitation recipient identities. A disposable database, `dental_m144_oauth`, has been created locally; migrations V1–V14 completed and the app starts on Java 17 with a non-superuser/non-BYPASSRLS app role. Local-only OAuth settings are ignored by Git. No identity token or password was logged or saved.

The real Google sign-in UI was exercised. The chooser initially offered an account not on the Platform Admin allowlist; its live sign-in was correctly denied. I then entered the owner email supplied by the user and Google reached the password challenge. I stopped there for the owner to complete password/MFA in Chrome. The invitee address supplied by the user differs from the currently available Google identity by a spelling variation; the exact recipient identity must be confirmed before invitation issuance or acceptance. No invitation was issued, so staff access has not been verified.

The existing M1.4.2 browser suite used synthetic credentials and intercepted network calls; it is not evidence of real Google authentication or real invitation acceptance. The live test remains required for M1 closure.

## Test/evidence totals

- M1.4.4 source/preflight change: **1 preflight command passed**, shell syntax passed, whitespace/diff check passed.
- Product test suites were not rerun during this documentation/preflight reconciliation.
- Prior M1.4.2 reports record **30 backend + 24 frontend + 3 Playwright = 57 passed, 0 failed, 0 skipped**. These are historical recorded results, not M1.4.4 executions.
- Real Google flow: **attempted against the live Google Identity UI; unallowlisted account correctly denied; owner login pending user password/MFA handoff**.
- Invitation acceptance/staff access: **not run; invitee address spelling does not match the available Google account**.
- Hosted CI run `39232339581`: **verified successful** (backend 30, frontend 24, browser pass, security pass; OWASP skipped by deferral).
- Maven NVD scan: **deferred exception**, not a passed or failed scan.

## Acceptance decision and next steps

Keep M1 OPEN and M2 blocked until live Google login plus staff invitation acceptance are verified with authorized test accounts in an isolated clinic. Hosted run `39232339581` is now independently confirmed with preserved metadata and job evidence. NVD deferral is accepted for development only; complete the Maven vulnerability assessment and finding disposition before production deployment. Do not describe the application as production-ready.
