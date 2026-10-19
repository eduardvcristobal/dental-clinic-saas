-- Dental Clinic SaaS: PostgreSQL schema (MVP)
-- Multi-tenant: every business table carries clinic_id.
-- Tooth numbering uses FDI notation (11-48 permanent, 51-85 primary).
-- Money is NUMERIC(12,2). Payments are an append-only ledger (void, never edit/delete).

CREATE EXTENSION IF NOT EXISTS btree_gist;  -- for overlap-prevention constraints

-- ---------- ENUMS ----------
CREATE TYPE user_role          AS ENUM ('owner', 'admin', 'dentist', 'front_desk');
CREATE TYPE appointment_status AS ENUM ('booked', 'confirmed', 'arrived', 'in_chair', 'completed', 'cancelled', 'no_show');
CREATE TYPE tooth_status       AS ENUM ('existing', 'planned', 'completed');
CREATE TYPE plan_status        AS ENUM ('draft', 'proposed', 'accepted', 'in_progress', 'completed', 'declined');
CREATE TYPE plan_item_status   AS ENUM ('planned', 'scheduled', 'completed', 'cancelled');
CREATE TYPE invoice_status     AS ENUM ('draft', 'issued', 'partially_paid', 'paid', 'void');
CREATE TYPE payment_method     AS ENUM ('cash', 'card', 'bank_transfer', 'ewallet', 'hmo', 'other');
CREATE TYPE reminder_channel   AS ENUM ('sms', 'email');
CREATE TYPE reminder_status    AS ENUM ('scheduled', 'sent', 'failed', 'replied');
CREATE TYPE file_kind          AS ENUM ('xray', 'photo', 'consent', 'document', 'other');

