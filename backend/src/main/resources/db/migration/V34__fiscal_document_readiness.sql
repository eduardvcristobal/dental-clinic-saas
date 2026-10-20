-- M7.12: fiscal-document readiness metadata. This is not a registered invoice issuer.
CREATE TABLE fiscal_profile (
 clinic_id UUID PRIMARY KEY REFERENCES clinic(id),
 jurisdiction CHAR(2) NOT NULL CHECK(jurisdiction ~ '^[A-Z]{2}$'),
 registered_name TEXT NOT NULL CHECK(length(btrim(registered_name)) BETWEEN 3 AND 200),
 registered_address TEXT NOT NULL CHECK(length(btrim(registered_address)) BETWEEN 5 AND 500),
 tax_registration_no TEXT NOT NULL CHECK(length(btrim(tax_registration_no)) BETWEEN 3 AND 80),
 tax_category TEXT NOT NULL CHECK(tax_category IN('VAT','NON_VAT','OTHER')),
 permit_reference TEXT CHECK(permit_reference IS NULL OR length(btrim(permit_reference)) BETWEEN 3 AND 150),
 reviewed_by UUID,
 reviewed_at TIMESTAMPTZ,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,reviewed_by) REFERENCES app_user(clinic_id,id)
);
ALTER TABLE fiscal_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE fiscal_profile FORCE ROW LEVEL SECURITY;
CREATE POLICY fiscal_profile_tenant ON fiscal_profile
 USING(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
 GRANT SELECT,INSERT,UPDATE ON fiscal_profile TO dental_app;
 END IF; END $$;
