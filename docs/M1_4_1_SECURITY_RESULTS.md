# M1.4.1 Security Regression Results

**Status: application regression tests, browser suite and npm audit are recorded as passing for M1.4.2; live-provider verification remains open. Maven NVD scan is DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION, not PASS.**

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
- OWASP Dependency-Check Maven 12.1.0: NVD update did not complete; no Maven vulnerability count is claimed. Per owner direction in M1.4.4, the assessment is **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** to M10. This is not a PASS. Residual risk: applicable vulnerabilities in Maven dependencies have not been assessed. Before production deployment, complete the Maven assessment with current advisory data and document/remediate applicable high/critical findings.
- GitHub Actions dependency review and secret scan: the workflow retains its security job and pull-request dependency review. A later commit (`07cc18f`) documents run 39232339581 as green, but its primary run metadata and logs are unavailable in this checkout; the assertion is **not independently verified**. No hosted secret-scan or dependency-review result is claimed by this report.

## Residual security gates

1. Complete a real allowlisted Google login and invitation acceptance with authorized test accounts in an isolated clinic.
2. Verify run 39232339581 from authenticated primary evidence or execute hosted CI again and retain metadata/logs for the tested SHA and every job.
3. Complete the deferred Maven vulnerability assessment in M10 and before production deployment; document findings and remediation.

No secrets were copied into the repository. Google credentials were not used by the synthetic tests.
