-- V4: procedure catalog, visits, treatment plans, tooth chart, prescriptions.
CREATE TABLE procedure_catalog (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id        UUID NOT NULL REFERENCES clinic(id),
  code             TEXT NOT NULL,
  name             TEXT NOT NULL,
  category         TEXT,
  default_price    NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (default_price >= 0),
  requires_tooth   BOOLEAN NOT NULL DEFAULT false,
  requires_surface BOOLEAN NOT NULL DEFAULT false,
  active           BOOLEAN NOT NULL DEFAULT true,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  version          BIGINT NOT NULL DEFAULT 0,
  UNIQUE (clinic_id, code)
);

CREATE TABLE visit (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id      UUID NOT NULL REFERENCES clinic(id),
  appointment_id UUID REFERENCES appointment(id),
  patient_id     UUID NOT NULL REFERENCES patient(id),
  dentist_id     UUID NOT NULL REFERENCES dentist(id),
  started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at       TIMESTAMPTZ,
  subjective     TEXT,
  objective      TEXT,
  assessment     TEXT,
  plan_notes     TEXT,
  locked_at      TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  version        BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE file_asset ADD CONSTRAINT fk_file_asset_visit FOREIGN KEY (visit_id) REFERENCES visit(id);

CREATE TABLE treatment_plan (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  patient_id  UUID NOT NULL REFERENCES patient(id),
  dentist_id  UUID NOT NULL REFERENCES dentist(id),
  title       TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'DRAFT'
              CHECK (status IN ('DRAFT','PROPOSED','ACCEPTED','IN_PROGRESS','COMPLETED','DECLINED')),
  accepted_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE treatment_plan_item (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id          UUID NOT NULL REFERENCES clinic(id),
  plan_id            UUID NOT NULL REFERENCES treatment_plan(id),
  procedure_id       UUID NOT NULL REFERENCES procedure_catalog(id),
  tooth_number       SMALLINT CHECK (tooth_number BETWEEN 11 AND 85),
  surface            TEXT,
  phase              SMALLINT NOT NULL DEFAULT 1,
  quantity           INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit_price         NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
  discount           NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
  status             TEXT NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','SCHEDULED','COMPLETED','CANCELLED')),
  completed_visit_id UUID REFERENCES visit(id),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE visit_procedure (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id    UUID NOT NULL REFERENCES clinic(id),
  visit_id     UUID NOT NULL REFERENCES visit(id),
  plan_item_id UUID REFERENCES treatment_plan_item(id),
  procedure_id UUID NOT NULL REFERENCES procedure_catalog(id),
  tooth_number SMALLINT CHECK (tooth_number BETWEEN 11 AND 85),
  surface      TEXT,
  price        NUMERIC(12,2) NOT NULL CHECK (price >= 0),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

-- FDI numbering: 11-48 permanent, 51-85 primary. One row per finding/treatment; drives the odontogram.
CREATE TABLE tooth_record (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id      UUID NOT NULL REFERENCES clinic(id),
  patient_id     UUID NOT NULL REFERENCES patient(id),
  tooth_number   SMALLINT NOT NULL CHECK (tooth_number BETWEEN 11 AND 85),
  surface        TEXT,
  condition_code TEXT NOT NULL,
  status         TEXT NOT NULL DEFAULT 'EXISTING' CHECK (status IN ('EXISTING','PLANNED','COMPLETED')),
  visit_id       UUID REFERENCES visit(id),
  notes          TEXT,
  recorded_by    UUID REFERENCES app_user(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE prescription (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id    UUID NOT NULL REFERENCES clinic(id),
  visit_id     UUID NOT NULL REFERENCES visit(id),
  medication   TEXT NOT NULL,
  dosage       TEXT,
  instructions TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);
