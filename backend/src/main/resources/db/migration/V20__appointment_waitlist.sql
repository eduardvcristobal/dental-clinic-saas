-- M3.8 waitlist and slot offers (no SMS/provider dispatch).
CREATE TABLE appointment_waitlist (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL, patient_id UUID NOT NULL,
 branch_id UUID NOT NULL, appointment_type_id UUID, preferred_after TIMESTAMPTZ NOT NULL,
 preferred_before TIMESTAMPTZ NOT NULL, status TEXT NOT NULL DEFAULT 'WAITING' CHECK(status IN ('WAITING','OFFERED','BOOKED','CANCELLED','EXPIRED')),
 offered_until TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(preferred_after<preferred_before),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,branch_id) REFERENCES branch(clinic_id,id),
 FOREIGN KEY(clinic_id,appointment_type_id) REFERENCES appointment_type(clinic_id,id)
);
ALTER TABLE appointment_waitlist ENABLE ROW LEVEL SECURITY; ALTER TABLE appointment_waitlist FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointment_waitlist USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN GRANT SELECT,INSERT,UPDATE,DELETE ON appointment_waitlist TO dental_app; END IF; END $$;
