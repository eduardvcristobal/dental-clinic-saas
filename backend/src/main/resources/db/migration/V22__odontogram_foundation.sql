-- M4.1 Odontogram foundation. Append-only findings preserve clinical history.
-- V4 tooth_record is retained for compatibility; this model is additive.
CREATE OR REPLACE FUNCTION odontogram_valid_fdi(n integer)
RETURNS boolean LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
 SELECT (n BETWEEN 11 AND 48 AND n / 10 BETWEEN 1 AND 4 AND n % 10 BETWEEN 1 AND 8)
     OR (n BETWEEN 51 AND 85 AND n / 10 BETWEEN 5 AND 8 AND n % 10 BETWEEN 1 AND 5);
$$;

CREATE OR REPLACE FUNCTION odontogram_valid_surface(n integer, s text)
RETURNS boolean LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
 SELECT s IS NULL OR (
  odontogram_valid_fdi(n) AND (
   s IN ('M','D','B','L','F') OR
   (s = 'P' AND n / 10 IN (1,2,5,6)) OR
   (s = 'O' AND n % 10 >= 4) OR
   (s = 'I' AND n % 10 <= 3)
  )
 );
$$;

CREATE TABLE odontogram_chart (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL REFERENCES clinic(id),
 patient_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_by UUID,
 UNIQUE(clinic_id,id),
 UNIQUE(clinic_id,patient_id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,created_by) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_odontogram_chart_patient ON odontogram_chart(clinic_id,patient_id);

CREATE TABLE odontogram_finding_event (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL REFERENCES clinic(id),
 chart_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 tooth_number SMALLINT NOT NULL CHECK (odontogram_valid_fdi(tooth_number)),
 surface TEXT CHECK (odontogram_valid_surface(tooth_number,surface)),
 condition_code TEXT NOT NULL CHECK(length(btrim(condition_code)) BETWEEN 1 AND 80),
 event_kind TEXT NOT NULL CHECK (event_kind IN ('OBSERVATION','CORRECTION','RETRACTION')),
 clinical_status TEXT NOT NULL DEFAULT 'EXISTING' CHECK (clinical_status IN ('EXISTING','PLANNED','COMPLETED')),
 notes TEXT,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 recorded_by UUID NOT NULL,
 supersedes_event_id UUID,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(clinic_id,id),
 UNIQUE(clinic_id,patient_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES odontogram_chart(clinic_id,patient_id),
 FOREIGN KEY(clinic_id,chart_id) REFERENCES odontogram_chart(clinic_id,id),
 FOREIGN KEY(clinic_id,recorded_by) REFERENCES app_user(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id,supersedes_event_id)
    REFERENCES odontogram_finding_event(clinic_id,patient_id,id),
 CHECK ((event_kind = 'OBSERVATION' AND supersedes_event_id IS NULL)
    OR (event_kind IN ('CORRECTION','RETRACTION') AND supersedes_event_id IS NOT NULL))
);
CREATE INDEX idx_odontogram_event_history ON odontogram_finding_event(clinic_id,patient_id,recorded_at DESC,id);
CREATE INDEX idx_odontogram_event_tooth ON odontogram_finding_event(clinic_id,chart_id,tooth_number,recorded_at DESC);

-- A finding must belong to the specified chart, even when both IDs belong to this clinic.
ALTER TABLE odontogram_chart ADD CONSTRAINT uq_odontogram_chart_patient_id UNIQUE(clinic_id,patient_id,id);
ALTER TABLE odontogram_finding_event ADD CONSTRAINT fk_odontogram_event_chart_patient
  FOREIGN KEY(clinic_id,patient_id,chart_id) REFERENCES odontogram_chart(clinic_id,patient_id,id);

CREATE OR REPLACE FUNCTION odontogram_prevent_event_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Odontogram history is append-only; use a correction event' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER odontogram_history_immutable
BEFORE UPDATE OR DELETE ON odontogram_finding_event
FOR EACH ROW EXECUTE FUNCTION odontogram_prevent_event_mutation();

ALTER TABLE odontogram_chart ENABLE ROW LEVEL SECURITY;
ALTER TABLE odontogram_chart FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON odontogram_chart
 USING (clinic_id = nullif(current_setting('app.clinic_id', true),'')::uuid)
 WITH CHECK (clinic_id = nullif(current_setting('app.clinic_id', true),'')::uuid);
ALTER TABLE odontogram_finding_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE odontogram_finding_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON odontogram_finding_event
 USING (clinic_id = nullif(current_setting('app.clinic_id', true),'')::uuid)
 WITH CHECK (clinic_id = nullif(current_setting('app.clinic_id', true),'')::uuid);

DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname = 'dental_app') THEN
  GRANT SELECT,INSERT,UPDATE,DELETE ON odontogram_chart TO dental_app;
  GRANT SELECT,INSERT ON odontogram_finding_event TO dental_app;
 END IF;
END $$;
