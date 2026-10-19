package com.dentalapp.patient;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.service.NumberingService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** M2.1 registry only: clinical records and billing remain separate, preview-only workflows. */
@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final JdbcTemplate db;
    private final NumberingService numbering;
    public PatientController(JdbcTemplate db, NumberingService numbering) { this.db=db; this.numbering=numbering; }

    public record PatientInput(String firstName, String lastName, LocalDate birthDate, String sex,
                               String phone, String email, String address, String emergencyName,
                               String emergencyPhone, Boolean smsOptIn) {}

    private AuthPrincipal staff(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal p))
            throw new AccessDeniedException("Clinic staff access required");
        return p;
    }
    private static String required(String s, String label, int max) {
        if (s == null || s.isBlank() || s.strip().length()>max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid "+label);
        return s.strip();
    }
    private static String optional(String s, int max) {
        if (s == null || s.isBlank()) return null;
        if (s.strip().length()>max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Field exceeds maximum length");
        return s.strip();
    }
    private void validate(PatientInput p) {
        required(p.firstName(),"first name",120);
        required(p.lastName(),"last name",120);
        if (p.birthDate()!=null && p.birthDate().isAfter(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Birth date cannot be in the future");
        if (p.sex()!=null && !p.sex().isBlank() && !List.of("FEMALE","MALE","OTHER","UNDISCLOSED").contains(p.sex()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported sex value");
        optional(p.phone(),80); optional(p.email(),254); optional(p.address(),2000);
        optional(p.emergencyName(),160); optional(p.emergencyPhone(),80);
    }
    private static final String COLUMNS = "id,chart_no,first_name,last_name,birth_date,sex,phone,email,address,emergency_name,emergency_phone,sms_opt_in,created_at,updated_at,version";
    @GetMapping @Transactional(readOnly=true)
    public List<Map<String,Object>> search(Authentication auth, @RequestParam(defaultValue="") String q,
                  @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="25") int limit) {
        UUID clinic=staff(auth).clinicId();
        if (offset<0 || limit<1 || limit>100 || q.length()>120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination or search");
        String text="%"+q.strip().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        return db.queryForList("SELECT "+COLUMNS+" FROM patient WHERE clinic_id=? AND deleted_at IS NULL AND (chart_no ILIKE ? ESCAPE '\\' OR first_name ILIKE ? ESCAPE '\\' OR last_name ILIKE ? ESCAPE '\\' OR concat_ws(' ',first_name,last_name) ILIKE ? ESCAPE '\\' OR phone ILIKE ? ESCAPE '\\') ORDER BY last_name,first_name,id LIMIT ? OFFSET ?",clinic,text,text,text,text,text,limit,offset);
    }
    @GetMapping("/{id}") @Transactional(readOnly=true)
    public Map<String,Object> get(Authentication auth,@PathVariable UUID id) {
        List<Map<String,Object>> rows=db.queryForList("SELECT "+COLUMNS+" FROM patient WHERE id=? AND clinic_id=? AND deleted_at IS NULL",id,staff(auth).clinicId());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
        return rows.get(0);
    }
    @GetMapping("/duplicate-candidates") @Transactional(readOnly=true)
    public List<Map<String,Object>> duplicates(Authentication auth,@RequestParam String firstName,@RequestParam String lastName,@RequestParam LocalDate birthDate) {
        UUID clinic=staff(auth).clinicId();
        String first=required(firstName,"first name",120),last=required(lastName,"last name",120);
        return db.queryForList("SELECT id,chart_no,first_name,last_name,birth_date FROM patient WHERE clinic_id=? AND deleted_at IS NULL AND lower(first_name)=lower(?) AND lower(last_name)=lower(?) AND birth_date=? ORDER BY created_at LIMIT 20",clinic,first,last,birthDate);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> create(Authentication auth,@RequestBody PatientInput input) {
        AuthPrincipal user=staff(auth);validate(input);
        UUID clinic=user.clinicId(),id=UUID.randomUUID();
        db.update("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name,birth_date,sex,phone,email,address,emergency_name,emergency_phone,sms_opt_in) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,clinic,numbering.nextChartNo(),required(input.firstName(),"first name",120),required(input.lastName(),"last name",120),input.birthDate(),optional(input.sex(),20),optional(input.phone(),80),optional(input.email(),254),optional(input.address(),2000),optional(input.emergencyName(),160),optional(input.emergencyPhone(),80),Boolean.TRUE.equals(input.smsOptIn()));
        audit(clinic,user.userId(),"PATIENT_CREATED",id);
        return get(auth,id);
    }
    @PutMapping("/{id}") @Transactional
    public Map<String,Object> update(Authentication auth,@PathVariable UUID id,@RequestBody PatientInput input) {
        AuthPrincipal user=staff(auth);validate(input);
        int count=db.update("UPDATE patient SET first_name=?,last_name=?,birth_date=?,sex=?,phone=?,email=?,address=?,emergency_name=?,emergency_phone=?,sms_opt_in=?,updated_at=now(),version=version+1 WHERE id=? AND clinic_id=? AND deleted_at IS NULL",
            required(input.firstName(),"first name",120),required(input.lastName(),"last name",120),input.birthDate(),optional(input.sex(),20),optional(input.phone(),80),optional(input.email(),254),optional(input.address(),2000),optional(input.emergencyName(),160),optional(input.emergencyPhone(),80),Boolean.TRUE.equals(input.smsOptIn()),id,user.clinicId());
        if(count!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
        audit(user.clinicId(),user.userId(),"PATIENT_UPDATED",id);
        return get(auth,id);
    }
    @PostMapping("/{id}/archive") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void archive(Authentication auth,@PathVariable UUID id) {
        AuthPrincipal user=staff(auth);
        if(!List.of("OWNER","ADMIN").contains(user.role())) throw new AccessDeniedException("Archiving requires clinic management role");
        int count=db.update("UPDATE patient SET deleted_at=now(),updated_at=now(),version=version+1 WHERE id=? AND clinic_id=? AND deleted_at IS NULL",id,user.clinicId());
        if(count!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
        audit(user.clinicId(),user.userId(),"PATIENT_ARCHIVED",id);
    }
    private void audit(UUID clinic,UUID user,String action,UUID patient) {
        db.update("INSERT INTO audit_log(clinic_id,user_id,action,entity,entity_id) VALUES (?,?,?,'patient',?)",clinic,user,action,patient);
    }
}
