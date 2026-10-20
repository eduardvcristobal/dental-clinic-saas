-- M3.1: scoped appointment configuration. All appointments retain V3 exclusion rules.
ALTER TABLE appointment_type ADD COLUMN buffer_before_minutes INT NOT NULL DEFAULT 0 CHECK (buffer_before_minutes BETWEEN 0 AND 240);
ALTER TABLE appointment_type ADD COLUMN buffer_after_minutes INT NOT NULL DEFAULT 0 CHECK (buffer_after_minutes BETWEEN 0 AND 240);
CREATE UNIQUE INDEX uq_appointment_type_name_per_clinic ON appointment_type(clinic_id, lower(name));
CREATE TABLE scheduling_policy (
 clinic_id UUID PRIMARY KEY REFERENCES clinic(id), slot_minutes INT NOT NULL DEFAULT 15 CHECK(slot_minutes IN (5,10,15,20,30,60)),
 minimum_notice_minutes INT NOT NULL DEFAULT 0 CHECK(minimum_notice_minutes BETWEEN 0 AND 43200),
 booking_horizon_days INT NOT NULL DEFAULT 180 CHECK(booking_horizon_days BETWEEN 1 AND 730),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE scheduling_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE scheduling_policy FORCE ROW LEVEL SECURITY;
CREATE POLICY scheduling_policy_tenant ON scheduling_policy USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN GRANT SELECT,INSERT,UPDATE,DELETE ON scheduling_policy TO dental_app; END IF; END $$;
