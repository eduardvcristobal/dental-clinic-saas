-- M5.1 additive encounter lifecycle. Preserve V4 visit and existing signed notes.
CREATE TABLE visit_lifecycle (
 visit_id UUID PRIMARY KEY,
 clinic_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 status TEXT NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS','COMPLETED','CANCELLED')),
 started_by UUID NOT NULL,
 completed_by UUID,
 cancelled_by UUID,
 closed_at TIMESTAMPTZ,
 cancellation_reason TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE (clinic_id, visit_id),
 FOREIGN KEY (clinic_id,visit_id) REFERENCES visit(clinic_id,id),
 FOREIGN KEY (clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY (clinic_id,started_by) REFERENCES app_user(clinic_id,id),
 FOREIGN KEY (clinic_id,completed_by) REFERENCES app_user(clinic_id,id),
 FOREIGN KEY (clinic_id,cancelled_by) REFERENCES app_user(clinic_id,id),
 CHECK ((status='IN_PROGRESS' AND closed_at IS NULL AND completed_by IS NULL AND cancelled_by IS NULL)
     OR (status='COMPLETED' AND closed_at IS NOT NULL AND completed_by IS NOT NULL AND cancelled_by IS NULL)
     OR (status='CANCELLED' AND closed_at IS NOT NULL AND cancelled_by IS NOT NULL AND completed_by IS NULL AND length(btrim(cancellation_reason))>0))
);
CREATE INDEX idx_visit_lifecycle_patient ON visit_lifecycle(clinic_id,patient_id,created_at DESC);
ALTER TABLE visit_lifecycle ENABLE ROW LEVEL SECURITY;
ALTER TABLE visit_lifecycle FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON visit_lifecycle
 USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
 GRANT SELECT,INSERT,UPDATE ON visit_lifecycle TO dental_app;
 END IF; END $$;
