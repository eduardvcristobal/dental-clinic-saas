-- M8.10.1: additive provider-delivery primitives; provider sending remains disabled until approved.
ALTER TABLE notification_policy
 ADD COLUMN timezone_id TEXT NOT NULL DEFAULT 'UTC',
 ADD CONSTRAINT notification_policy_timezone_nonempty CHECK (length(trim(timezone_id)) BETWEEN 1 AND 80);
ALTER TABLE notification_delivery
 ADD COLUMN destination_snapshot TEXT,
 ADD COLUMN provider_name TEXT,
 ADD COLUMN provider_event_at TIMESTAMPTZ,
 ADD COLUMN provider_status TEXT,
 ADD COLUMN last_attempt_at TIMESTAMPTZ,
 ADD COLUMN idempotency_key UUID NOT NULL DEFAULT gen_random_uuid(),
 ADD COLUMN appointment_start_snapshot TIMESTAMPTZ,
 ADD COLUMN suppressed_at TIMESTAMPTZ;
CREATE UNIQUE INDEX notification_delivery_idempotency_unique ON notification_delivery(idempotency_key);
CREATE INDEX notification_delivery_claim_idx
 ON notification_delivery(scheduled_for, next_attempt_at, lease_until)
 WHERE status IN ('PENDING','CLAIMED','FAILED');
CREATE UNIQUE INDEX notification_delivery_provider_reference_unique
 ON notification_delivery(provider_name,provider_reference)
 WHERE provider_name IS NOT NULL AND provider_reference IS NOT NULL;
CREATE TABLE notification_delivery_event (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 delivery_id UUID NOT NULL,
 provider_name TEXT NOT NULL,
 provider_event_id TEXT NOT NULL,
 event_type TEXT NOT NULL CHECK (event_type IN ('ACCEPTED','SENT','DELIVERED','BOUNCED','FAILED','COMPLAINED','UNSUBSCRIBED')),
 occurred_at TIMESTAMPTZ NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(provider_name,provider_event_id),
 FOREIGN KEY(clinic_id,delivery_id) REFERENCES notification_delivery(clinic_id,id)
);
CREATE TABLE notification_patient_action (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 delivery_id UUID NOT NULL,
 token_hash BYTEA NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ,
 requested_action TEXT NOT NULL CHECK(requested_action IN('CONFIRM','CANCEL_REQUEST','RESCHEDULE_REQUEST','OPT_OUT')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,delivery_id) REFERENCES notification_delivery(clinic_id,id)
);
CREATE INDEX notification_patient_action_expiration_idx ON notification_patient_action(expires_at) WHERE consumed_at IS NULL;
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['notification_delivery_event','notification_patient_action']
 LOOP
 EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
 EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
 EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid) WITH CHECK(clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid)',t);
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
   EXECUTE format('GRANT SELECT,INSERT,UPDATE ON %I TO dental_app',t);
 END IF;
 END LOOP;
END $$;
