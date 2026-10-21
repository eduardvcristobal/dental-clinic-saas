-- M8.11.4: shared, transactionally atomic redemption throttle across application replicas.
CREATE TABLE notification_redemption_limit (
 clinic_id UUID NOT NULL REFERENCES clinic(id),
 bucket_start TIMESTAMPTZ NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts >= 0),
 PRIMARY KEY(clinic_id,bucket_start)
);
ALTER TABLE notification_redemption_limit ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_redemption_limit FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_redemption_limit
 USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
  GRANT SELECT,INSERT,UPDATE ON notification_redemption_limit TO dental_app;
 END IF;
END $$;
