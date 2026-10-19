# M2.5 — Security Findings

## Findings and controls

1. **Concurrent demographic edits — corrected.** Update accepts an expected version and applies a conditional database update. Missing version returns 400, stale version returns 409, and concurrent same-version requests produce one winner and one conflict.
2. **Clinic object access — passed in tested paths.** Backend predicates include the principal clinic; two-clinic HTTP tests return 404 for foreign patient IDs. PostgreSQL RLS and composite same-clinic FKs also fail closed for patient, history, guardian, consent, branch, staff, and audit relationships exercised by the suite.
3. **Clinical role access — passed.** OWNER/DENTIST can exercise allowed history operations; FRONT_DESK receives 403 and the UI displays no clinical history form for that role.
4. **Archived chart access — passed.** Archived patient detail/care access is denied and archive requires clinic management.
5. **Consent and guardian semantics — constrained and documented.** Administrative acknowledgement is distinct from a signature. Guardian contact data is self-reported and grants no implied legal authority. Legacy signed fields survive the V15 upgrade but need semantic review.
6. **Token-bearing DTO logging — corrected.** Google identity, access, refresh, invitation, patient-demographics, guardian, and medical-history DTO string forms redact sensitive values. Spring MVC web logging is held at INFO so ambient DEBUG does not enable request/response body or query-parameter diagnostics. Regression tests verify logger levels and DTO redaction; final live logs contained no JWT strings.
7. **Duplicate races — residual medium risk.** Matching is advisory and does not serialize/block patient creation; parallel duplicates can create separate charts. Automatic merge remains prohibited. Provide staff review and define identity resolution before relying on duplicate prevention.
8. **Hosted CI — blocked.** Current hosted workflow run, commit SHA, and job conclusions could not be retrieved because GitHub CLI credentials are invalid and API access failed.
9. **NVD dependency assessment — deferred, not passed.** **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** to M10. Complete Maven vulnerability assessment before production deployment. Optional OWASP job remains enabled in workflow configuration.

## Residual release risks

- Hosted CI must pass for the final commit before M2 can be marked complete for development.
- Legacy consent interpretation must be reviewed before production migration.
- Duplicate detection is only advisory; no auto-merge exists.
- ESLint/Oxlint has two asynchronous set-state warnings.
- No penetration test, privacy review, production restore drill, or real patient workflow approval was performed.

**No production-readiness claim is made.** Real patient data and production clinical workflows remain prohibited pending appropriate review.
