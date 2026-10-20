-- M4.4: enforce clinical correction provenance in SQL, not only in HTTP validation.
CREATE OR REPLACE FUNCTION odontogram_validate_supersession()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original_tooth smallint;
DECLARE original_surface text;
BEGIN
 IF NEW.supersedes_event_id IS NULL THEN
   RETURN NEW;
 END IF;
 SELECT tooth_number, surface INTO original_tooth, original_surface
 FROM odontogram_finding_event
 WHERE clinic_id = NEW.clinic_id
   AND patient_id = NEW.patient_id
   AND id = NEW.supersedes_event_id;
 IF NOT FOUND OR original_tooth <> NEW.tooth_number
      OR original_surface IS DISTINCT FROM NEW.surface THEN
   RAISE EXCEPTION 'Correction must target the original tooth and surface'
     USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER odontogram_same_tooth_correction
BEFORE INSERT ON odontogram_finding_event
FOR EACH ROW EXECUTE FUNCTION odontogram_validate_supersession();
