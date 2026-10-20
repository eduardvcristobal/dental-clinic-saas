-- M4.4: ensure that a finding event cannot be superseded twice in concurrent transactions.
-- PostgreSQL enforces this atomically even when both requests pass API prechecks.
CREATE UNIQUE INDEX uq_odontogram_single_supersession
    ON odontogram_finding_event (clinic_id, patient_id, supersedes_event_id)
    WHERE supersedes_event_id IS NOT NULL;

-- Explicitly bound event kinds and author relationships are enforced by V22.
-- Append-only trigger prevents destructive updates and deletes.
