package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Live clinic-scoped data for the workspace command center. */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceDashboardController {
    private final JdbcTemplate db;

    public WorkspaceDashboardController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/dashboard") @Transactional(readOnly = true)
    public Map<String,Object> dashboard(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal user))
            throw new AccessDeniedException("Clinic staff authentication required");
        var clinic = db.queryForMap("SELECT timezone FROM clinic WHERE id=?", user.clinicId());
        ZoneId zone;
        try { zone = ZoneId.of((String) clinic.get("timezone")); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Clinic timezone is invalid"); }
        LocalDate today = LocalDate.now(zone);
        Timestamp from = Timestamp.from(today.atStartOfDay(zone).toInstant());
        Timestamp to = Timestamp.from(today.plusDays(1).atStartOfDay(zone).toInstant());

        Map<String,Object> counts = db.queryForMap("""
            SELECT
              (SELECT count(*) FROM appointment WHERE clinic_id=? AND starts_at>=? AND starts_at<?) AS appointments_today,
              (SELECT count(*) FROM patient WHERE clinic_id=? AND deleted_at IS NULL) AS patients_registered,
              (SELECT count(*) FROM appointment WHERE clinic_id=? AND starts_at>=? AND starts_at<? AND status='BOOKED') AS appointments_awaiting_confirmation,
              (SELECT count(*) FROM appointment WHERE clinic_id=? AND starts_at>=? AND starts_at<? AND status='ARRIVED') AS patients_waiting,
              (SELECT count(*) FROM recall WHERE clinic_id=? AND completed=false AND due_date<=?) AS recalls_due,
              (SELECT count(*) FROM chair WHERE clinic_id=? AND active=true) AS active_chairs
            """, user.clinicId(), from, to, user.clinicId(), user.clinicId(), from, to,
            user.clinicId(), from, to, user.clinicId(), today, user.clinicId());

        List<Map<String,Object>> schedule = db.queryForList("""
            SELECT a.id, a.starts_at, a.status, p.first_name, p.last_name,
                   coalesce(t.name, a.reason, 'Appointment') AS activity,
                   d.full_name AS dentist_name, coalesce(ch.name, 'Unassigned') AS chair_name
            FROM appointment a
            JOIN patient p ON p.id=a.patient_id AND p.clinic_id=a.clinic_id
            JOIN dentist d ON d.id=a.dentist_id AND d.clinic_id=a.clinic_id
            LEFT JOIN appointment_type t ON t.id=a.appointment_type_id AND t.clinic_id=a.clinic_id
            LEFT JOIN chair ch ON ch.id=a.chair_id AND ch.clinic_id=a.clinic_id
            WHERE a.clinic_id=? AND a.starts_at>=? AND a.starts_at<?
            ORDER BY a.starts_at, a.id LIMIT 50
            """, user.clinicId(), from, to);

        Map<String,Object> result = new LinkedHashMap<>(counts);
        result.put("clinicDate", today.toString());
        result.put("schedule", schedule);
        return result;
    }
}
