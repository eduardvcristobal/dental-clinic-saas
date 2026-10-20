-- M7 financial closure: real refund disbursement is distinct from voiding a recorded payment.
CREATE TABLE payment_refund (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 invoice_id UUID NOT NULL,
 payment_id UUID NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0),
 method TEXT NOT NULL CHECK(method IN('CASH','CARD','BANK_TRANSFER','EWALLET','OTHER')),
 reference TEXT NOT NULL CHECK(length(btrim(reference)) BETWEEN 3 AND 255),
 reason TEXT NOT NULL CHECK(length(btrim(reason)) BETWEEN 3 AND 500),
 idempotency_key UUID NOT NULL,
 refunded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 recorded_by UUID NOT NULL,
 UNIQUE(clinic_id,idempotency_key),
 FOREIGN KEY(clinic_id,invoice_id) REFERENCES invoice(clinic_id,id),
 FOREIGN KEY(clinic_id,payment_id) REFERENCES payment(clinic_id,id),
 FOREIGN KEY(clinic_id,recorded_by) REFERENCES app_user(clinic_id,id)
);
CREATE INDEX idx_payment_refund_invoice ON payment_refund(clinic_id,invoice_id,refunded_at);
CREATE TRIGGER payment_refund_no_delete BEFORE DELETE ON payment_refund FOR EACH ROW EXECUTE FUNCTION forbid_delete();
CREATE FUNCTION payment_refund_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Refund records are immutable; reconcile with a separate adjustment'; END $$;
CREATE TRIGGER payment_refund_no_update BEFORE UPDATE ON payment_refund FOR EACH ROW EXECUTE FUNCTION payment_refund_immutable();
ALTER TABLE payment_refund ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_refund FORCE ROW LEVEL SECURITY;
CREATE POLICY payment_refund_tenant ON payment_refund
 USING(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid)
 WITH CHECK(clinic_id=nullif(current_setting('app.clinic_id',true),'')::uuid);
ALTER TABLE billing_event DROP CONSTRAINT billing_event_event_type_check;
ALTER TABLE billing_event ADD CONSTRAINT billing_event_event_type_check CHECK(event_type IN
 ('DRAFT_CREATED','LINE_ADDED','ISSUED','PAYMENT_RECORDED','PAYMENT_VOIDED','INVOICE_VOIDED','INSTALLMENT_ADDED','REFUND_RECORDED','RECONCILIATION_RECORDED'));
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN
 GRANT SELECT,INSERT ON payment_refund TO dental_app;
 END IF; END $$;
