-- Execute with psql as the NON-owner dental_app role after Flyway V1-V9.
-- psql -X -v ON_ERROR_STOP=1 -U dental_app -d dental -f backend/scripts/verify-rls.sql
-- Includes negative checks without requiring seeded PHI.
DO $$ BEGIN
 IF current_setting('is_superuser')='on' OR EXISTS (
   SELECT 1 FROM pg_roles WHERE rolname=current_user AND (rolbypassrls OR rolsuper)) THEN
   RAISE EXCEPTION 'RLS verification must use a non-bypass runtime role';
 END IF;
END $$;
SELECT tablename, rowsecurity, forcerowsecurity
FROM pg_tables WHERE schemaname='public' AND tablename IN
 ('app_user','patient','payment','auth_refresh_session');
DO $$ DECLARE failures int; BEGIN
 SELECT count(*) INTO failures FROM pg_tables WHERE schemaname='public'
  AND tablename IN ('app_user','patient','payment','auth_refresh_session')
  AND (NOT rowsecurity OR NOT forcerowsecurity);
 IF failures<>0 THEN RAISE EXCEPTION 'RLS not enabled and forced on checked tables'; END IF;
END $$;
-- A missing tenant may never reveal any app users.
SELECT set_config('app.clinic_id','',false);
DO $$ BEGIN IF EXISTS (SELECT 1 FROM app_user) THEN
 RAISE EXCEPTION 'Missing tenant leaked app_user rows'; END IF; END $$;
-- Create seeded test fixtures in a disposable DB to verify cross-tenant INSERT/SELECT/UPDATE/DELETE.
