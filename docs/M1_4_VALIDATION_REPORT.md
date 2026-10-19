# M1.4 Validation Report

Date: 2026-10-19

## Outcome

**M1.4 is not certified and M1 remains open.** Backend and frontend automated checks passed on Java 17 and a fresh, isolated PostgreSQL cluster. Fresh V1–V14 migration and a V13→V14 upgrade passed. The configured GitHub repository has no remote; live Google login and complete browser onboarding (including invite acceptance concurrency) were not verified. M2 must not start under the requested gate.

## Environment and isolation

- macOS 15.6.1, OpenJDK 17.0.20, Maven 3.9.16
- Node.js 24.18.0, npm 11.16.0, Git present
- Docker CLI 29.7.2 present; Docker daemon was not used
- PostgreSQL 18.6 (temporary cluster in `/private/tmp`, TCP port 55432)
- Test databases: `dental_m14_migration`, `dental_m14_auth`, `dental_m14_upgrade`
- The existing local PostgreSQL cluster and `dental` database were not modified.

The first in-sandbox Maven invocation could not bind local HTTP sockets or connect to loopback PostgreSQL; it produced infrastructure errors. The verification was rerun with local networking enabled. The final rerun passed. No tests were disabled or skipped.

## Commands executed

```sh
java -version
mvn -version
node -v
npm -v
git status --short
./scripts/m1_4_verify.sh
```

`m1_4_verify.sh` was run with Java 17 and explicit credentials/URLs for fresh disposable databases. It executes `mvn -B -ntp clean verify`, `npm ci --no-audit --no-fund`, `npm test`, `npm run build`, and `npm run lint`.

The separate upgrade was run by starting the application with `SPRING_FLYWAY_TARGET=13` against `dental_m14_upgrade`, inserting synthetic-only clinic/user/patient rows, then restarting with the normal Flyway target. The second startup logged “Current version ... 13” and applied only migration 14.

## Results

| Gate | Result | Evidence |
|---|---|---|
| Java 17 / Maven build and package | PASS | `mvn clean verify`, packaged Spring Boot JAR |
| Backend JUnit/integration | PASS | 26 tests, 0 failures, 0 errors, 0 skipped |
| Fresh PostgreSQL V1–V14 | PASS | Fresh migration integration test, 14 successful migrations |
| V13→V14 existing schema upgrade | PASS | V14 alone applied to V13 database; synthetic row preserved |
| Migration checksums/history | PASS | 14 history entries; successful V14 checksum recorded in DB output |
| Restricted-role RLS | PASS | Role `dental_app` is NOSUPERUSER/NOBYPASSRLS; V14 leaves zero `platform_admin_access` policies; 30 forced-RLS tables observed in upgrade DB |
| Platform Admin clinical-data denial | PASS | As restricted role with `app.platform_admin=true`, patient count 0 and app_user count 0; preserved synthetic patient count 1 as DB owner |
| Frontend locked dependency install | PASS | `npm ci --no-audit --no-fund` |
| Frontend tests | PASS | 15 tests across 4 files, 0 failures, 0 skipped |
| TypeScript/Vite production build | PASS | `npm run build` |
| Frontend lint | PASS WITH WARNINGS | Existing `StaffOnboarding.tsx` warnings: impure `Date` call in render and synchronous state update in effect |
| Browser registration page | PASS, limited smoke | Chrome rendered `/register-clinic`; visible disabled-by-default notice; no account was created |
| Self-registration disabled behavior | PASS | POST to isolated app returned HTTP 403 |
| Platform Admin API authentication boundary | PASS, negative case | Unauthenticated `/api/platform-admin/clinics` returned HTTP 401 |
| GitHub Actions definition review | PASS, static review | Java 17/PostgreSQL, frontend, Gitleaks and dependency-review jobs exist |
| Hosted workflow | BLOCKED | `git remote -v` returned no remotes; no hosted run was triggered |
| Real Google authentication | BLOCKED | No real Google sign-in completed; browser profile/account and OAuth origin setup were not validated |
| Complete browser onboarding | BLOCKED | Registration remains disabled; user did not authorize enabling it for local test. Real Google invitation acceptance, wrong-email, expiry/revoke, dentist and session flows were not browser-tested |
| Invite HTTP concurrency | BLOCKED | DB-level atomic claim/replay regression ran; concurrent acceptance through HTTP was not executed |
| Responsive route-by-route review | BLOCKED | Only registration page rendered; no complete desktop/tablet/mobile route review was captured |
| Screenshots saved as deliverables | BLOCKED | Browser screenshot was inspected in-session, but the OS capture API could not save a workspace image |

### Final test totals

- Backend: **26 passed, 0 failed, 0 skipped, 0 blocked**
- Frontend: **15 passed, 0 failed, 0 skipped, 0 blocked**
- Overall automated suite: **41 passed, 0 failed, 0 skipped, 0 blocked**
- The initial sandbox-restricted attempt failed infrastructure setup; the successful rerun is counted above. Lint warnings are not test failures.

## Security findings and code changes

No source changes were needed during this validation. The tested source already contains V14 policy removal, invitation token URL cleanup, `no-referrer`, clinic-scoped RLS and fail-closed self-registration. Automated tests validated login token matrices, JWT/session behavior, refresh rotation/replay/concurrency, logout, tenant RLS, tenant foreign keys, one-time invitation state transitions, and management controller authorization.

The validation did not establish a complete authenticated browser onboarding or a live Google OAuth flow. No real personal information or production data was used. Synthetic rows were inserted only into the disposable upgrade database.

## Risks and recommendation

1. Complete Google browser sign-in with the authorized development origin and account.
2. Run local browser E2E against a disposable DB with an explicitly approved test-only registration override, covering invite correct/wrong email, expiry, revocation, parallel acceptance, dentist assignment and logout/session restoration.
3. Verify responsive layouts and save screenshot artifacts for key routes.
4. Configure a Git remote/authorized branch when ready, then run the hosted workflow. CI was intentionally not pushed or triggered.
5. Review the two frontend lint warnings and resolve or document them before the next release gate.
6. Review third-party dependency advisories with an available registry/security scan; no online vulnerability scan was run.

**Decision: M1.4 partially validated; not certified. Do not proceed to M2 or use real patient data.**
