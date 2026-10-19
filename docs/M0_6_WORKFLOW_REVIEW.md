# M0.6 — Patient Workflow Simulation & Design Review

Date: 2026-10-19  
Status: Implemented in React, runtime validation tracked in `PROJECT_STATUS.md`.

## Purpose

Provide an end-to-end interactive **design simulator** with fictional patient data. The simulation is not an electronic medical record and must not call clinical, billing, reminder or scheduling APIs. Review checklists exist only in React component state and reset when leaving/reloading.

## Seven stages

1. **Register (M2)** — existing-patient check, demographics, medical history, consent.
2. **Book (M3)** — dentist/chair availability, appointment confirmation.
3. **Arrive (M3)** — identity verification, waiting queue, medical changes.
4. **Examine (M4–M5)** — review history and odontogram, SOAP documentation and signing.
5. **Plan (M6)** — estimate, treatment acceptance, phase progression.
6. **Bill (M7)** — performed services, invoice, partial payment and audit.
7. **Recall (M8)** — agreed follow-up, communication preferences, opt-outs.

Each stage links to an existing preview or coming-soon destination *only when that module is visible for the authenticated role*. The simulator does not grant any permissions.

## Exception paths for subsequent design review

- **Walk-ins:** may check in without a previous appointment; emergency presentation should not be blocked by regular calendar booking.
- **Consultation only:** visit and SOAP note can stand alone without a treatment plan, procedure, or invoice.
- **Deferred, cancelled or refused care:** record clinical decision and patient consent/refusal before billing actual services.
- **Multi-visit plans:** actual procedures link to encounter and tooth chart; proposal is separate from performed care.
- **Corrections:** signed notes require addenda, never silent overwrite.
- **Tenant & role limits:** backend remains authoritative; no clinical data to Platform Admin merely due to administrative privileges.

## Design acceptance discussion

Validate with one front-desk staff member and one dentist on both desktop and tablet: recognition of stages, ability to find the patient 360° screen, clarity of role-specific actions, meaning of alerts, and wording of demo-only vs coming-soon badges. Record decisions here before M1.

## Scope guardrails

This is **not** real registration, check-in, odontogram editing, SOAP signing, billing, or reminder delivery. M0.4's provider, CI and Platform Admin security findings remain open. M0.5 human responsive verification is also still open.
