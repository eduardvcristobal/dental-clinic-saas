-- V8: PostgreSQL row-level security as a backstop to Hibernate's @TenantId.
-- The app sets  SELECT set_config('app.clinic_id', '<uuid>', false)  on every connection checkout.
-- If it is unset, no rows are visible and no rows can be written (fail closed).
-- FORCE applies the policy to the table owner too. The app should still connect as a
-- non-superuser role WITHOUT BYPASSRLS (superusers and BYPASSRLS roles skip RLS).
DO $$
DECLARE t text;
BEGIN
  FOR t IN
    SELECT c.table_name FROM information_schema.columns c
    JOIN information_schema.tables tb
      ON tb.table_schema = c.table_schema AND tb.table_name = c.table_name AND tb.table_type = 'BASE TABLE'
    WHERE c.table_schema = current_schema()
      AND c.column_name = 'clinic_id'
      AND c.table_name NOT IN ('flyway_schema_history')
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON %I
         USING (clinic_id = nullif(current_setting(''app.clinic_id'', true), '''')::uuid)
         WITH CHECK (clinic_id = nullif(current_setting(''app.clinic_id'', true), '''')::uuid)', t);
  END LOOP;
END $$;

-- Grant the runtime role (created by your infra/DBA) access, if it exists.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dental_app') THEN
    GRANT USAGE ON SCHEMA public TO dental_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO dental_app;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO dental_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO dental_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO dental_app;
  END IF;
END $$;
