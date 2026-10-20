-- M5.4: auditable, clinic-scoped prescription drafts and encounter document references.
-- Not an electronic prescribing transmission, dispensing or external storage service.
CREATE TABLE encounter_prescription_draft (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),clinic_id UUID NOT NULL,visit_id UUID NOT NULL,patient_id UUID NOT NULL,
 medication TEXT NOT NULL CHECK(length(btrim(medication)) BETWEEN 1 AND 300),
 dosage TEXT NOT NULL CHECK(length(btrim(dosage)) BETWEEN 1 AND 500),
 instructions TEXT NOT NULL CHECK(length(btrim(instructions)) BETWEEN 1 AND 2000),
 status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status='DRAFT'),
 author_id UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,visit_id) REFERENCES visit(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,author_id) REFERENCES app_user(clinic_id,id)
);
CREATE TABLE encounter_document_reference (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),clinic_id UUID NOT NULL,visit_id UUID NOT NULL,patient_id UUID NOT NULL,
 filename TEXT NOT NULL CHECK(length(btrim(filename)) BETWEEN 1 AND 255),
 external_reference TEXT NOT NULL CHECK(length(btrim(external_reference)) BETWEEN 1 AND 512),
 content_type TEXT NOT NULL CHECK(length(btrim(content_type)) BETWEEN 1 AND 120),
 added_by UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,visit_id) REFERENCES visit(clinic_id,id),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,added_by) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_encounter_rx ON encounter_prescription_draft(clinic_id,visit_id,created_at);
CREATE INDEX idx_encounter_documents ON encounter_document_reference(clinic_id,visit_id,created_at);
CREATE TRIGGER encounter_rx_immutable BEFORE UPDATE OR DELETE ON encounter_prescription_draft FOR EACH ROW EXECUTE FUNCTION encounter_history_immutable();
CREATE TRIGGER encounter_doc_immutable BEFORE UPDATE OR DELETE ON encounter_document_reference FOR EACH ROW EXECUTE FUNCTION encounter_history_immutable();
DO $$ DECLARE tab TEXT; BEGIN
 FOREACH tab IN ARRAY ARRAY['encounter_prescription_draft','encounter_document_reference'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tab);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',tab);
  EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid) WITH CHECK (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid)',tab);
 END LOOP;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
 GRANT SELECT,INSERT ON encounter_prescription_draft,encounter_document_reference TO dental_app;
 END IF;
END $$;
