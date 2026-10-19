# M1.4.1 Security Regression Results

**Status: application regression tests, browser suite and npm audit pass; OWASP NVD scan and live-provider gates remain open.**

## Executed application security checks

- HTTP invitation lifecycle: first clinic owner provisioning, valid acceptance, replay denial, revoked/expired token denial, one-staff-account outcome under simultaneous claims, dentist mapping and audit event assertion.
- Authenticated Platform Admin: allowlisted principal sees directory endpoint; clinical records API returns forbidden; non-allowlisted principal is denied; modified JWT is rejected.
- Existing V14/RLS checks: fresh V1–V14 run and V13→V14 upgrade succeeded on isolated PostgreSQL 18.6; restricted-role tenant and Platform Admin denial tests passed. This session enabled the previously skipped `MigrationAndTenantIsolationTest` by supplying a disposable database, so the backend total is now 30/30 with nothing skipped.
- Frontend GSI identity wrapper: tests verify one initialization across mount/remount and callback forwarding. Lint clean.
- Browser security regressions (new this session, all passing): registration disabled by default at every viewport; invitation token stripped from the URL; Platform Admin screen exposes no patient/clinical data; no mutating HTTP request is issued by any preview route; Google Identity initializes exactly once across login screens.
- Sign-out hardening: the local session is now cleared even when the Google script call fails (`AuthProvider.signOut` no longer lets a GSI error skip `clear()`).
- Clinic-code spoofing via loopback fixed: IP-literal hosts can no longer be interpreted as a tenant slug (`frontend/src/lib/tenant.ts`), with unit coverage.

## Dependency scanning

- npm audit (this session, registry reachable): **0 vulnerabilities** — full dependency tree, including dev dependencies; metadata reports info/low/moderate/high/critical all zero. Re-run after the Playwright dependency was added.
- OWASP Dependency-Check Maven 12.1.0: NVD catalog download (409,901 records) again did not complete within the session window; anonymous NVD API access is throttled without an API key. No Maven vulnerability count is claimed. The GitHub Actions workflow now carries an opt-in `owasp` job that runs automatically once an `NVD_API_KEY` secret and a `RUN_OWASP_SCAN=true` repository variable are configured.
- GitHub Actions dependency review and secret scan: workflow configuration extended (browser evidence upload, opt-in OWASP). Still not executed remotely — no Git remote is configured.

## Residual security gates

1. Complete the OWASP NVD scan (needs an NVD API key or a longer window) and remediate any high/critical findings.
2. Complete a real allowlisted Google login and confirm authorized origin/client configuration.
3. Execute hosted CI after the owner enables/configures the remote.

No secrets were copied into the repository. Google credentials were not used by the synthetic tests.
