# M1.2 — Secure onboarding, invitations and dentist profiles

Status: **implementation delivered, NOT runtime-verified or production-approved** (2026-10-19).

## Added

- `POST /api/onboarding/clinics`: verifies the first owner's Google ID token, creates the clinic and first branch, provisions a single `OWNER` and branch membership in one transaction. **Disabled by default** via `APP_ONBOARDING_SELF_SERVICE=false`. Enable only with deployment-level abuse defenses, clinic-slug policy and Google client configuration.
- `POST /api/onboarding/invitations`: `OWNER`/`ADMIN` creates 7-day single-use invitations for `ADMIN`, `DENTIST`, `FRONT_DESK`, tied to one branch; returns a one-time bearer token. The token is SHA-256 hashed in the database and must be transmitted privately. There is **no email sender integration**.
- `GET /api/onboarding/invitations` and `POST /api/onboarding/invitations/{id}/revoke`: tenant-scoped invitation list and revocation.
- `POST /api/onboarding/accept`: Google-verified recipient email must exactly match the invitation. Locks invitation row, checks expiry/revocation, creates staff and branch membership and marks invitation accepted atomically. Does not log in automatically; staff uses existing clinic-slug Google sign-in.
- `GET /api/onboarding/dentists`, `POST /api/onboarding/dentists`: tenant-restricted list and dentist profiles for active users with the `DENTIST` role; validates `user_id` in caller's clinic.
- `V13__clinic_onboarding_and_invitations.sql`: invitation and event tables with tenant foreign keys and forced RLS, and unique dentist-to-user mapping.
- Staff admin React workspace: create/revoke invitation, one-time copy, create dentist profile.
- Unit-test source for disabled self-registration, role boundaries, forbidden role escalation, token shape and cross-origin rejection.

## Security limitations, future tasks

1. **Do not enable public self-registration until anti-abuse, tenant quotas, email deliverability and verification workflows are reviewed.** The backend accepts Google identity but does not establish authorization to represent a clinic business.
2. No browser-facing first-owner signup or invitation acceptance form; API paths work only after external Google ID token provision. Production UX remains pending.
3. Invitations are manually distributed, not emailed. Share only via a trusted private channel.
4. Missing: end-to-end tests with two clinics, real restricted-role PostgreSQL V1–V13 Flyway migration, concurrency/replay and actual Google provider sign-in, completed browser E2E, and robust audit/event retention policy.
5. The prior M0.4 Platform Admin cross-clinic access vulnerability remains unresolved; no real patient data should be onboarded.
6. Platform Admin roles cannot be invited and are separate from clinic staff. Clinic creation is distinct from Clinic Management APIs and remains disabled by default.

## Environment tests

- JDK 17: not available in this environment (only JDK 21).
- Maven: not available; Java build/test **NOT EXECUTED**.
- PostgreSQL: not available; V13 migration/RLS **NOT EXECUTED**.
- Frontend dependency cache unavailable; full TypeScript/Vite/Vitest **NOT EXECUTED**.
- File/artifact integrity and source-level checks: performed locally, see delivery.

**Next:** M1.3 — Runtime integration, invitation replay tests, RLS & Google browser verification, first-owner/acceptance forms, production rollout checks.
