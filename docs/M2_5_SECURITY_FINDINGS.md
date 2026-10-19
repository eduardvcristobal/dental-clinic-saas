# M2.5 — Security Findings

## Findings and controls

1. **Concurrent demographic edits — corrected.** Update accepts an expected version and applies a conditional database update. Missing version returns 400, stale version returns 409, and concurrent same-version requests produce one winner and one conflict.
2. **Clinic object access — passed in tested paths.** Backend predicates include the principal clinic; two-clinic HTTP tests return 404 for foreign patient IDs. PostgreSQL RLS and composite same-clinic FKs also fail closed for patient, history, guardian, consent, branch, staff, and audit relationships exercised by the suite.
3. **Clinical role access — passed.** OWNER/DENTIST can exercise allowed history operations; FRONT_DESK receives 403 and the UI displays no clinical history form for that role.
4. **Archived chart access — passed.** Archived patient detail/care access is denied and archive requires clinic management.
5. **Consent and guardian semantics — constrained and documented.** Administrative acknowledgement is distinct from a signature. Guardian contact data is self-reported and grants no implied legal authority. Legacy signed fields survive the V15 upgrade but need semantic review.
6. **Token-bearing DTO logging — corrected.** Google identity, access, refresh, invitation, patient-demographics, guardian, and medical-history DTO string forms redact sensitive values. Spring MVC web logging is held at INFO so ambient DEBUG does not enable request/response body or query-parameter diagnostics. Regression tests verify logger levels and DTO redaction; final live logs contained no JWT strings.
7. **Duplicate races — residual medium risk.** Matching is advisory and does not serialize/block patient creation; parallel duplicates can create separate charts. Automatic merge remains prohibited. Provide staff review and define identity resolution before relying on duplicate prevention.
8. **Hosted CI — blocked.** Current hosted workflow run, commit SHA, and job conclusions could not be retrieved because GitHub CLI credentials are invalid and API access failed. Local CI-equivalent checks passed; they do not replace hosted evidence.
9. **NVD dependency assessment — failed threshold.** Dependency-Check 12.1.6 completed from the OWASP-maintained NVD 2.0 feed mirror: 84 dependencies scanned (54 unique), 13 matched as vulnerable, 179 dependency/CVE matches, 114 unique CVEs (25 critical, 44 high, 39 medium, 6 low by highest severity). With `failBuildOnCVSS=7`, the scanner exited nonzero. No suppressions or manual applicability review were performed. Treat all critical/high matches as unresolved until triaged and remediated. The local NVD key was present and accepted by a minimal API request (HTTP 200); the full direct NVD update stalled at 78% on 12.1.0 and 97% on 12.1.1. Version 12.1.6 and timeout/retry settings are now used by the optional workflow. Sonatype OSS Index was skipped because no credentials were configured.

## Residual release risks

- Hosted CI must pass for the final commit, and critical/high dependency matches must be triaged and remediated before M2 can be marked complete for development.
- Legacy consent interpretation must be reviewed before production migration.
- Duplicate detection is only advisory; no auto-merge exists.
- ESLint/Oxlint has two asynchronous set-state warnings.
- No penetration test, privacy review, production restore drill, or real patient workflow approval was performed.

**No production-readiness claim is made.** Real patient data and production clinical workflows remain prohibited pending appropriate review. Complete and review the Maven vulnerability assessment before production deployment; the OWASP mirror is best-effort and the OSS Index source was unavailable in this run.
