package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Live clinic-scoped data for the workspace command center. */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceDashboardController {
    private final JdbcTemplate db;

    public WorkspaceDashboardController(JdbcTemplate db) { this.db = db; }

    private ZoneId clinicZone(UUID clinicId) {
        var clinic = db.queryForMap("SELECT timezone FROM clinic WHERE id=?", clinicId);
        try { return ZoneId.of((String) clinic.get("timezone")); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Clinic timezone is invalid"); }
    }

    @GetMapping("/dashboard") @Transactional(readOnly = true)
    public Map<String,Object> dashboard(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal user))
            throw new AccessDeniedException("Clinic staff authentication required");
        ZoneId zone = clinicZone(user.clinicId());
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

    /**
     * Whole-day resource timeline for the command center: branch opening hours, resource lanes
     * (chairs and providers) and every appointment overlapping the requested clinic date.
     * Appointments are matched on overlap rather than start time so a visit that began before
     * midnight still appears on the board.
     */
    @GetMapping("/day-board") @Transactional(readOnly = true)
    public Map<String,Object> dayBoard(Authentication authentication,
                                       @RequestParam(required = false) String date,
                                       @RequestParam(required = false) UUID branchId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal user))
            throw new AccessDeniedException("Clinic staff authentication required");
        UUID clinic = user.clinicId();
        ZoneId zone = clinicZone(clinic);
        LocalDate day = LocalDate.now(zone);
        if (date != null && !date.isBlank()) {
            try { day = LocalDate.parse(date); }
            catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid board date"); }
        }
        Timestamp from = Timestamp.from(day.atStartOfDay(zone).toInstant());
        Timestamp to = Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant());
        int weekday = day.getDayOfWeek().getValue();

        List<Object> branchArgs = new ArrayList<>();
        branchArgs.add(weekday);
        branchArgs.add(clinic);
        String branchClause = "";
        if (branchId != null) { branchClause = " AND b.id=?"; branchArgs.add(branchId); }
        List<Map<String,Object>> branches = db.queryForList("""
            SELECT b.id, b.name, boh.opens_at, boh.closes_at
            FROM branch b
            LEFT JOIN branch_operating_hour boh
              ON boh.branch_id=b.id AND boh.clinic_id=b.clinic_id AND boh.day_of_week=?
            WHERE b.clinic_id=? AND b.active=true
            """ + branchClause + " ORDER BY b.name", branchArgs.toArray());

        List<Object> laneArgs = new ArrayList<>();
        laneArgs.add(clinic);
        String laneClause = "";
        if (branchId != null) { laneClause = " AND branch_id=?"; laneArgs.add(branchId); }
        List<Map<String,Object>> lanes = new ArrayList<>(db.queryForList("""
            SELECT id, 'CHAIR' AS kind, name, branch_id, NULL::text AS color
            FROM chair WHERE clinic_id=? AND active=true
            """ + laneClause + " ORDER BY name", laneArgs.toArray()));
        lanes.addAll(db.queryForList("""
            SELECT id, 'DENTIST' AS kind, full_name AS name, NULL::uuid AS branch_id, calendar_color AS color
            FROM dentist WHERE clinic_id=? AND active=true ORDER BY full_name
            """, clinic));

        List<Object> blockArgs = new ArrayList<>();
        blockArgs.add(clinic);
        blockArgs.add(to);
        blockArgs.add(from);
        String blockClause = "";
        if (branchId != null) { blockClause = " AND a.branch_id=?"; blockArgs.add(branchId); }
        List<Map<String,Object>> blocks = db.queryForList("""
            SELECT a.id, a.starts_at, a.ends_at, a.status, a.branch_id, a.reason,
                   p.first_name || ' ' || p.last_name AS patient_name,
                   coalesce(t.name, a.reason, 'Appointment') AS activity,
                   t.color AS type_color,
                   a.dentist_id, d.full_name AS dentist_name, d.calendar_color AS dentist_color,
                   a.chair_id, coalesce(ch.name, '') AS chair_name
            FROM appointment a
            JOIN patient p ON p.id=a.patient_id AND p.clinic_id=a.clinic_id
            JOIN dentist d ON d.id=a.dentist_id AND d.clinic_id=a.clinic_id
            LEFT JOIN appointment_type t ON t.id=a.appointment_type_id AND t.clinic_id=a.clinic_id
            LEFT JOIN chair ch ON ch.id=a.chair_id AND ch.clinic_id=a.clinic_id
            WHERE a.clinic_id=? AND a.starts_at<? AND a.ends_at>?
            """ + blockClause + " ORDER BY a.starts_at, a.id", blockArgs.toArray());

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("date", day.toString());
        result.put("timezone", zone.getId());
        result.put("branches", branches);
        result.put("lanes", lanes);
        result.put("blocks", blocks);
        return result;
    }
}
