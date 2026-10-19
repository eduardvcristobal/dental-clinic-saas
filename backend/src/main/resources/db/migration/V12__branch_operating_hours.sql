-- M1: local weekly operating hours per branch. Times are interpreted in the clinic timezone.
CREATE TABLE branch_operating_hour (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL,
  branch_id UUID NOT NULL,
  day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
  opens_at TIME NOT NULL,
  closes_at TIME NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT operating_hour_order CHECK (opens_at < closes_at),
  CONSTRAINT operating_hour_unique UNIQUE (branch_id, day_of_week),
  CONSTRAINT operating_hour_tenant_branch FOREIGN KEY (clinic_id, branch_id) REFERENCES branch(clinic_id, id)
);
CREATE INDEX branch_operating_hour_clinic_branch ON branch_operating_hour(clinic_id, branch_id);
ALTER TABLE branch_operating_hour ENABLE ROW LEVEL SECURITY;
ALTER TABLE branch_operating_hour FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON branch_operating_hour
 USING (clinic_id = nullif(current_setting('app.clinic_id', true), '')::uuid)
 WITH CHECK (clinic_id = nullif(current_setting('app.clinic_id', true), '')::uuid);
-- V11 platform-admin privilege policy is intentionally NOT inherited by this new table.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dental_app') THEN
  GRANT SELECT, INSERT, UPDATE, DELETE ON branch_operating_hour TO dental_app;
 END IF;
END $$;
