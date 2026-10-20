-- M8.10: appointment changes atomically invalidate stale unsent reminders.
-- PostgreSQL trigger is independent of which API/client modifies an appointment.
CREATE OR REPLACE FUNCTION suppress_stale_appointment_reminders()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.starts_at IS DISTINCT FROM OLD.starts_at OR NEW.status IS DISTINCT FROM OLD.status THEN
   UPDATE notification_delivery
   SET status='SUPPRESSED',suppressed_at=now(),lease_until=NULL,
       failure_reason='Appointment changed; schedule a new reminder'
   WHERE clinic_id=NEW.clinic_id AND appointment_id=NEW.id
     AND status IN ('PENDING','FAILED');
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER appointment_reminder_sync
 AFTER UPDATE OF starts_at,status ON appointment
 FOR EACH ROW EXECUTE FUNCTION suppress_stale_appointment_reminders();

-- Provider accepted != recipient delivered; callbacks must independently confirm delivery.
ALTER TABLE notification_delivery DROP CONSTRAINT notification_delivery_status_check;
ALTER TABLE notification_delivery ADD CONSTRAINT notification_delivery_status_check
 CHECK(status IN ('PENDING','CLAIMED','ACCEPTED','DELIVERED','FAILED','SUPPRESSED'));
