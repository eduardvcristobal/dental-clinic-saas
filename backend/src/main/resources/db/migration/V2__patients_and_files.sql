-- V2: patients, medical history, files, consents.
CREATE TABLE patient (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinic(id),
  chart_no        TEXT NOT NULL,
  first_name      TEXT NOT NULL,
  last_name       TEXT NOT NULL,
  birth_date      DATE,
  sex             TEXT,
  phone           TEXT,
  email           TEXT,
  address         TEXT,
  guardian_id     UUID REFERENCES patient(id),
  emergency_name  TEXT,
  emergency_phone TEXT,
  sms_opt_in      BOOLEAN NOT NULL DEFAULT true,
  notes           TEXT,
  deleted_at      TIMESTAMPTZ,                       -- soft delete only, never hard delete
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  version         BIGINT NOT NULL DEFAULT 0,
  UNIQUE (clinic_id, chart_no)
);

CREATE TABLE patient_medical_history (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id     UUID NOT NULL REFERENCES clinic(id),
  patient_id    UUID NOT NULL REFERENCES patient(id),
  allergies     TEXT,
  conditions    TEXT,
  medications   TEXT,
  questionnaire JSONB,
  updated_by    UUID REFERENCES app_user(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0
);

-- visit_id FK is added in V4 once visit exists.
CREATE TABLE file_asset (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  patient_id  UUID REFERENCES patient(id),
  visit_id    UUID,
  kind        TEXT NOT NULL CHECK (kind IN ('XRAY','PHOTO','CONSENT','DOCUMENT','OTHER')),
  storage_key TEXT NOT NULL,
  file_name   TEXT NOT NULL,
  mime_type   TEXT,
  size_bytes  BIGINT,
  uploaded_by UUID REFERENCES app_user(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE consent_form (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id  UUID NOT NULL REFERENCES clinic(id),
  patient_id UUID NOT NULL REFERENCES patient(id),
  form_type  TEXT NOT NULL,
  signed_at  TIMESTAMPTZ,
  signed_by  TEXT,
  file_id    UUID REFERENCES file_asset(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0
);
