-- Clinic-wide or branch-specific all-day closure dates.
CREATE TABLE clinic_holiday (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL,
  branch_id UUID,
  starts_on DATE NOT NULL,
  ends_on DATE NOT NULL,
  name TEXT NOT NULL,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (starts_on <= ends_on),
  CHECK (char_length(trim(name)) BETWEEN 1 AND 120),
  FOREIGN KEY (clinic_id) REFERENCES clinic(id),
  FOREIGN KEY (clinic_id, branch_id) REFERENCES branch(clinic_id, id)
);
CREATE INDEX clinic_holiday_scope_dates ON clinic_holiday(clinic_id, branch_id, starts_on, ends_on);
ALTER TABLE clinic_holiday ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic_holiday FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clinic_holiday
  USING (clinic_id = nullif(current_setting('app.clinic_id', true), '')::uuid)
  WITH CHECK (clinic_id = nullif(current_setting('app.clinic_id', true), '')::uuid);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dental_app') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON clinic_holiday TO dental_app;
  END IF;
END $$;
