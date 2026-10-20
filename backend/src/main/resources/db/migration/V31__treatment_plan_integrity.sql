-- M6: make treatment history patient attribution and treatment-item price locks database enforced.
ALTER TABLE treatment_plan_event
 ADD CONSTRAINT fk_treatment_event_plan_patient
 FOREIGN KEY(clinic_id,patient_id,plan_id)
 REFERENCES treatment_plan(clinic_id,patient_id,id);
CREATE OR REPLACE FUNCTION treatment_item_protect_accepted_price()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE plan_state TEXT;
BEGIN
 IF TG_OP='DELETE' THEN
   RAISE EXCEPTION 'Treatment items cannot be deleted; change status with clinical audit';
 END IF;
 SELECT status INTO plan_state FROM treatment_plan
  WHERE clinic_id=NEW.clinic_id AND id=NEW.plan_id FOR SHARE;
 IF plan_state<>'DRAFT' AND
    (NEW.procedure_id IS DISTINCT FROM OLD.procedure_id
      OR NEW.tooth_number IS DISTINCT FROM OLD.tooth_number
      OR NEW.surface IS DISTINCT FROM OLD.surface
      OR NEW.phase IS DISTINCT FROM OLD.phase
      OR NEW.quantity IS DISTINCT FROM OLD.quantity
      OR NEW.unit_price IS DISTINCT FROM OLD.unit_price
      OR NEW.discount IS DISTINCT FROM OLD.discount) THEN
   RAISE EXCEPTION 'Accepted treatment scope and prices are immutable; issue a new plan revision';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER treatment_plan_item_no_delete
 BEFORE DELETE ON treatment_plan_item FOR EACH ROW
 EXECUTE FUNCTION treatment_item_protect_accepted_price();
CREATE TRIGGER treatment_plan_item_price_lock
 BEFORE UPDATE ON treatment_plan_item FOR EACH ROW
 EXECUTE FUNCTION treatment_item_protect_accepted_price();
ALTER TABLE treatment_plan_item ADD CONSTRAINT chk_treatment_item_line_net
 CHECK (discount <= unit_price*quantity);
ALTER TABLE treatment_plan_item ADD CONSTRAINT chk_treatment_item_valid_fdi
 CHECK (tooth_number IS NULL OR odontogram_valid_fdi(tooth_number));
