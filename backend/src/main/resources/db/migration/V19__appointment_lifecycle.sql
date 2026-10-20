-- M3.6 reschedule/cancellation/status append-only events.
CREATE TABLE appointment_event (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL, appointment_id UUID NOT NULL,
 changed_by UUID NOT NULL, event_type TEXT NOT NULL,
 old_status TEXT, new_status TEXT, old_starts_at TIMESTAMPTZ, new_starts_at TIMESTAMPTZ,
 reason TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,appointment_id) REFERENCES appointment(clinic_id,id),
 FOREIGN KEY(clinic_id,changed_by) REFERENCES app_user(clinic_id,id)
);
ALTER TABLE appointment_event ENABLE ROW LEVEL SECURITY; ALTER TABLE appointment_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointment_event USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN GRANT SELECT,INSERT,UPDATE,DELETE ON appointment_event TO dental_app; END IF; END $$;
