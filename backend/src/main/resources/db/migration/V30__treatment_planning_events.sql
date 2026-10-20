-- M6.1–M6.6: reuse V4 treatment_plan/item/catalog and V5 billing; append-only change evidence.
ALTER TABLE treatment_plan ADD COLUMN IF NOT EXISTS consent_reference TEXT;
ALTER TABLE treatment_plan ADD COLUMN IF NOT EXISTS consent_recorded_at TIMESTAMPTZ;
ALTER TABLE treatment_plan ADD COLUMN IF NOT EXISTS consent_recorded_by UUID;
ALTER TABLE treatment_plan ADD CONSTRAINT fk_treatment_consent_actor
 FOREIGN KEY(clinic_id,consent_recorded_by) REFERENCES app_user(clinic_id,id);
ALTER TABLE treatment_plan ADD CONSTRAINT chk_treatment_consent_ref CHECK
 (consent_reference IS NULL OR length(btrim(consent_reference)) BETWEEN 3 AND 256);
CREATE TABLE treatment_plan_event(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 plan_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 actor_id UUID NOT NULL,
 event_type TEXT NOT NULL CHECK(event_type IN('CREATED','ITEM_ADDED','PROPOSED','ACCEPTED','DECLINED','STARTED','ITEM_SCHEDULED','ITEM_COMPLETED','COMPLETED')),
 details TEXT CHECK(details IS NULL OR length(details)<=1000),
 event_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,plan_id) REFERENCES treatment_plan(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,actor_id) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_treatment_plan_event ON treatment_plan_event(clinic_id,plan_id,event_at,id);
CREATE OR REPLACE FUNCTION treatment_history_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Treatment plan history is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER treatment_plan_event_immutable BEFORE UPDATE OR DELETE ON treatment_plan_event
 FOR EACH ROW EXECUTE FUNCTION treatment_history_immutable();
ALTER TABLE treatment_plan_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE treatment_plan_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON treatment_plan_event
 USING(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
 GRANT SELECT,INSERT ON treatment_plan_event TO dental_app;
 END IF; END $$;
-- A completed treatment item can only reference an encounter for the same patient.
ALTER TABLE treatment_plan ADD CONSTRAINT uq_treatment_plan_clinic_patient_id UNIQUE(clinic_id,patient_id,id);
ALTER TABLE visit ADD CONSTRAINT uq_visit_clinic_patient_id UNIQUE(clinic_id,patient_id,id);
-- RLS clinic checks and explicit API authorization remain mandatory.
