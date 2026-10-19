-- V5: invoices, payments (append-only ledger), installments.
CREATE TABLE invoice (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id  UUID NOT NULL REFERENCES clinic(id),
  branch_id  UUID NOT NULL REFERENCES branch(id),
  patient_id UUID NOT NULL REFERENCES patient(id),
  visit_id   UUID REFERENCES visit(id),
  invoice_no TEXT NOT NULL,
  status     TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOID')),
  issued_at  TIMESTAMPTZ,
  subtotal   NUMERIC(12,2) NOT NULL DEFAULT 0,
  discount   NUMERIC(12,2) NOT NULL DEFAULT 0,
  total      NUMERIC(12,2) NOT NULL DEFAULT 0,
  notes      TEXT,
  created_by UUID REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0,
  UNIQUE (clinic_id, invoice_no)
);

CREATE TABLE invoice_line (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id    UUID NOT NULL REFERENCES clinic(id),
  invoice_id   UUID NOT NULL REFERENCES invoice(id),
  procedure_id UUID REFERENCES procedure_catalog(id),
  plan_item_id UUID REFERENCES treatment_plan_item(id),
  description  TEXT NOT NULL,
  quantity     INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit_price   NUMERIC(12,2) NOT NULL,
  discount     NUMERIC(12,2) NOT NULL DEFAULT 0,
  line_total   NUMERIC(12,2) NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

-- Amounts are never edited or deleted; corrections are voids (voided_at + reason).
CREATE TABLE payment (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id   UUID NOT NULL REFERENCES clinic(id),
  invoice_id  UUID NOT NULL REFERENCES invoice(id),
  patient_id  UUID NOT NULL REFERENCES patient(id),
  amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  method      TEXT NOT NULL CHECK (method IN ('CASH','CARD','BANK_TRANSFER','EWALLET','HMO','OTHER')),
  reference   TEXT,
  paid_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  received_by UUID REFERENCES app_user(id),
  voided_at   TIMESTAMPTZ,
  void_reason TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE payment_schedule (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id  UUID NOT NULL REFERENCES clinic(id),
  invoice_id UUID NOT NULL REFERENCES invoice(id),
  due_date   DATE NOT NULL,
  amount     NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  paid       BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0
);

-- Block hard deletes of money records at the database level.
CREATE FUNCTION forbid_delete() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'Deleting from % is not allowed; void instead', TG_TABLE_NAME; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER payment_no_delete BEFORE DELETE ON payment FOR EACH ROW EXECUTE FUNCTION forbid_delete();
CREATE TRIGGER invoice_no_delete BEFORE DELETE ON invoice FOR EACH ROW EXECUTE FUNCTION forbid_delete();
