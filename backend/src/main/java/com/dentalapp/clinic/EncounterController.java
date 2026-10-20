package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** M5.1 — scoped visit lifecycle; SOAP editing and signing remain M5.2–M5.3. */
@RestController
@RequestMapping("/api/encounters")
public class EncounterController {
  private final JdbcTemplate db;
  public EncounterController(JdbcTemplate db) { this.db=db; }
  public record StartInput(UUID patientId, UUID dentistId, UUID appointmentId) {}
  public record TransitionInput(String status, String reason, Long expectedVersion) {}

  private AuthPrincipal clinical(Authentication a) {
    if(a==null || !(a.getPrincipal() instanceof AuthPrincipal p)
      || !Set.of("OWNER","ADMIN","DENTIST").contains(p.role()))
      throw new AccessDeniedException("Clinical encounter access required");
    return p;
  }
  private void require(boolean yes, String reason) {
    if(!yes) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);
  }
  private boolean exists(String table,UUID clinic,UUID id) {
    return Boolean.TRUE.equals(db.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM "+table+" WHERE clinic_id=? AND id=?)",Boolean.class,clinic,id));
  }
  private Map<String,Object> find(UUID clinic,UUID id) {
    var rows=db.queryForList("SELECT id,patient_id,dentist_id,appointment_id,started_at,ended_at,encounter_status,completed_at,cancelled_at,version "+
      "FROM visit WHERE clinic_id=? AND id=?",clinic,id);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Encounter not found");
    return rows.get(0);
  }
  @GetMapping("/patients/{patientId}")
  @Transactional(readOnly=true)
  public List<Map<String,Object>> byPatient(Authentication a,@PathVariable UUID patientId) {
    UUID c=clinical(a).clinicId();
    if(!exists("patient",c,patientId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
    return db.queryForList("SELECT id,patient_id,dentist_id,appointment_id,started_at,ended_at,encounter_status,completed_at,cancelled_at,version "+
      "FROM visit WHERE clinic_id=? AND patient_id=? ORDER BY started_at DESC,id DESC LIMIT 100",c,patientId);
  }
  @GetMapping("/{id}")
  @Transactional(readOnly=true)
  public Map<String,Object> get(Authentication a,@PathVariable UUID id) {
    return find(clinical(a).clinicId(),id);
  }
  @GetMapping("/{id}/history")
  @Transactional(readOnly=true)
  public List<Map<String,Object>> history(Authentication a,@PathVariable UUID id) {
    UUID c=clinical(a).clinicId();find(c,id);
    return db.queryForList("SELECT id,from_status,to_status,reason,actor_user_id,recorded_at "+
      "FROM visit_lifecycle_event WHERE clinic_id=? AND visit_id=? ORDER BY recorded_at,id",c,id);
  }
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> start(Authentication a,@RequestBody StartInput in) {
    AuthPrincipal p=clinical(a); UUID c=p.clinicId();
    require(in!=null&&in.patientId()!=null&&in.dentistId()!=null,"Patient and dentist required");
    require(Boolean.TRUE.equals(db.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL)",
      Boolean.class,c,in.patientId())),"Active patient required");
    require(exists("dentist",c,in.dentistId()),"Dentist not in clinic");
    if(in.appointmentId()!=null) {
      require(Boolean.TRUE.equals(db.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM appointment WHERE clinic_id=? AND id=? AND patient_id=? AND dentist_id=?)",
        Boolean.class,c,in.appointmentId(),in.patientId(),in.dentistId())),
        "Appointment must belong to same patient and dentist");
    }
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO visit(id,clinic_id,patient_id,dentist_id,appointment_id,encounter_status) VALUES(?,?,?,?,?,'IN_PROGRESS')",
      id,c,in.patientId(),in.dentistId(),in.appointmentId());
    db.update("INSERT INTO visit_lifecycle_event(clinic_id,visit_id,patient_id,actor_user_id,to_status) VALUES(?,?,?,?,'IN_PROGRESS')",
      c,id,in.patientId(),p.userId());
    return find(c,id);
  }
  @PostMapping("/{id}/transition")
  @Transactional
  public Map<String,Object> transition(Authentication a,@PathVariable UUID id,@RequestBody TransitionInput in) {
    AuthPrincipal p=clinical(a);UUID c=p.clinicId();
    require(in!=null&&in.expectedVersion()!=null&&in.expectedVersion()>=0,"Expected version required");
    require(Set.of("COMPLETED","CANCELLED").contains(in.status()),"Invalid lifecycle transition");
    if("CANCELLED".equals(in.status())) require(in.reason()!=null&&!in.reason().isBlank()&&in.reason().length()<=500,"Cancellation reason required");
    if(in.reason()!=null)require(in.reason().length()<=500,"Reason too long");
    Map<String,Object> existing=find(c,id);
    if(!"IN_PROGRESS".equals(existing.get("encounter_status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Encounter is already finalized");
    int changed=db.update("UPDATE visit SET encounter_status=?, completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END,"+
      "cancelled_at=CASE WHEN ?='CANCELLED' THEN now() ELSE NULL END,ended_at=now(),updated_at=now(),version=version+1 "+
      "WHERE clinic_id=? AND id=? AND encounter_status='IN_PROGRESS' AND version=?",
      in.status(),in.status(),in.status(),c,id,in.expectedVersion());
    if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Encounter was modified by another user");
    db.update("INSERT INTO visit_lifecycle_event(clinic_id,visit_id,patient_id,actor_user_id,from_status,to_status,reason) "+
      "VALUES(?,?,?,?,'IN_PROGRESS',?,?)",
      c,id,existing.get("patient_id"),p.userId(),in.status(),in.reason());
    return find(c,id);
  }
}
