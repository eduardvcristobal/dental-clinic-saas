CREATE TABLE patient_profile_photo (
  clinic_id UUID NOT NULL,
  patient_id UUID NOT NULL,
  image_data BYTEA NOT NULL,
  updated_by UUID REFERENCES app_user(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (clinic_id, patient_id),
  FOREIGN KEY (clinic_id, patient_id) REFERENCES patient(clinic_id, id),
  CHECK (octet_length(image_data) <= 5242880)
);

ALTER TABLE patient_profile_photo ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_profile_photo FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON patient_profile_photo
  USING (clinic_id = current_setting('app.clinic_id', true)::uuid)
  WITH CHECK (clinic_id = current_setting('app.clinic_id', true)::uuid);
