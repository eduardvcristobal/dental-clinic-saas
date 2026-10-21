-- M10.6: tenant-leading indexes for bounded reporting and finance lookups.
CREATE INDEX idx_m10_invoice_issued ON invoice(clinic_id,issued_at,id) WHERE status NOT IN ('DRAFT','VOID');
CREATE INDEX idx_m10_payment_paid ON payment(clinic_id,paid_at,invoice_id) WHERE voided_at IS NULL;
CREATE INDEX idx_m10_refund_at ON payment_refund(clinic_id,refunded_at,invoice_id);
CREATE INDEX idx_m10_appointment_start ON appointment(clinic_id,starts_at,status);
CREATE INDEX idx_m10_patient_registered ON patient(clinic_id,created_at);
CREATE INDEX idx_m10_plan_item_completed ON treatment_plan_item(clinic_id,updated_at) WHERE status='COMPLETED';
CREATE INDEX idx_m10_visit_first_signed ON visit(clinic_id,patient_id,started_at) WHERE locked_at IS NOT NULL;
-- M10.7: enforce audit-log insert-only semantics, including application role.
CREATE OR REPLACE FUNCTION prevent_audit_log_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Audit entries are immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER m10_audit_immutable BEFORE UPDATE OR DELETE ON audit_log
 FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();
REVOKE UPDATE,DELETE ON audit_log FROM dental_app;
CREATE INDEX idx_m10_audit_recent ON audit_log(clinic_id,created_at DESC,id DESC);
