-- M5.3 clinical signing: immutable signed snapshot and append-only reasoned amendments.
CREATE TABLE encounter_signed_note (
 visit_id UUID PRIMARY KEY,
 clinic_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 draft_version BIGINT NOT NULL CHECK(draft_version>0),
 subjective TEXT NOT NULL, objective TEXT NOT NULL, assessment TEXT NOT NULL, plan_notes TEXT NOT NULL,
 signed_by UUID NOT NULL, signed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(clinic_id,visit_id),
 FOREIGN KEY(clinic_id,visit_id) REFERENCES visit(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,signed_by) REFERENCES app_user(clinic_id,id)
);
CREATE TABLE encounter_note_amendment (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,visit_id UUID NOT NULL,patient_id UUID NOT NULL,
 reason TEXT NOT NULL CHECK(length(btrim(reason)) BETWEEN 3 AND 500),
 amendment TEXT NOT NULL CHECK(length(btrim(amendment)) BETWEEN 1 AND 20000),
 author_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,visit_id) REFERENCES encounter_signed_note(clinic_id,visit_id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,author_id) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_encounter_amendment_visit ON encounter_note_amendment(clinic_id,visit_id,created_at,id);
CREATE TRIGGER signed_note_immutable BEFORE UPDATE OR DELETE ON encounter_signed_note
 FOR EACH ROW EXECUTE FUNCTION encounter_history_immutable();
CREATE TRIGGER note_amendment_immutable BEFORE UPDATE OR DELETE ON encounter_note_amendment
 FOR EACH ROW EXECUTE FUNCTION encounter_history_immutable();
DO $$ DECLARE tab TEXT; BEGIN
 FOREACH tab IN ARRAY ARRAY['encounter_signed_note','encounter_note_amendment'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tab);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',tab);
  EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid) WITH CHECK (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid)',tab);
 END LOOP;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
  GRANT SELECT,INSERT ON encounter_signed_note,encounter_note_amendment TO dental_app;
 END IF;
END $$;
