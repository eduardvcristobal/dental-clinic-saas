# M8 Reminders & Patient Recall — Development Handoff

Date: 2026-10-20
Branch: `feature/m8-notifications-recall` from accepted M7 main.
Status: **Partially implemented / provider integration blocked by configuration and regulatory prerequisites.** Never claim production delivery.

## Milestones
| Milestone | Implemented | Gate / limitations |
|---|---|---|
| M8.1 Notification foundation | V35 tenant RLS, policy, channel consent, delivery and response tables | Migrations and role tests required |
| M8.2 Appointment reminder scheduling | Staff-only explicit queue request for existing active appointment, lead-time, clinic policy, consent | Does not yet subscribe automatically to appointment changes; must revalidate/reschedule and suppress pending messages on edits |
| M8.3 Email delivery infrastructure | Provider-neutral transport contract; implementation fails closed without provider | **No actual email sending, worker, retry, tracking or provider integration** |
| M8.4 SMS abstraction | SMS channel enum, opt-in queue, documented international/phone/provider callback gate | **E.164 validation, SMS gateway and delivery callbacks not implemented** |
| M8.5 Patient recalls | Existing recall records, creation, listing, opt-in recall queue | No auto-generation from completed treatment or clinical recall schedules |
| M8.6 Patient response handling | Staff-recorded CONFIRM, CANCEL_REQUEST or RESCHEDULE_REQUEST in tenant-scoped response log; no direct schedule changes | **No authenticated public patient response flow** |
| M8.7 Clinic notification center | Connected nav and React screen with queue, policy, consent, manual recall/reminder operations | Provider sending intentionally disabled |
| M8.8 Privacy and regression | Tenant RLS, consent withdrawal suppression for pending/failed work, role checks, fail-closed transport; synthetic real PG/Playwright regression | No tokenized patient opt-out/self-service or actual message rate-limit/quiet-hour worker |
| M8.9 Acceptance | CI reports and this review | M8 is development scaffold, NOT end-to-end notification delivery acceptance |

## Critical stop gates before external delivery
- No patient contact attempt is performed: notification scheduling writes a PENDING row only. No deliverable notification may be treated as SENT or DELIVERED.
- Never enable email/SMS transport from UI flags alone; implement an approved provider adapter, key vault, signed callback validation, retry/lease semantics, idempotency, rate limits, queue worker, per-recipient suppression, audit trail and generic PHI-safe templates.
- Appointment reminder jobs must be revalidated against latest appointment start/status, channel preference and consent just before sending; cancel stale jobs.
- Staff-entered appointment response is not authenticated patient intent; use signed, expiring single-use reply tokens and a secure booking action approval workflow.
- Enforce E.164 numbers and jurisdictional marketing/transactional messaging consent; avoid including treatments, diagnoses, or sensitive details.
- Verify suppression races, quiet hours and time zones before any external sending.
- Keep external communication off in every environment until explicit deployment/configuration review.

## Test evidence
See GitHub Actions on branch. Full hosted regression and real-backend M8 test must pass before draft PR acceptance.
