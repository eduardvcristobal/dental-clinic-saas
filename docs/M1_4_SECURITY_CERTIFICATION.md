# M1.4 Security Certification

**Status: Partial verification only; security certification not granted.**

## Verified

- Fresh V1–V14 migration succeeds; the separate V13→V14 upgrade succeeds without losing synthetic tenant data.
- `dental_app` is a restricted login role without SUPERUSER or BYPASSRLS.
- V14 removes all `platform_admin_access` policies. With the platform-admin GUC set, the restricted role sees zero patient and app_user rows.
- Migration/RLS integration regression covers clinic A/B patient isolation, cross-clinic writes and relationship denial, tenant context cleanup, invitation tenant FKs and single-use conditional claim/replay behavior.
- Auth integration regression covers signed Google test tokens and invalid token cases, clinic membership, JWT validation, refresh rotation/replay/concurrency, logout, cookie and Origin behavior.
- Unauthenticated Platform Admin API access returns 401. Registration endpoint remains closed by default and returns 403.

## Not certified

No real Google authentication or fully authenticated browser E2E was completed. Invitation HTTP concurrency, complete staff/dentist workflows, responsive multi-route review and hosted CI were not run. V11/V14 policy behavior was reviewed and verified in the disposable DB; a full authenticated Platform Admin dashboard/API review is still outstanding. No dependency vulnerability scan was run.

## Decision

Do not enable public clinic registration for deployment, do not activate real patient data, and do not mark M1 complete. Complete the listed checks in `M1_4_VALIDATION_REPORT.md` before M2.