-- ---------- TENANCY ----------
CREATE TABLE clinic (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  timezone    TEXT NOT NULL DEFAULT 'Asia/Manila',
  currency    CHAR(3) NOT NULL DEFAULT 'PHP',
  phone       TEXT,
  email       TEXT,
  address     TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE branch (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  name        TEXT NOT NULL,
  address     TEXT,
  phone       TEXT,
  active      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE chair (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id   UUID NOT NULL REFERENCES branch(id),
  name        TEXT NOT NULL,
  active      BOOLEAN NOT NULL DEFAULT true
);

-- ---------- USERS & STAFF ----------
CREATE TABLE app_user (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id     UUID NOT NULL REFERENCES clinic(id),
  email         TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  full_name     TEXT NOT NULL,
  role          user_role NOT NULL,
  active        BOOLEAN NOT NULL DEFAULT true,
  last_login_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (clinic_id, email)
);

CREATE TABLE user_branch (
  user_id   UUID NOT NULL REFERENCES app_user(id),
  branch_id UUID NOT NULL REFERENCES branch(id),
  PRIMARY KEY (user_id, branch_id)
);

CREATE TABLE dentist (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  user_id     UUID REFERENCES app_user(id),
  full_name   TEXT NOT NULL,
  license_no  TEXT,
  specialty   TEXT,
  calendar_color TEXT,
  active      BOOLEAN NOT NULL DEFAULT true
);

-- ---------- PATIENTS ----------
CREATE TABLE patient (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id          UUID NOT NULL REFERENCES clinic(id),
  chart_no           TEXT NOT NULL,
  first_name         TEXT NOT NULL,
  last_name          TEXT NOT NULL,
  birth_date         DATE,
  sex                TEXT,
  phone              TEXT,
  email              TEXT,
  address            TEXT,
  guardian_id        UUID REFERENCES patient(id),   -- parent/guardian for minors
  emergency_name     TEXT,
  emergency_phone    TEXT,
  sms_opt_in         BOOLEAN NOT NULL DEFAULT true,
  notes              TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at         TIMESTAMPTZ,                   -- soft delete only
  UNIQUE (clinic_id, chart_no)
);

CREATE TABLE patient_medical_history (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id     UUID NOT NULL REFERENCES patient(id),
  allergies      TEXT,
  conditions     TEXT,
  medications    TEXT,
  questionnaire  JSONB,                             -- flexible yes/no health form
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     UUID REFERENCES app_user(id)
);

-- ---------- FILES ----------
CREATE TABLE file_asset (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id    UUID NOT NULL REFERENCES clinic(id),
  patient_id   UUID REFERENCES patient(id),
  visit_id     UUID,                                -- FK added after visit table
  kind         file_kind NOT NULL,
  storage_key  TEXT NOT NULL,                       -- key in S3-compatible storage
  file_name    TEXT NOT NULL,
  mime_type    TEXT,
  size_bytes   BIGINT,
  uploaded_by  UUID REFERENCES app_user(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consent_form (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id   UUID NOT NULL REFERENCES patient(id),
  form_type    TEXT NOT NULL,                       -- e.g. treatment, privacy, photo
  signed_at    TIMESTAMPTZ,
  signed_by    TEXT,
  file_id      UUID REFERENCES file_asset(id)
);

-- ---------- SCHEDULING ----------
CREATE TABLE appointment_type (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id        UUID NOT NULL REFERENCES clinic(id),
  name             TEXT NOT NULL,
  default_minutes  INT NOT NULL DEFAULT 30,
  color            TEXT,
  active           BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE appointment (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id           UUID NOT NULL REFERENCES clinic(id),
  branch_id           UUID NOT NULL REFERENCES branch(id),
  patient_id          UUID NOT NULL REFERENCES patient(id),
  dentist_id          UUID NOT NULL REFERENCES dentist(id),
  chair_id            UUID REFERENCES chair(id),
  appointment_type_id UUID REFERENCES appointment_type(id),
  starts_at           TIMESTAMPTZ NOT NULL,
  ends_at             TIMESTAMPTZ NOT NULL,
  status              appointment_status NOT NULL DEFAULT 'booked',
  reason              TEXT,
  notes               TEXT,
  created_by          UUID REFERENCES app_user(id),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at),
  -- no double-booking a dentist or a chair (ignoring cancelled / no-show)
  EXCLUDE USING gist (dentist_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (status NOT IN ('cancelled', 'no_show')),
  EXCLUDE USING gist (chair_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (chair_id IS NOT NULL AND status NOT IN ('cancelled', 'no_show'))
);

CREATE TABLE reminder (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinic(id),
  appointment_id  UUID NOT NULL REFERENCES appointment(id),
  channel         reminder_channel NOT NULL,
  scheduled_for   TIMESTAMPTZ NOT NULL,
  sent_at         TIMESTAMPTZ,
  status          reminder_status NOT NULL DEFAULT 'scheduled',
  patient_reply   TEXT
);

CREATE TABLE recall (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  patient_id  UUID NOT NULL REFERENCES patient(id),
  due_date    DATE NOT NULL,
  reason      TEXT DEFAULT 'Routine checkup',
  contacted_at TIMESTAMPTZ,
  completed   BOOLEAN NOT NULL DEFAULT false
);

-- ---------- CLINICAL ----------
CREATE TABLE procedure_catalog (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id        UUID NOT NULL REFERENCES clinic(id),
  code             TEXT NOT NULL,
  name             TEXT NOT NULL,
  category         TEXT,                            -- preventive, restorative, surgery...
  default_price    NUMERIC(12,2) NOT NULL DEFAULT 0,
  requires_tooth   BOOLEAN NOT NULL DEFAULT false,
  requires_surface BOOLEAN NOT NULL DEFAULT false,
  active           BOOLEAN NOT NULL DEFAULT true,
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
  subjective     TEXT,   -- SOAP: complaint
  objective      TEXT,   -- findings
  assessment     TEXT,   -- diagnosis
  plan_notes     TEXT,   -- next steps
  locked_at      TIMESTAMPTZ  -- notes become read-only after sign-off
);

ALTER TABLE file_asset
  ADD CONSTRAINT fk_file_visit FOREIGN KEY (visit_id) REFERENCES visit(id);

CREATE TABLE treatment_plan (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id    UUID NOT NULL REFERENCES clinic(id),
  patient_id   UUID NOT NULL REFERENCES patient(id),
  dentist_id   UUID NOT NULL REFERENCES dentist(id),
  title        TEXT NOT NULL,
  status       plan_status NOT NULL DEFAULT 'draft',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  accepted_at  TIMESTAMPTZ
);

CREATE TABLE treatment_plan_item (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id             UUID NOT NULL REFERENCES treatment_plan(id),
  procedure_id        UUID NOT NULL REFERENCES procedure_catalog(id),
  tooth_number        SMALLINT,
  surface             TEXT,                         -- M, O, D, B, L (combinations allowed)
  phase               SMALLINT NOT NULL DEFAULT 1,
  quantity            INT NOT NULL DEFAULT 1,
  unit_price          NUMERIC(12,2) NOT NULL,
  discount            NUMERIC(12,2) NOT NULL DEFAULT 0,
  status              plan_item_status NOT NULL DEFAULT 'planned',
  completed_visit_id  UUID REFERENCES visit(id)
);

CREATE TABLE visit_procedure (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  visit_id      UUID NOT NULL REFERENCES visit(id),
  plan_item_id  UUID REFERENCES treatment_plan_item(id),
  procedure_id  UUID NOT NULL REFERENCES procedure_catalog(id),
  tooth_number  SMALLINT,
  surface       TEXT,
  price         NUMERIC(12,2) NOT NULL
);

-- One row per tooth/surface finding or treatment: this drives the odontogram.
CREATE TABLE tooth_record (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id     UUID NOT NULL REFERENCES patient(id),
  tooth_number   SMALLINT NOT NULL,
  surface        TEXT,
  condition_code TEXT NOT NULL,                     -- caries, filling, crown, missing, implant...
  status         tooth_status NOT NULL DEFAULT 'existing',
  visit_id       UUID REFERENCES visit(id),
  notes          TEXT,
  recorded_by    UUID REFERENCES app_user(id),
  recorded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (tooth_number BETWEEN 11 AND 85)
);

CREATE TABLE prescription (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  visit_id      UUID NOT NULL REFERENCES visit(id),
  medication    TEXT NOT NULL,
  dosage        TEXT,
  instructions  TEXT
);

-- ---------- BILLING ----------
CREATE TABLE invoice (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  branch_id   UUID NOT NULL REFERENCES branch(id),
  patient_id  UUID NOT NULL REFERENCES patient(id),
  visit_id    UUID REFERENCES visit(id),
  invoice_no  TEXT NOT NULL,
  status      invoice_status NOT NULL DEFAULT 'draft',
  issued_at   TIMESTAMPTZ,
  subtotal    NUMERIC(12,2) NOT NULL DEFAULT 0,
  discount    NUMERIC(12,2) NOT NULL DEFAULT 0,
  total       NUMERIC(12,2) NOT NULL DEFAULT 0,
  notes       TEXT,
  created_by  UUID REFERENCES app_user(id),
  UNIQUE (clinic_id, invoice_no)
);

CREATE TABLE invoice_line (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id    UUID NOT NULL REFERENCES invoice(id),
  procedure_id  UUID REFERENCES procedure_catalog(id),
  plan_item_id  UUID REFERENCES treatment_plan_item(id),
  description   TEXT NOT NULL,
  quantity      INT NOT NULL DEFAULT 1,
  unit_price    NUMERIC(12,2) NOT NULL,
  discount      NUMERIC(12,2) NOT NULL DEFAULT 0,
  line_total    NUMERIC(12,2) NOT NULL
);

-- Append-only ledger: corrections are voids, never UPDATE/DELETE of amounts.
CREATE TABLE payment (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  invoice_id  UUID NOT NULL REFERENCES invoice(id),
  patient_id  UUID NOT NULL REFERENCES patient(id),
  amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  method      payment_method NOT NULL,
  reference   TEXT,
  paid_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  received_by UUID REFERENCES app_user(id),
  voided_at   TIMESTAMPTZ,
  void_reason TEXT
);

CREATE TABLE payment_schedule (                      -- installments
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id  UUID NOT NULL REFERENCES invoice(id),
  due_date    DATE NOT NULL,
  amount      NUMERIC(12,2) NOT NULL,
  paid        BOOLEAN NOT NULL DEFAULT false
);

-- ---------- AUDIT ----------
CREATE TABLE audit_log (
  id          BIGSERIAL PRIMARY KEY,
  clinic_id   UUID NOT NULL,
  user_id     UUID,
  action      TEXT NOT NULL,                        -- view, create, update, void, login...
  entity      TEXT NOT NULL,
  entity_id   UUID,
  details     JSONB,
  ip_address  INET,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- INDEXES ----------
CREATE INDEX idx_patient_name       ON patient (clinic_id, last_name, first_name);
CREATE INDEX idx_patient_phone      ON patient (clinic_id, phone);
CREATE INDEX idx_appt_calendar      ON appointment (clinic_id, branch_id, starts_at);
CREATE INDEX idx_appt_patient       ON appointment (patient_id, starts_at DESC);
CREATE INDEX idx_tooth_patient      ON tooth_record (patient_id, tooth_number);
CREATE INDEX idx_visit_patient      ON visit (patient_id, started_at DESC);
CREATE INDEX idx_invoice_patient    ON invoice (patient_id, status);
CREATE INDEX idx_payment_invoice    ON payment (invoice_id);
CREATE INDEX idx_reminder_due       ON reminder (scheduled_for) WHERE status = 'scheduled';
CREATE INDEX idx_recall_due         ON recall (clinic_id, due_date) WHERE completed = false;
CREATE INDEX idx_audit_entity       ON audit_log (clinic_id, entity, entity_id);

-- ---------- ROW-LEVEL SECURITY (backstop for tenant isolation) ----------
-- Enable per table, e.g.:
--   ALTER TABLE patient ENABLE ROW LEVEL SECURITY;
--   CREATE POLICY tenant_isolation ON patient
--     USING (clinic_id = current_setting('app.clinic_id')::uuid);
-- Set app.clinic_id per request/connection from the authenticated user's clinic.
-- Tables without clinic_id (e.g. payment_schedule, prescription) are reached via their parent.
