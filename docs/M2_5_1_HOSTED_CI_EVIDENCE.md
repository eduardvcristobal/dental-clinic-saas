# M2.5.1 — Hosted GitHub Actions Evidence

Date checked: 2026-10-20 · Repository: `eduardvcristobal/dental-clinic-saas` · Branch: `main`

## Runtime identity

- GitHub CLI authenticated as `eduardvcristobal` (keyring credential, active). The M2.5-era "invalid token" state had recovered; no credentials were requested or pasted in chat.
- Initial HTTPS push was rejected because the OAuth token lacked the `workflow` scope needed for a push containing a workflow-file change. Resolved by pushing over the configured SSH key (`git@github.com:...`), which workflow rules allow; no token was modified or shared.
- The `owasp` job is an opt-in job (`vars.RUN_OWASP_SCAN == 'true'` and an `NVD_API_KEY` secret); it was not configured for this run and therefore reports `skipped`, not failed.

## Run 39348777480 — "Backend Java 17"

- URL: https://github.com/eduardvcristobal/dental-clinic-saas/actions/runs/39348777480
- Tested commit SHA: **a5894c2fe765d3d9b18df3f1c3d3d9894aba364e** (`fix(m2.5.1): patch Tomcat/Jackson/log4j within Boot BOM and resolve set-state-in-effect warnings`) — highest application-code commit on `main` at trigger time (push incident: 2026-10-20T00:37:52Z).
- Overall conclusion: **success** (status: completed).

| Job | Conclusion | Job ID | Notes |
|---|---|---|---|
| `java17` (backend) | **success** | 118387720854 | Ubuntu runner; postgres:16 service; JDK 17 Temurin; `mvn -B -ntp verify` incl. V1–V15 migrations/RLS and auth tests (1 m 22 s) |
| `frontend` | **success** | 118387721062 | tsc, oxlint, Vitest, Vite build (18 s) |
| `browser` | **success** | 118387721168 | Playwright Chromium 4/4, evidence artifacts uploaded (1 m 46 s) |
| `security` | **success** | 118387721229 | Gitleaks + dependency-review (7 s) |
| `owasp` | skipped | 118387722177 | Opt-in job, not configured for this repo run |

Verification method: `gh run view 39348777480 --json …` with per-job `conclusion` values read directly from the GitHub API — claimed conclusions above are copied verbatim from that response.

## Run 39349377386 — final commit re-check

The documentation-and-evidence commit `2a2728e` (later push) triggered the same workflow, and its run completed with the **same conclusions**: java17/frontend/browser/security success, owasp skipped. So the hosted result above is confirmed against the repository's final state on `main`, not only the application-code commit.

Dependency-scan disclaimer: the hosted run did not perform the NVD dependency scan (opt-in job skipped). The Maven-side scan evidence lives in the local M2.5.1 triage report; both leave the same residual upstream-unfixed set to re-check at M10.
