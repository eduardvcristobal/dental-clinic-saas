-- M2.2: separate guardian contact identities from patient charts; do not forge signatures.
CREATE TABLE patient_guardian_contact (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL REFERENCES clinic(id),
 patient_id UUID NOT NULL,
 full_name TEXT NOT NULL CHECK (length(trim(full_name)) BETWEEN 1 AND 160),
 relationship TEXT NOT NULL CHECK (length(trim(relationship)) BETWEEN 1 AND 80),
 phone TEXT, email TEXT,
 is_primary BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE (clinic_id,id),
 CONSTRAINT fk_guardian_contact_patient FOREIGN KEY (clinic_id,patient_id) REFERENCES patient(clinic_id,id)
);
CREATE UNIQUE INDEX uq_primary_guardian_contact ON patient_guardian_contact(clinic_id,patient_id) WHERE is_primary;
CREATE INDEX idx_guardian_contact_patient ON patient_guardian_contact(clinic_id,patient_id);
CREATE UNIQUE INDEX uq_medical_history_patient ON patient_medical_history(clinic_id,patient_id);
ALTER TABLE consent_form ADD COLUMN consent_status TEXT NOT NULL DEFAULT 'UNSIGNED' CHECK (consent_status IN ('UNSIGNED','ACKNOWLEDGED'));
ALTER TABLE consent_form ADD COLUMN acknowledged_at TIMESTAMPTZ;
ALTER TABLE consent_form ADD COLUMN acknowledged_by UUID;
ALTER TABLE consent_form ADD CONSTRAINT fk_consent_ack_user FOREIGN KEY (clinic_id,acknowledged_by) REFERENCES app_user(clinic_id,id);
ALTER TABLE patient_guardian_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_guardian_contact FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON patient_guardian_contact USING (clinic_id = nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id = nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
 GRANT SELECT,INSERT,UPDATE,DELETE ON patient_guardian_contact TO dental_app;
 END IF;
END $$;
