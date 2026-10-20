-- M8.10.20: invalidate a claimed reminder on appointment edits or consent withdrawal.
-- This protects the period between queue claim and provider preflight. A true in-flight
-- HTTP call still requires send fencing before production activation.
CREATE OR REPLACE FUNCTION suppress_stale_appointment_reminders()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.starts_at IS DISTINCT FROM OLD.starts_at OR NEW.status IS DISTINCT FROM OLD.status THEN
   UPDATE notification_delivery
   SET status='SUPPRESSED',suppressed_at=now(),lease_until=NULL,
       failure_reason='Appointment changed; schedule a new reminder'
   WHERE clinic_id=NEW.clinic_id AND appointment_id=NEW.id
     AND status IN ('PENDING','FAILED','CLAIMED');
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION suppress_withdrawn_notification_consent()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.consented AND NOT NEW.consented THEN
   UPDATE notification_delivery
   SET status='SUPPRESSED',suppressed_at=now(),lease_until=NULL,
       failure_reason='Consent withdrawn'
   WHERE clinic_id=NEW.clinic_id AND patient_id=NEW.patient_id
      AND channel=NEW.channel AND status IN ('PENDING','FAILED','CLAIMED');
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER notification_consent_withdrawal_sync
 AFTER UPDATE OF consented ON notification_consent
 FOR EACH ROW EXECUTE FUNCTION suppress_withdrawn_notification_consent();
