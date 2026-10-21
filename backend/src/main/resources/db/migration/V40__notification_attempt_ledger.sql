-- M8.11.3 durable, clinic-RLS send attempt ledger; never store patient message body or destination.
CREATE TABLE notification_delivery_attempt (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 delivery_id UUID NOT NULL,
 idempotency_key UUID NOT NULL,
 status TEXT NOT NULL CHECK (status IN ('CLAIMED','ACCEPTED','UNKNOWN')),
 provider_reference TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 concluded_at TIMESTAMPTZ,
 UNIQUE(clinic_id,delivery_id),
 FOREIGN KEY(clinic_id,delivery_id) REFERENCES notification_delivery(clinic_id,id)
);
CREATE INDEX notification_attempt_unresolved_idx ON notification_delivery_attempt(clinic_id,created_at)
 WHERE status IN ('CLAIMED','UNKNOWN');
ALTER TABLE notification_delivery_attempt ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_delivery_attempt FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_delivery_attempt
 USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
  GRANT SELECT,INSERT,UPDATE ON notification_delivery_attempt TO dental_app;
 END IF;
END $$;
