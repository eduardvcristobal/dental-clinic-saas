-- M3.9: notification events only; M8 delivers messages.
CREATE TABLE appointment_notification_outbox (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL, appointment_id UUID NOT NULL,
 event_type TEXT NOT NULL CHECK(event_type IN ('BOOKED','RESCHEDULED','CANCELLED','CONFIRMED')),
 state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','DELIVERED','FAILED','SUPPRESSED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), processed_at TIMESTAMPTZ,
 FOREIGN KEY(clinic_id,appointment_id) REFERENCES appointment(clinic_id,id)
);
ALTER TABLE appointment_notification_outbox ENABLE ROW LEVEL SECURITY; ALTER TABLE appointment_notification_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointment_notification_outbox USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN GRANT SELECT,INSERT,UPDATE,DELETE ON appointment_notification_outbox TO dental_app; END IF; END $$;
