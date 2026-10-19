# M1.4.6 — New Clinic Registration E2E

**Date:** 2026-10-19  
**Environment:** local PostgreSQL 18.6 disposable database `dental_m146_e2e`; Java 17.0.20; local frontend origin `http://localhost:5173`.

## Configuration

- `backend/src/main/resources/application.yml` sets `app.onboarding.self-service-enabled` to `${APP_ONBOARDING_SELF_SERVICE:false}`.
- The private local `backend/.env.local` setting was verified disabled before the test and left unchanged.
- Only the temporary backend process used for E2E had `APP_ONBOARDING_SELF_SERVICE=true`, pointed at `dental_m146_e2e`. No production or hosted configuration was changed.
- After the E2E test, the enabled process was stopped and the backend restarted with the local default. A `POST /api/onboarding/clinics` probe with an intentionally invalid token returned HTTP 403, `Clinic registration is not open`. Registration was disabled at the end of the test.

## Live Google verified registration and Owner login

Two authorized Google test identities registered two independent test clinics through `/register-clinic`:

| Clinic code | Clinic | Initial branch | Timezone |
|---|---|---|---|
| `m146-owner-20261019` | M1.4.6 Owner Test Clinic | Main Test Branch | Asia/Manila |
| `m146-second-20261019` | M1.4.6 Second Test Clinic | Second Main Branch | Asia/Manila |

Both registration success screens displayed the clinic-created result. The first Owner signed in through Google to the first clinic and opened Clinic Settings; the second authorized account signed in through Google to the second clinic and saw that clinic’s Settings. The test identity addresses and OAuth materials are omitted from this report.

Read-only database verification returned:

```text
m146-owner-20261019 | M1.4.6 Owner Test Clinic | Asia/Manila | branch=Main Test Branch | owners=1 | owner_branch_assignments=1 | created_events=1
m146-second-20261019 | M1.4.6 Second Test Clinic | Asia/Manila | branch=Second Main Branch | owners=1 | owner_branch_assignments=1 | created_events=1
```

## Two-clinic resource isolation

The second Owner’s browser Settings showed `M1.4.6 Second Test Clinic`. A read-only PostgreSQL check executed as the restricted `dental_app` role with `app.clinic_id` set to the second clinic returned:

```text
second clinic branch visible=1 | first clinic branch visible=0
```

The restricted role is non-superuser and has no `BYPASSRLS`; branch policies filtered the first clinic’s branch. Automated cross-tenant API/RLS regression also passed in the final Java suite.

## Safe failure cases

The final Java/PostgreSQL suite verifies:

- Invalid clinic slug: HTTP 400 with safe validation detail; no clinic row created.
- Invalid timezone: HTTP 400; transaction creates no clinic, branch, or Owner.
- Duplicate clinic code: HTTP 409, `That clinic code is already in use.`; unique DB constraint remains authoritative for concurrent requests.
- Invalid Google identity: HTTP 401 before any clinic is written.
- Unapproved Origin: HTTP 403 before Google verification or database access.
- Default-disabled self-registration: HTTP 403, verified again after the temporary E2E backend was restarted with registration disabled.

## Regression result

OpenJDK 17.0.20 Maven `clean verify`: 33 passed, 0 failures, 0 errors, 0 skipped. It used fresh auth and migration DBs and included V1–V14 migrations, RLS, duplicate/invalid registration, invitation lifecycle, and concurrent acceptance. No patient records were activated; the E2E database holds only clinic/onboarding test fixtures.
