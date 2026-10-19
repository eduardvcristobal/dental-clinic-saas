-- M1.3: platform operators are not clinical users. V11's blanket policy is removed.
-- The global clinic directory is intentionally separate from tenant clinical tables.
DO $$
DECLARE item record;
BEGIN
  FOR item IN SELECT schemaname, tablename FROM pg_policies
    WHERE schemaname = current_schema() AND policyname = 'platform_admin_access'
  LOOP
    EXECUTE format('DROP POLICY platform_admin_access ON %I.%I', item.schemaname, item.tablename);
  END LOOP;
END $$;
-- Tenant permissions and tenant-specific RLS remain untouched. Do not add
-- platform-wide read/write exceptions for patients, users or financial records.
