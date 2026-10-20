-- M3.3 chair resource unavailability.
CREATE TABLE chair_block (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL, chair_id UUID NOT NULL,
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL, reason TEXT, CHECK(starts_at<ends_at),
 FOREIGN KEY(clinic_id,chair_id) REFERENCES chair(clinic_id,id)
);
ALTER TABLE chair_block ENABLE ROW LEVEL SECURITY; ALTER TABLE chair_block FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON chair_block USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN GRANT SELECT,INSERT,UPDATE,DELETE ON chair_block TO dental_app; END IF; END $$;
