-- M5.5 optional encounter link. Existing patient-wide odontogram events remain unmodified/unattributed.
ALTER TABLE visit ADD CONSTRAINT uq_visit_clinic_patient_visit
 UNIQUE(clinic_id,patient_id,id);
ALTER TABLE odontogram_finding_event ADD COLUMN encounter_visit_id UUID;
ALTER TABLE odontogram_finding_event ADD CONSTRAINT fk_odontogram_event_encounter_patient
 FOREIGN KEY(clinic_id,patient_id,encounter_visit_id)
 REFERENCES visit(clinic_id,patient_id,id);
CREATE INDEX idx_odontogram_event_encounter ON odontogram_finding_event(clinic_id,encounter_visit_id,recorded_at,id)
 WHERE encounter_visit_id IS NOT NULL;
