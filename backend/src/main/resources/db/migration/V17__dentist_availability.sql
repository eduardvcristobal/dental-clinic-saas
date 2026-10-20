-- M3.2: dentist weekly local-time availability and date exceptions.
CREATE TABLE dentist_availability (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL, dentist_id UUID NOT NULL,
 branch_id UUID NOT NULL, day_of_week SMALLINT NOT NULL CHECK(day_of_week BETWEEN 1 AND 7),
 starts_at TIME NOT NULL, ends_at TIME NOT NULL, CHECK(starts_at<ends_at),
 FOREIGN KEY(clinic_id,dentist_id) REFERENCES dentist(clinic_id,id),
 FOREIGN KEY(clinic_id,branch_id) REFERENCES branch(clinic_id,id),
 UNIQUE(dentist_id,branch_id,day_of_week,starts_at)
);
CREATE TABLE dentist_time_off (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL, dentist_id UUID NOT NULL,
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL, reason TEXT, CHECK(starts_at<ends_at),
 FOREIGN KEY(clinic_id,dentist_id) REFERENCES dentist(clinic_id,id)
);
DO $$ DECLARE t text; BEGIN FOREACH t IN ARRAY ARRAY['dentist_availability','dentist_time_off'] LOOP
 EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
 EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
 EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid) WITH CHECK (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid)',t);
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON %I TO dental_app',t); END IF;
 END LOOP; END $$;
