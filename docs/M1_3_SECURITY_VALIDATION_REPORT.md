# M1.3 — Onboarding integration and security validation report

Date: 2026-10-19

## Delivered

- Dedicated Google-verification browser forms for first-owner registration and staff invitation acceptance. Both are public routes by necessity, but backend registration remains **disabled by default**.
- Invitation link displays a one-time bearer token. Browser acceptance clears token from the address bar after success. Treat the link as a secret; avoid third-party analytics on the acceptance page.
- V14 forward migration removes the V11 blanket `platform_admin_access` RLS policy. Platform administrators now see only clinic-directory metadata; clinical/patient/user/finance cross-tenant access is not implicitly granted.
- Extended restricted-role migration test to expect V1–V14 and assert that global context cannot read patients or users.
- Added explicit negative unit tests for unauthorized management and origins.

## Validation and limitations

| Gate | Status | Evidence / action |
|---|---|---|
| Source code updated | Implemented | M1.3 changes and Git commit |
| PostgreSQL fresh V1–V14 | **NOT RUN** | Requires PostgreSQL server, owner/runtime roles, test credentials |
| JDK 17 compilation and unit tests | **NOT RUN** | JDK 17 and Maven unavailable in execution environment |
| Full Google OAuth sign-in | **NOT RUN** | Requires configured Google OAuth client and authorized origin |
| End-to-end browser workflows | **NOT RUN** | Requires installed npm packages, browser and backend |
| Two-clinic RLS in DB | **NOT RUN** | Added assertions to PG-dependent tests; not executed |
| Invitation replay / concurrency | **NOT RUN** | Needs two simultaneous HTTP requests against PG; not claimed complete |
| Platform Admin clinical isolation | **NOT RUN** | V14 policy removal authored and assertions added, runtime proof outstanding |
| Hosted CI | **NOT RUN** | Requires pushing branch and GitHub Actions execution |

## Important security notes

1. Restrict `app.onboarding.self-service-enabled` to `false` until abuse prevention, verification, and provisioning quotas are in place. Creation of unlimited clinics from a public endpoint would invite abuse.
2. Invitation links are credentials: do not log them, record them in analytics, or include them in referrer headers. Consider an exchange flow that removes URL tokens immediately, not only on successful acceptance.
3. V14 revokes the entire V11 platform-admin RLS grant. Do not restore it to fix a reporting screen: use carefully scoped aggregated reporting and authorized functions instead.
4. Existing business API authorization needs independent testing. A frontend role restriction does not protect database operations.
5. Production approval is **BLOCKED** until all of the above security and runtime checks actually pass.

## Operator verification commands

On a machine with JDK 17, Maven, PostgreSQL and access to dependencies:

```bash
cd backend
mvn -B -ntp clean verify
cd ../frontend
npm ci
npm test
npm run build
npm run lint
```

Provide isolated test database credentials through `M04_MIGRATION_TEST_DB_URL`, `M04_MIGRATION_TEST_OWNER_USER`, `M04_MIGRATION_TEST_OWNER_PASSWORD`, and `M04_MIGRATION_TEST_APP_PASSWORD` as described in backend test source. Never run destructive test setup against a live database.
