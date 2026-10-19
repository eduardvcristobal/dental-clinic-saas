package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** M1 management API. Tenant is always extracted from authenticated principal, never request payload. */
@RestController
@RequestMapping("/api/clinic-management")
public class ClinicManagementController {
    private final JdbcTemplate db;
    public ClinicManagementController(JdbcTemplate db) { this.db = db; }

    private AuthPrincipal user(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) throw new AccessDeniedException("Clinic staff authentication required");
        return p;
    }
    private AuthPrincipal manager(Authentication auth) {
        AuthPrincipal p = user(auth);
        if (!List.of("OWNER", "ADMIN").contains(p.role())) throw new AccessDeniedException("Clinic management requires OWNER or ADMIN");
        return p;
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        return value.strip();
    }
    private static String optional(String value, int max) {
        if (value != null && value.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        return value == null ? null : value.strip();
    }
    private static void updated(int count) {
        if (count != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found in your clinic");
    }
    private void ownBranch(UUID clinic, UUID branch) {
        Integer count = db.queryForObject("SELECT count(*) FROM branch WHERE id=? AND clinic_id=?", Integer.class, branch, clinic);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
    }
    private void ownChair(UUID clinic, UUID branch, UUID chair) {
        Integer count = db.queryForObject("SELECT count(*) FROM chair WHERE id=? AND branch_id=? AND clinic_id=?", Integer.class, chair, branch, clinic);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chair not found");
    }

    public record ClinicUpdate(String name, String timezone, String phone, String email, String address) {}
    public record BranchInput(String name, String address, String phone) {}
    public record ChairInput(String name) {}
    public record HoursInput(int dayOfWeek, String opensAt, String closesAt) {}
    public record AssignmentInput(UUID userId) {}

    @GetMapping("/clinic") @Transactional(readOnly = true)
    public Map<String,Object> clinic(Authentication auth) {
        AuthPrincipal p = user(auth);
        return db.queryForMap("SELECT id,slug,name,timezone,currency,phone,email,address,active FROM clinic WHERE id=?", p.clinicId());
    }
    @PutMapping("/clinic") @Transactional
    public Map<String,Object> updateClinic(Authentication auth, @RequestBody ClinicUpdate input) {
        AuthPrincipal p = manager(auth);
        String timezone = required(input.timezone(), "timezone", 80);
        try { ZoneId.of(timezone); } catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IANA timezone"); }
        updated(db.update("UPDATE clinic SET name=?,timezone=?,phone=?,email=?,address=?,updated_at=now(),version=version+1 WHERE id=?",
            required(input.name(),"clinic name",160),timezone,optional(input.phone(),80),optional(input.email(),254),optional(input.address(),2000),p.clinicId()));
        return clinic(auth);
    }
    @GetMapping("/branches") @Transactional(readOnly = true)
    public List<Map<String,Object>> branches(Authentication auth) {
        UUID clinic = user(auth).clinicId();
        return db.queryForList("SELECT id,name,address,phone,active FROM branch WHERE clinic_id=? ORDER BY name", clinic);
    }
    @PostMapping("/branches") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> createBranch(Authentication auth, @RequestBody BranchInput input) {
        UUID clinic = manager(auth).clinicId();
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO branch(id,clinic_id,name,address,phone) VALUES (?,?,?,?,?)",id,clinic,required(input.name(),"branch name",160), optional(input.address(),2000),optional(input.phone(),80));
        return db.queryForMap("SELECT id,name,address,phone,active FROM branch WHERE id=? AND clinic_id=?",id,clinic);
    }
    @PutMapping("/branches/{branchId}") @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateBranch(Authentication auth, @PathVariable UUID branchId, @RequestBody BranchInput input) {
        UUID clinic = manager(auth).clinicId();
        updated(db.update("UPDATE branch SET name=?,address=?,phone=?,updated_at=now(),version=version+1 WHERE id=? AND clinic_id=?", required(input.name(),"branch name",160),optional(input.address(),2000),optional(input.phone(),80),branchId,clinic));
    }
    @PatchMapping("/branches/{branchId}/active") @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void branchActive(Authentication auth, @PathVariable UUID branchId, @RequestParam boolean active) {
        UUID clinic = manager(auth).clinicId();
        updated(db.update("UPDATE branch SET active=?,updated_at=now(),version=version+1 WHERE id=? AND clinic_id=?",active,branchId,clinic));
    }
    @GetMapping("/branches/{branchId}/chairs") @Transactional(readOnly = true)
    public List<Map<String,Object>> chairs(Authentication auth,@PathVariable UUID branchId) {
        UUID clinic = user(auth).clinicId(); ownBranch(clinic,branchId);
        return db.queryForList("SELECT id,name,active FROM chair WHERE clinic_id=? AND branch_id=? ORDER BY name",clinic,branchId);
    }
    @PostMapping("/branches/{branchId}/chairs") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> createChair(Authentication auth,@PathVariable UUID branchId,@RequestBody ChairInput input) {
        UUID clinic = manager(auth).clinicId(); ownBranch(clinic,branchId);
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO chair(id,clinic_id,branch_id,name) VALUES (?,?,?,?)",id,clinic,branchId,required(input.name(),"chair name",120));
        return db.queryForMap("SELECT id,name,active FROM chair WHERE id=? AND clinic_id=?",id,clinic);
    }
    @PutMapping("/branches/{branchId}/chairs/{chairId}") @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateChair(Authentication auth,@PathVariable UUID branchId,@PathVariable UUID chairId,@RequestBody ChairInput input) {
        UUID clinic=manager(auth).clinicId(); ownChair(clinic,branchId,chairId);
        updated(db.update("UPDATE chair SET name=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND branch_id=? AND id=?",required(input.name(),"chair name",120),clinic,branchId,chairId));
    }
    @PatchMapping("/branches/{branchId}/chairs/{chairId}/active") @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void chairActive(Authentication auth,@PathVariable UUID branchId,@PathVariable UUID chairId,@RequestParam boolean active) {
        UUID clinic=manager(auth).clinicId(); ownChair(clinic,branchId,chairId);
        updated(db.update("UPDATE chair SET active=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND branch_id=? AND id=?",active,clinic,branchId,chairId));
    }
    @GetMapping("/branches/{branchId}/hours") @Transactional(readOnly = true)
    public List<Map<String,Object>> hours(Authentication auth,@PathVariable UUID branchId) {
        UUID clinic=user(auth).clinicId(); ownBranch(clinic,branchId);
        return db.queryForList("SELECT day_of_week,opens_at,closes_at FROM branch_operating_hour WHERE clinic_id=? AND branch_id=? ORDER BY day_of_week",clinic,branchId);
    }
    @PutMapping("/branches/{branchId}/hours") @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceHours(Authentication auth,@PathVariable UUID branchId,@RequestBody List<HoursInput> hours) {
        UUID clinic=manager(auth).clinicId(); ownBranch(clinic,branchId);
        if(hours==null || hours.size()>7 || hours.stream().map(HoursInput::dayOfWeek).distinct().count()!=hours.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expected unique weekdays, maximum seven");
        for(HoursInput h:hours) {
            if(h.dayOfWeek()<1 || h.dayOfWeek()>7) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Weekday must be 1–7");
            try { if(!LocalTime.parse(h.opensAt()).isBefore(LocalTime.parse(h.closesAt()))) throw new IllegalArgumentException(); }
            catch(RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid operating hours"); }
        }
        db.update("DELETE FROM branch_operating_hour WHERE clinic_id=? AND branch_id=?",clinic,branchId);
        for(HoursInput h:hours) db.update("INSERT INTO branch_operating_hour(clinic_id,branch_id,day_of_week,opens_at,closes_at) VALUES (?,?,?,?,?)",clinic,branchId,h.dayOfWeek(),LocalTime.parse(h.opensAt()),LocalTime.parse(h.closesAt()));
    }
    @GetMapping("/staff") @Transactional(readOnly = true)
    public List<Map<String,Object>> staff(Authentication auth) {
        UUID clinic=manager(auth).clinicId();
        return db.queryForList("SELECT id,full_name,email,role,active FROM app_user WHERE clinic_id=? ORDER BY full_name",clinic);
    }
    @GetMapping("/branches/{branchId}/staff") @Transactional(readOnly = true)
    public List<Map<String,Object>> assigned(Authentication auth,@PathVariable UUID branchId) {
        UUID clinic=manager(auth).clinicId(); ownBranch(clinic,branchId);
        return db.queryForList("SELECT u.id,u.full_name,u.role FROM app_user u JOIN user_branch x ON x.user_id=u.id AND x.clinic_id=u.clinic_id WHERE x.clinic_id=? AND x.branch_id=? ORDER BY u.full_name",clinic,branchId);
    }
    @PostMapping("/branches/{branchId}/staff") @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(Authentication auth,@PathVariable UUID branchId,@RequestBody AssignmentInput input) {
        UUID clinic=manager(auth).clinicId(); ownBranch(clinic,branchId);
        Integer matches=db.queryForObject("SELECT count(*) FROM app_user WHERE id=? AND clinic_id=? AND active=true",Integer.class,input.userId(),clinic);
        if(matches==null || matches!=1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Staff must be active in this clinic");
        db.update("INSERT INTO user_branch(clinic_id,user_id,branch_id) VALUES (?,?,?) ON CONFLICT (user_id,branch_id) DO NOTHING",clinic,input.userId(),branchId);
    }
    @DeleteMapping("/branches/{branchId}/staff/{userId}") @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(Authentication auth,@PathVariable UUID branchId,@PathVariable UUID userId) {
        UUID clinic=manager(auth).clinicId(); ownBranch(clinic,branchId);
        updated(db.update("DELETE FROM user_branch WHERE clinic_id=? AND branch_id=? AND user_id=?",clinic,branchId,userId));
    }
}
