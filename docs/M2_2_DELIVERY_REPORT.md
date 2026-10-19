# M2.2 — Medical history, guardian contacts, consent and identity integrity

## Delivered
- V15 forward migration: patient_guardian_contact with tenant FK/RLS, unique primary contact; one medical history per patient; consent status and administrative acknowledgement fields.
- PatientCareController: GET/PUT medical history, GET/POST/DELETE guardian contacts, GET/POST consents, POST acknowledge, GET identity-review.
- Patient Registry's selected-patient details load tenant-scoped clinical history for authorized clinical roles, guardians, unsigned consent tracking and possible matching charts.
- Clinical edits audited; records bound to clinic identity from authenticated principal, not client ID.

## Explicit limitations
- Consent acknowledgement means a staff member marked a record acknowledged. It **is not** patient consent, a signature or permission to treat. Signature collection and consent-specific clinical controls must be implemented separately.
- Guardian contacts are unverified self-reported relationships; no legal guardian authority is established by data entry.
- The exact-name/DOB match list is advisory and does not merge patient charts.
- Existing M2.1 Patient 360° demonstration remains visibly fictional; real M2.2 fields appear only in the selected patient record.
- All sample test data must be synthetic.

## M2.5 regression evidence (2026-10-19)
- Java 17.0.20 full Maven suite: 53 passed, 0 failed, 0 skipped, including patient-care authorization, audit, consent transition, and token-redaction regressions.
- PostgreSQL 18.6 fresh V1–V15 and V14→V15 upgrade tests passed with a restricted application role; medical history, guardians, consent, same-clinic foreign keys, and RLS were exercised.
- Live authenticated Playwright against Spring Boot and isolated PostgreSQL passed guardian contact entry/removal, clinical history save, administrative consent acknowledgement, and cross-role/cross-clinic denials.
- A legacy V14 consent row retained its original signed_at/signed_by metadata across V15. The new status defaults to UNSIGNED; the M2 UI does not treat the legacy fields as verified consent. Review legacy consent semantics before production migration.
- Consent acknowledgement remains an administrative action, not a signature or treatment authorization. Guardian relationships remain unverified.

The accepted Maven NVD scan is DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION to M10. Complete a Maven vulnerability assessment before any production deployment.
