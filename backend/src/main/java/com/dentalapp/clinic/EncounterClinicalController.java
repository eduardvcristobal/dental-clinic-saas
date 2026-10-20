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

/** M5.2 draft clinical notes; signed notes and amendments belong to M5.3. */
@RestController @RequestMapping("/api/encounters/{visitId}/clinical")
public class EncounterClinicalController {
 private final JdbcTemplate db;
 public EncounterClinicalController(JdbcTemplate db) {this.db=db;}
 public record SoapInput(String subjective,String objective,String assessment,String planNotes,Long expectedVersion) {}
 public record DiagnosisInput(String codeSystem,String code,String description) {}
 private AuthPrincipal clinician(Authentication a) {
  if(a==null || !(a.getPrincipal() instanceof AuthPrincipal p) ||
    !Set.of("OWNER","ADMIN","DENTIST").contains(p.role()))
   throw new AccessDeniedException("Clinical encounter access required");
  return p;
 }
 private Map<String,Object> visit(UUID c,UUID id) {
  var rows=db.queryForList("SELECT patient_id,encounter_status,locked_at FROM visit WHERE clinic_id=? AND id=?",c,id);
  if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Encounter not found");
  return rows.get(0);
 }
 private void editable(Map<String,Object> visit) {
  if(!"IN_PROGRESS".equals(visit.get("encounter_status")) || visit.get("locked_at")!=null)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Encounter is finalized or locked");
 }
 private static String field(String s) {
  if(s==null) return "";
  if(s.length()>20000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Clinical field exceeds 20000 characters");
  return s;
 }
 @GetMapping("/soap") @Transactional(readOnly=true)
 public Map<String,Object> soap(Authentication a,@PathVariable UUID visitId) {
  UUID c=clinician(a).clinicId();visit(c,visitId);
  var rows=db.queryForList("SELECT subjective,objective,assessment,plan_notes,version,updated_at,updated_by "+
   "FROM encounter_soap_draft WHERE clinic_id=? AND visit_id=?",c,visitId);
  return rows.isEmpty()?Map.of("subjective","","objective","","assessment","","plan_notes","","version",0L):rows.get(0);
 }
 @GetMapping("/revisions") @Transactional(readOnly=true)
 public List<Map<String,Object>> revisions(Authentication a,@PathVariable UUID visitId) {
  UUID c=clinician(a).clinicId();visit(c,visitId);
  return db.queryForList("SELECT id,draft_version,subjective,objective,assessment,plan_notes,actor_user_id,recorded_at "+
   "FROM encounter_soap_revision WHERE clinic_id=? AND visit_id=? ORDER BY draft_version DESC LIMIT 100",c,visitId);
 }
 @PutMapping("/soap") @Transactional
 public Map<String,Object> save(Authentication a,@PathVariable UUID visitId,@RequestBody SoapInput input) {
  AuthPrincipal p=clinician(a);
  if(input==null||input.expectedVersion()==null||input.expectedVersion()<0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expected version required");
  String s=field(input.subjective()),o=field(input.objective()),assessment=field(input.assessment()),plan=field(input.planNotes());
  // Lock encounter row first so signing/completion cannot race with this draft write.
  var visits=db.queryForList("SELECT patient_id,encounter_status,locked_at FROM visit WHERE clinic_id=? AND id=? FOR UPDATE",p.clinicId(),visitId);
  if(visits.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Encounter not found");
  var v=visits.get(0);editable(v);UUID patient=(UUID)v.get("patient_id");
  int changed;
  if(input.expectedVersion()==0) {
   changed=db.update("INSERT INTO encounter_soap_draft(visit_id,clinic_id,patient_id,subjective,objective,assessment,plan_notes,version,updated_by) "+
    "VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT (visit_id) DO NOTHING",visitId,p.clinicId(),patient,s,o,assessment,plan,1L,p.userId());
  } else {
   changed=db.update("UPDATE encounter_soap_draft SET subjective=?,objective=?,assessment=?,plan_notes=?,version=version+1,updated_by=?,updated_at=now() "+
    "WHERE clinic_id=? AND visit_id=? AND version=?",s,o,assessment,plan,p.userId(),p.clinicId(),visitId,input.expectedVersion());
  }
  if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Clinical draft version changed");
  long newVersion=input.expectedVersion()+1;
  db.update("INSERT INTO encounter_soap_revision(clinic_id,visit_id,patient_id,draft_version,subjective,objective,assessment,plan_notes,actor_user_id) "+
   "VALUES(?,?,?,?,?,?,?,?,?)",p.clinicId(),visitId,patient,newVersion,s,o,assessment,plan,p.userId());
  return soap(a,visitId);
 }
 @GetMapping("/diagnoses") @Transactional(readOnly=true)
 public List<Map<String,Object>> diagnoses(Authentication a,@PathVariable UUID visitId) {
  UUID c=clinician(a).clinicId();visit(c,visitId);
  return db.queryForList("SELECT id,code_system,diagnosis_code,description,created_at,created_by FROM encounter_diagnosis "+
   "WHERE clinic_id=? AND visit_id=? ORDER BY created_at,id",c,visitId);
 }
 @PostMapping("/diagnoses") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> addDiagnosis(Authentication a,@PathVariable UUID visitId,@RequestBody DiagnosisInput input) {
  AuthPrincipal p=clinician(a);
  if(input==null || !Set.of("ICD10","SNODENT","LOCAL").contains(input.codeSystem()) ||
   input.code()==null||input.code().isBlank()||input.code().length()>80 ||
   input.description()==null||input.description().isBlank()||input.description().length()>500)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid diagnosis");
  var visits=db.queryForList("SELECT patient_id,encounter_status,locked_at FROM visit WHERE clinic_id=? AND id=? FOR UPDATE",p.clinicId(),visitId);
  if(visits.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Encounter not found");
  editable(visits.get(0));
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO encounter_diagnosis(id,clinic_id,visit_id,patient_id,code_system,diagnosis_code,description,created_by) "+
    "VALUES(?,?,?,?,?,?,?,?)",id,p.clinicId(),visitId,visits.get(0).get("patient_id"),input.codeSystem(),input.code().trim(),input.description().trim(),p.userId());
  return db.queryForMap("SELECT id,code_system,diagnosis_code,description,created_at,created_by FROM encounter_diagnosis WHERE clinic_id=? AND id=?",p.clinicId(),id);
 }
}
