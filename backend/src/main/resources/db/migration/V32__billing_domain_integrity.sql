-- M7.1 — strengthen existing V5 financial domain without altering original migrations.
ALTER TABLE invoice ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP';
ALTER TABLE invoice ADD COLUMN plan_id UUID;
ALTER TABLE invoice ADD COLUMN voided_at TIMESTAMPTZ;
ALTER TABLE invoice ADD COLUMN void_reason TEXT;
ALTER TABLE invoice ADD CONSTRAINT fk_invoice_plan_tenant FOREIGN KEY(clinic_id,plan_id) REFERENCES treatment_plan(clinic_id,id);
ALTER TABLE invoice ADD CONSTRAINT chk_invoice_money CHECK(subtotal>=0 AND discount>=0 AND discount<=subtotal AND total=subtotal-discount);
ALTER TABLE invoice ADD CONSTRAINT chk_invoice_currency CHECK(currency_code ~ '^[A-Z]{3}$');
ALTER TABLE invoice ADD CONSTRAINT chk_invoice_void CHECK((status='VOID')=(voided_at IS NOT NULL));
ALTER TABLE invoice_line ADD CONSTRAINT chk_invoice_line_money CHECK(quantity>0 AND unit_price>=0 AND discount>=0 AND discount<=quantity*unit_price AND line_total=quantity*unit_price-discount);
CREATE UNIQUE INDEX uq_invoice_plan_item_billed ON invoice_line(clinic_id,plan_item_id) WHERE plan_item_id IS NOT NULL;
ALTER TABLE payment ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX uq_payment_idempotency ON payment(clinic_id,idempotency_key) WHERE idempotency_key IS NOT NULL;
ALTER TABLE payment ADD CONSTRAINT chk_payment_void_metadata CHECK((voided_at IS NULL AND void_reason IS NULL) OR (voided_at IS NOT NULL AND length(btrim(void_reason)) BETWEEN 3 AND 500));
ALTER TABLE payment_schedule ADD CONSTRAINT uq_payment_schedule_clinic_invoice_date UNIQUE(clinic_id,invoice_id,due_date);
CREATE TABLE billing_event(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 invoice_id UUID NOT NULL,
 actor_id UUID NOT NULL,
 event_type TEXT NOT NULL CHECK(event_type IN ('DRAFT_CREATED','LINE_ADDED','ISSUED','PAYMENT_RECORDED','PAYMENT_VOIDED','INVOICE_VOIDED','INSTALLMENT_ADDED')),
 reference_id UUID,
 reason TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(clinic_id,invoice_id) REFERENCES invoice(clinic_id,id),
 FOREIGN KEY(clinic_id,actor_id) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_billing_event_invoice ON billing_event(clinic_id,invoice_id,created_at,id);
CREATE TRIGGER billing_event_no_update BEFORE UPDATE OR DELETE ON billing_event FOR EACH ROW EXECUTE FUNCTION treatment_history_immutable();
ALTER TABLE billing_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing_event FORCE ROW LEVEL SECURITY;
CREATE POLICY billing_event_tenant ON billing_event
 USING(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
 GRANT SELECT,INSERT ON billing_event TO dental_app;
 END IF; END $$;
