-- M1.2: one-time invitation tokens are stored hashed, never as plaintext.
CREATE TABLE staff_invitation (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL REFERENCES clinic(id),
 email TEXT NOT NULL, role TEXT NOT NULL CHECK (role IN ('ADMIN','DENTIST','FRONT_DESK')),
 branch_id UUID NOT NULL, token_hash TEXT NOT NULL UNIQUE,
 invited_by UUID NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 accepted_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT invitation_branch_tenant FOREIGN KEY (clinic_id, branch_id) REFERENCES branch(clinic_id,id),
 CONSTRAINT invitation_inviter_tenant FOREIGN KEY (clinic_id, invited_by) REFERENCES app_user(clinic_id,id),
 CONSTRAINT invitation_completion_exclusive CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);
CREATE INDEX staff_invitation_tenant_email ON staff_invitation(clinic_id, lower(email));
CREATE TABLE clinic_admin_event (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), clinic_id UUID NOT NULL REFERENCES clinic(id),
 actor_id UUID, action TEXT NOT NULL, subject_id UUID, detail TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT admin_event_actor_tenant FOREIGN KEY (clinic_id,actor_id) REFERENCES app_user(clinic_id,id)
);
ALTER TABLE staff_invitation ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff_invitation FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON staff_invitation USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
ALTER TABLE clinic_admin_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic_admin_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clinic_admin_event USING (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid) WITH CHECK (clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
-- Only one dentist profile per linked staff account, where linked.
CREATE UNIQUE INDEX uq_dentist_clinic_user ON dentist(clinic_id,user_id) WHERE user_id IS NOT NULL;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
  GRANT SELECT,INSERT,UPDATE,DELETE ON staff_invitation,clinic_admin_event TO dental_app;
 END IF;
END $$;
