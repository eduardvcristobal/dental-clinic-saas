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

## Verification
- Implementation and source packaging performed here. Java 17/Maven, PostgreSQL, and installed frontend dependencies were unavailable; compilation and runtime E2E are **NOT VERIFIED**.
- Execute on local development environment: `cd backend && mvn clean verify`, fresh V1–V15 migrations and restricted-role RLS; `cd frontend && npm ci && npm run lint && npm run build && npm test`; then browser regression for clinical roles and Front Desk.
- Maintain the accepted NVD deferral as a pre-production requirement.
