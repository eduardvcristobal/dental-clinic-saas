package com.dentalapp.patient;

import com.dentalapp.auth.AuthPrincipal;
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

/** M2.2: patient-scoped histories, guardian contacts, and UNSIGNED consent tracking. */
@RestController
@RequestMapping("/api/patients/{patientId}")
public class PatientCareController {
 private final JdbcTemplate db;
 public PatientCareController(JdbcTemplate db) { this.db=db; }
 private AuthPrincipal staff(Authentication a) {
  if(a==null || !(a.getPrincipal() instanceof AuthPrincipal p) || p.clinicId()==null || p.userId()==null) throw new AccessDeniedException("Clinic staff required");
  return p;
 }
 private void clinical(AuthPrincipal p) {
  if(!List.of("OWNER","ADMIN","DENTIST").contains(p.role())) throw new AccessDeniedException("Clinical history requires clinician or management role");
 }
 private void patient(UUID clinic,UUID id) {
  if(db.queryForObject("SELECT count(*) FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL",Integer.class,clinic,id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
 }
 private String text(String s,int max,boolean required) {
  String v=s==null?"":s.strip();
  if((required&&v.isEmpty())||v.length()>max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid field length");
  return v.isEmpty()?null:v;
 }
 private void audit(AuthPrincipal p,String action,UUID subject) {
  db.update("INSERT INTO audit_log(clinic_id,user_id,action,entity,entity_id) VALUES (?,?,?,'patient',?)",p.clinicId(),p.userId(),action,subject);
 }
 public record HistoryInput(String allergies,String conditions,String medications) {
  @Override public String toString() { return "HistoryInput[clinicalFields=[REDACTED]]"; }
 }
 @GetMapping("/medical-history") @Transactional(readOnly=true)
 public Map<String,Object> history(Authentication auth,@PathVariable UUID patientId) {
  AuthPrincipal p=staff(auth);clinical(p);patient(p.clinicId(),patientId);
  List<Map<String,Object>> rows=db.queryForList("SELECT allergies,conditions,medications,updated_at,version FROM patient_medical_history WHERE clinic_id=? AND patient_id=?",p.clinicId(),patientId);
  return rows.isEmpty()?Map.of("recorded",false):rows.get(0);
 }
 @PutMapping("/medical-history") @Transactional
 public Map<String,Object> saveHistory(Authentication auth,@PathVariable UUID patientId,@RequestBody HistoryInput input) {
  AuthPrincipal p=staff(auth);clinical(p);patient(p.clinicId(),patientId);
  String allergies=text(input.allergies(),4000,false), conditions=text(input.conditions(),4000,false),medications=text(input.medications(),4000,false);
  db.update("INSERT INTO patient_medical_history(clinic_id,patient_id,allergies,conditions,medications,updated_by) VALUES (?,?,?,?,?,?) ON CONFLICT (clinic_id,patient_id) DO UPDATE SET allergies=excluded.allergies,conditions=excluded.conditions,medications=excluded.medications,updated_by=excluded.updated_by,updated_at=now(),version=patient_medical_history.version+1",p.clinicId(),patientId,allergies,conditions,medications,p.userId());
  audit(p,"PATIENT_MEDICAL_HISTORY_UPDATED",patientId);return history(auth,patientId);
 }
 public record GuardianInput(String fullName,String relationship,String phone,String email,Boolean primary) {
  @Override public String toString() { return "GuardianInput[contactFields=[REDACTED], primary="+primary+"]"; }
 }
 @GetMapping("/guardians") @Transactional(readOnly=true)
 public List<Map<String,Object>> guardians(Authentication auth,@PathVariable UUID patientId) {
  AuthPrincipal p=staff(auth);patient(p.clinicId(),patientId);
  return db.queryForList("SELECT id,full_name,relationship,phone,email,is_primary,created_at FROM patient_guardian_contact WHERE clinic_id=? AND patient_id=? ORDER BY is_primary DESC,created_at",p.clinicId(),patientId);
 }
 @PostMapping("/guardians") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> addGuardian(Authentication auth,@PathVariable UUID patientId,@RequestBody GuardianInput input) {
  AuthPrincipal p=staff(auth);patient(p.clinicId(),patientId);
  UUID id=UUID.randomUUID();boolean primary=Boolean.TRUE.equals(input.primary());
  if(primary)db.update("UPDATE patient_guardian_contact SET is_primary=false,updated_at=now() WHERE clinic_id=? AND patient_id=? AND is_primary",p.clinicId(),patientId);
  db.update("INSERT INTO patient_guardian_contact(id,clinic_id,patient_id,full_name,relationship,phone,email,is_primary) VALUES (?,?,?,?,?,?,?,?)",id,p.clinicId(),patientId,text(input.fullName(),160,true),text(input.relationship(),80,true),text(input.phone(),80,false),text(input.email(),254,false),primary);
  audit(p,"PATIENT_GUARDIAN_ADDED",patientId);
  return db.queryForMap("SELECT id,full_name,relationship,phone,email,is_primary FROM patient_guardian_contact WHERE id=? AND clinic_id=?",id,p.clinicId());
 }
 @DeleteMapping("/guardians/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
 public void removeGuardian(Authentication auth,@PathVariable UUID patientId,@PathVariable UUID id) {
  AuthPrincipal p=staff(auth);patient(p.clinicId(),patientId);
  if(db.update("DELETE FROM patient_guardian_contact WHERE id=? AND clinic_id=? AND patient_id=?",id,p.clinicId(),patientId)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Guardian not found");
  audit(p,"PATIENT_GUARDIAN_REMOVED",patientId);
 }
 public record ConsentInput(String formType) {}
 @GetMapping("/consents") @Transactional(readOnly=true)
 public List<Map<String,Object>> consents(Authentication auth,@PathVariable UUID patientId) {
  AuthPrincipal p=staff(auth);patient(p.clinicId(),patientId);
  return db.queryForList("SELECT id,form_type,consent_status,created_at,acknowledged_at,signed_at,signed_by FROM consent_form WHERE clinic_id=? AND patient_id=? ORDER BY created_at DESC",p.clinicId(),patientId);
 }
 @PostMapping("/consents") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> requestConsent(Authentication auth,@PathVariable UUID patientId,@RequestBody ConsentInput input) {
  AuthPrincipal p=staff(auth);patient(p.clinicId(),patientId);UUID id=UUID.randomUUID();
  db.update("INSERT INTO consent_form(id,clinic_id,patient_id,form_type,consent_status) VALUES (?,?,?,?,'UNSIGNED')",id,p.clinicId(),patientId,text(input.formType(),120,true));
  audit(p,"PATIENT_CONSENT_REQUESTED",patientId);
  return db.queryForMap("SELECT id,form_type,consent_status FROM consent_form WHERE clinic_id=? AND id=?",p.clinicId(),id);
 }
 @PostMapping("/consents/{id}/acknowledge") @Transactional
 public Map<String,Object> acknowledge(Authentication auth,@PathVariable UUID patientId,@PathVariable UUID id) {
  AuthPrincipal p=staff(auth);patient(p.clinicId(),patientId);
  if(db.update("UPDATE consent_form SET consent_status='ACKNOWLEDGED',acknowledged_at=now(),acknowledged_by=?,updated_at=now(),version=version+1 WHERE id=? AND patient_id=? AND clinic_id=? AND consent_status='UNSIGNED'",p.userId(),id,patientId,p.clinicId())!=1)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Consent not available for acknowledgement");
  audit(p,"PATIENT_CONSENT_ACKNOWLEDGED",patientId);
  return db.queryForMap("SELECT id,form_type,consent_status,acknowledged_at FROM consent_form WHERE id=? AND clinic_id=?",id,p.clinicId());
 }
 @GetMapping("/identity-review") @Transactional(readOnly=true)
 public Map<String,Object> identityReview(Authentication auth,@PathVariable UUID patientId) {
  AuthPrincipal p=staff(auth);patient(p.clinicId(),patientId);
  Map<String,Object> base=db.queryForMap("SELECT id,chart_no,first_name,last_name,birth_date FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL",p.clinicId(),patientId);
  Object birth=base.get("birth_date");
  if(birth==null)return Map.of("patient",base,"candidates",List.of(),"reviewRequired",false);
  List<Map<String,Object>> matches=db.queryForList("SELECT id,chart_no,first_name,last_name,birth_date FROM patient WHERE clinic_id=? AND id<>? AND deleted_at IS NULL AND lower(trim(first_name))=lower(trim(?)) AND lower(trim(last_name))=lower(trim(?)) AND birth_date=? ORDER BY created_at LIMIT 20",p.clinicId(),patientId,base.get("first_name"),base.get("last_name"),birth);
  return Map.of("patient",base,"candidates",matches,"reviewRequired",!matches.isEmpty());
 }
}
