# M10.2–M10.5 — Authentication, tenancy, privacy and API protection

## Implemented controls and test evidence
- M10.2: Existing stateless Bearer JWT principal validation, signature/issuer and role checks; tenant context sourced exclusively from authenticated principal. No new login mechanism or live OAuth production certification is claimed.
- M10.3: PostgreSQL integration assertion inspects **every** current public `clinic_id` table for FORCE RLS; rejects any tenant table with missing RLS. Confirms application runtime role is neither superuser nor BYPASSRLS.
- M10.4: Patient-data handling and release checklist below; infrastructure and policy enforcement requiring deployment configuration remain **open**, not certified.
- M10.5: Configurable `APP_PATIENT_ACTION_RPM` additional per-remote-address protection for public token redemption; isolated from normal auth quotas, with 429, Retry-After and Cache-Control headers. Retains existing tenant-scoped PostgreSQL budget; **a shared edge rate limiter is still required**, because this new IP bucket is process-local.
- M10.5: JUnit HTTP filter assertions for blocked/unblocked POST requests, 429 headers and window reset; invalid limits rejected.

## Privacy and data-protection inventory
| Data | Primary controls | Unverified production requirements |
| --- | --- | --- |
| Patient identifiers, medical histories and clinical notes | JWT roles and tenant-scoped APIs; FORCE RLS, same-clinic FKs | Formal least-privilege access review, data subject rights, consent and retention policy |
| Financial invoices, payments and refunds | Clinic RLS; append-only payment/refund evidence; role-restricted billing | Fiscal signoff, legal retention and encrypted archival |
| Provider notification destinations and responses | Generic outbound text, hashed patient action tokens; feature-gated transport | Provider DPIA/privacy contract, data residency, consent/withdrawal validation |
| Application access and refresh credentials | Signed access tokens and refresh rotation; no secrets in source | Production secret rotation, revocation drills, audit retention |
| Uploaded clinical files | Existing file access boundaries | Malware scanning, tenant-scoped object storage, expiration/deletion policy |
| Browser/HTTP responses and telemetry | Framework-level security headers; minimum log-level configuration | CDN/edge no-store enforcement, PHI log redaction sampling and penetration review |

## Operational checklist (must be signed before pilot)
- [ ] Use TLS termination and secure cookie settings in the actual deployed environment.
- [ ] Confirm PostgreSQL runtime roles cannot read another clinic's rows, even via direct SQL, for every deployed schema version.
- [ ] Configure shared edge/WAF rate limits by **verified** client address, not untrusted `X-Forwarded-For` from the public Internet.
- [ ] Define backups encryption, recoverability, audit retention and patient deletion/export legal boundaries.
- [ ] Capture authorized access, retention and incident review signoff. No real patient details in test data, logs or screenshots.
- [ ] Verify browser cache policy and upload/file object authorization under production TLS.
- [ ] Run hosted Java/PostgreSQL security and browser CI for final phase head.

**Release verdict: NOT production certified.** M8 outbound worker, provider messages and patient-action features remain disabled pending independent explicit acceptance.
