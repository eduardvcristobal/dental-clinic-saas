-- M5.2: clinical SOAP drafts and structured diagnoses. Signing belongs to M5.3.
CREATE TABLE encounter_soap_draft (
 visit_id UUID PRIMARY KEY,
 clinic_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 subjective TEXT NOT NULL DEFAULT '' CHECK(length(subjective)<=20000),
 objective TEXT NOT NULL DEFAULT '' CHECK(length(objective)<=20000),
 assessment TEXT NOT NULL DEFAULT '' CHECK(length(assessment)<=20000),
 plan_notes TEXT NOT NULL DEFAULT '' CHECK(length(plan_notes)<=20000),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 updated_by UUID NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(clinic_id,visit_id),
 FOREIGN KEY(clinic_id,visit_id) REFERENCES visit(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,updated_by) REFERENCES app_user(clinic_id,id)
);
CREATE TABLE encounter_soap_revision (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 visit_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 draft_version BIGINT NOT NULL,
 subjective TEXT NOT NULL,
 objective TEXT NOT NULL,
 assessment TEXT NOT NULL,
 plan_notes TEXT NOT NULL,
 actor_user_id UUID NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(clinic_id,visit_id,draft_version),
 FOREIGN KEY(clinic_id,visit_id) REFERENCES visit(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,actor_user_id) REFERENCES app_user(clinic_id,id)
);
CREATE TABLE encounter_diagnosis (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 visit_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 code_system TEXT NOT NULL CHECK(code_system IN ('ICD10','SNODENT','LOCAL')),
 diagnosis_code TEXT NOT NULL CHECK(length(btrim(diagnosis_code)) BETWEEN 1 AND 80),
 description TEXT NOT NULL CHECK(length(btrim(description)) BETWEEN 1 AND 500),
 created_by UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(clinic_id,id),
 FOREIGN KEY(clinic_id,visit_id) REFERENCES visit(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,created_by) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_encounter_diagnosis_visit ON encounter_diagnosis(clinic_id,visit_id,created_at);
CREATE INDEX idx_encounter_soap_revision_visit ON encounter_soap_revision(clinic_id,visit_id,recorded_at);
CREATE OR REPLACE FUNCTION encounter_history_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Clinical history is append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER soap_revision_immutable BEFORE UPDATE OR DELETE ON encounter_soap_revision
 FOR EACH ROW EXECUTE FUNCTION encounter_history_immutable();
CREATE TRIGGER diagnosis_immutable BEFORE UPDATE OR DELETE ON encounter_diagnosis
 FOR EACH ROW EXECUTE FUNCTION encounter_history_immutable();
DO $$ DECLARE tab TEXT; BEGIN
 FOREACH tab IN ARRAY ARRAY['encounter_soap_draft','encounter_soap_revision','encounter_diagnosis'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tab);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',tab);
  EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid) WITH CHECK (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid)',tab);
 END LOOP;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
  GRANT SELECT,INSERT,UPDATE ON encounter_soap_draft TO dental_app;
  GRANT SELECT,INSERT ON encounter_soap_revision,encounter_diagnosis TO dental_app;
 END IF;
END $$;
