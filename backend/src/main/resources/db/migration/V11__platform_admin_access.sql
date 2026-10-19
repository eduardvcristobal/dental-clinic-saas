-- Allow an explicitly authenticated platform administrator to read/write across tenants.
-- app.platform_admin is stamped only from a validated PLATFORM_ADMIN JWT principal by the backend.
DO $$
DECLARE t text;
BEGIN
  FOR t IN
    SELECT c.table_name FROM information_schema.columns c
    JOIN information_schema.tables tb
      ON tb.table_schema = c.table_schema AND tb.table_name = c.table_name AND tb.table_type = 'BASE TABLE'
    WHERE c.table_schema = current_schema()
      AND c.column_name = 'clinic_id'
      AND c.table_name <> 'flyway_schema_history'
  LOOP
    EXECUTE format('CREATE POLICY platform_admin_access ON %I USING (current_setting(''app.platform_admin'', true) = ''true'') WITH CHECK (current_setting(''app.platform_admin'', true) = ''true'')', t);
  END LOOP;
END $$;

-- Global refresh sessions do not belong to a clinic. Access is limited to the backend runtime role.
CREATE TABLE platform_admin_refresh_session (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  admin_id UUID NOT NULL,
  email TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX platform_admin_refresh_session_admin ON platform_admin_refresh_session (admin_id, expires_at);
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dental_app') THEN
   GRANT SELECT, INSERT, UPDATE, DELETE ON platform_admin_refresh_session TO dental_app;
 END IF;
END $$;
