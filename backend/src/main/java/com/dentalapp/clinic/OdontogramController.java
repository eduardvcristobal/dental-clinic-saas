package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.odontogram.ToothNotation;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Clinical odontogram: clinic from authenticated principal; immutable history in PostgreSQL. */
@RestController
@RequestMapping("/api/odontogram")
public class OdontogramController {
    private final JdbcTemplate db;
    public OdontogramController(JdbcTemplate db) { this.db = db; }

    public record FindingInput(Integer toothNumber, String surface, String conditionCode,
                               String clinicalStatus, String notes, String eventKind, UUID supersedesEventId) {}

    private AuthPrincipal clinician(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)
                || !Set.of("OWNER", "ADMIN", "DENTIST").contains(p.role()))
            throw new AccessDeniedException("Clinical chart access requires an authorized clinician");
        return p;
    }
    private void activePatient(UUID clinic, UUID patient) {
        if (patient == null || !Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL)",
                Boolean.class, clinic, patient)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
    }
    private static String status(String value) {
        String s = value == null ? "EXISTING" : value;
        if (!Set.of("EXISTING", "PLANNED", "COMPLETED").contains(s))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid finding status");
        return s;
    }
    private static String kind(String value) {
        String s = value == null ? "OBSERVATION" : value;
        if (!Set.of("OBSERVATION", "CORRECTION", "RETRACTION").contains(s))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event kind");
        return s;
    }
    private void validate(FindingInput input) {
        if (input == null || input.toothNumber() == null || !ToothNotation.validFdi(input.toothNumber())
                || !ToothNotation.validSurface(input.toothNumber(), input.surface()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid FDI tooth or surface");
        if (input.conditionCode() == null || input.conditionCode().isBlank()
                || input.conditionCode().length() > 80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Condition code is required (max 80)");
        if (input.notes() != null && input.notes().length() > 10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notes exceed limit");
    }

    @GetMapping("/patients/{patientId}")
    @Transactional(readOnly = true)
    public Map<String,Object> chart(Authentication auth, @PathVariable UUID patientId) {
        UUID clinic = clinician(auth).clinicId();
        activePatient(clinic, patientId);
        List<Map<String,Object>> charts = db.queryForList(
            "SELECT id,created_at FROM odontogram_chart WHERE clinic_id=? AND patient_id=?",clinic,patientId);
        List<Map<String,Object>> findings = db.queryForList(
            "SELECT id,tooth_number,surface,condition_code,event_kind,clinical_status,notes,recorded_at,recorded_by,supersedes_event_id "+
            "FROM odontogram_finding_event WHERE clinic_id=? AND patient_id=? ORDER BY recorded_at,id",
            clinic,patientId);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("patientId",patientId);
        result.put("chartId",charts.isEmpty()?null:charts.get(0).get("id"));
        result.put("events",findings);
        return result;
    }

    @PostMapping("/patients/{patientId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,Object> record(Authentication auth, @PathVariable UUID patientId,
                                      @RequestBody FindingInput input) {
        AuthPrincipal p = clinician(auth);
        UUID clinic=p.clinicId();
        activePatient(clinic,patientId);
        validate(input);
        String k=kind(input.eventKind()), s=status(input.clinicalStatus());
        if (("OBSERVATION".equals(k)) != (input.supersedesEventId() == null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corrections and retractions require an original event");

        db.update("INSERT INTO odontogram_chart(clinic_id,patient_id,created_by) VALUES(?,?,?) "+
                  "ON CONFLICT(clinic_id,patient_id) DO NOTHING",clinic,patientId,p.userId());
        UUID chartId = db.queryForObject("SELECT id FROM odontogram_chart WHERE clinic_id=? AND patient_id=?",
                                       UUID.class,clinic,patientId);
        if (input.supersedesEventId()!=null) {
            List<Map<String,Object>> original=db.queryForList(
                "SELECT tooth_number,surface FROM odontogram_finding_event "+
                "WHERE clinic_id=? AND patient_id=? AND id=? FOR SHARE",
                clinic,patientId,input.supersedesEventId());
            if (original.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Original finding not found");
            Map<String,Object> o=original.get(0);
            if (((Number)o.get("tooth_number")).intValue()!=input.toothNumber()
               || !Objects.equals(o.get("surface"),input.surface()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Correction must reference the same tooth and surface");
            if (Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM odontogram_finding_event WHERE clinic_id=? AND patient_id=? AND supersedes_event_id=?)",
                Boolean.class,clinic,patientId,input.supersedesEventId())))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Finding already superseded");
        }
        UUID eventId=UUID.randomUUID();
        db.update("INSERT INTO odontogram_finding_event "+
            "(id,clinic_id,chart_id,patient_id,tooth_number,surface,condition_code,event_kind,clinical_status,notes,recorded_by,supersedes_event_id) "+
            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",eventId,clinic,chartId,patientId,input.toothNumber(),
            input.surface(),input.conditionCode().trim(),k,s,input.notes(),p.userId(),input.supersedesEventId());
        return db.queryForMap("SELECT id,tooth_number,surface,condition_code,event_kind,clinical_status,notes,recorded_at,recorded_by,supersedes_event_id "+
                              "FROM odontogram_finding_event WHERE clinic_id=? AND patient_id=? AND id=?",clinic,patientId,eventId);
    }
}
