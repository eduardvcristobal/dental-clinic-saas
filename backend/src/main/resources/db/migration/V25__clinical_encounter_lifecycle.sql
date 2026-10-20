-- M5.1: additive lifecycle for existing V4 visit model. No legacy clinical notes changed.
ALTER TABLE visit ADD COLUMN encounter_status TEXT NOT NULL DEFAULT 'IN_PROGRESS'
  CHECK (encounter_status IN ('IN_PROGRESS','COMPLETED','CANCELLED'));
ALTER TABLE visit ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE visit ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE visit ADD CONSTRAINT visit_lifecycle_consistency CHECK (
  (encounter_status='IN_PROGRESS' AND completed_at IS NULL AND cancelled_at IS NULL)
  OR (encounter_status='COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
  OR (encounter_status='CANCELLED' AND completed_at IS NULL AND cancelled_at IS NOT NULL)
);
CREATE INDEX idx_visit_patient_lifecycle ON visit(clinic_id,patient_id,started_at DESC,id);

CREATE TABLE visit_lifecycle_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL,
  visit_id UUID NOT NULL,
  patient_id UUID NOT NULL,
  actor_user_id UUID NOT NULL,
  from_status TEXT CHECK (from_status IN ('IN_PROGRESS','COMPLETED','CANCELLED')),
  to_status TEXT NOT NULL CHECK (to_status IN ('IN_PROGRESS','COMPLETED','CANCELLED')),
  reason TEXT CHECK (reason IS NULL OR length(reason)<=500),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(clinic_id,id),
  FOREIGN KEY(clinic_id,visit_id) REFERENCES visit(clinic_id,id),
  FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
  FOREIGN KEY(clinic_id,actor_user_id) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_visit_lifecycle_history ON visit_lifecycle_event(clinic_id,visit_id,recorded_at,id);
CREATE OR REPLACE FUNCTION visit_lifecycle_event_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Visit lifecycle history is append-only' USING ERRCODE='23514';
END $$;
CREATE TRIGGER visit_lifecycle_no_mutation BEFORE UPDATE OR DELETE ON visit_lifecycle_event
 FOR EACH ROW EXECUTE FUNCTION visit_lifecycle_event_immutable();
ALTER TABLE visit_lifecycle_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE visit_lifecycle_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON visit_lifecycle_event
 USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
   GRANT SELECT,INSERT ON visit_lifecycle_event TO dental_app;
 END IF;
END $$;
