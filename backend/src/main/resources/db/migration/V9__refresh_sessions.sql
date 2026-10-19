-- Hashed rotating refresh credentials. Browser cookie contains a tenant selector and a random secret.
CREATE TABLE auth_refresh_session (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL REFERENCES clinic(id),
  user_id UUID NOT NULL REFERENCES app_user(id),
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX auth_refresh_session_active_user ON auth_refresh_session (clinic_id, user_id, expires_at);
ALTER TABLE auth_refresh_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth_refresh_session FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON auth_refresh_session
  USING (clinic_id = nullif(current_setting('app.clinic_id', true), '')::uuid)
  WITH CHECK (clinic_id = nullif(current_setting('app.clinic_id', true), '')::uuid);
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dental_app') THEN
   GRANT SELECT, INSERT, UPDATE, DELETE ON auth_refresh_session TO dental_app;
 END IF;
END $$;
