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

/** Draft-only prescriptions; metadata references only, not an upload or file storage API. */
@RestController @RequestMapping("/api/encounters/{visitId}/records")
public class EncounterRecordsController {
 private final JdbcTemplate db;
 public EncounterRecordsController(JdbcTemplate db){this.db=db;}
 public record PrescriptionInput(String medication,String dosage,String instructions) {}
 public record DocumentInput(String filename,String externalReference,String contentType) {}
 private AuthPrincipal clinician(Authentication a){
  if(a==null||!(a.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN","DENTIST").contains(p.role()))
   throw new AccessDeniedException("Clinical encounter access required");
  return p;
 }
 private Object patient(UUID clinic,UUID visit,boolean lock,boolean mutable){
  var rows=db.queryForList("SELECT patient_id,encounter_status,locked_at FROM visit WHERE clinic_id=? AND id=?"+
   (lock?" FOR UPDATE":""),clinic,visit);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Encounter not found");
  var row=rows.get(0);
  if(mutable&&(!"IN_PROGRESS".equals(row.get("encounter_status"))||row.get("locked_at")!=null))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Encounter no longer accepts drafts");
  return row.get("patient_id");
 }
 private void valid(String value,int max,String label){
  if(value==null||value.isBlank()||value.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" invalid");
 }
 @GetMapping("/prescriptions") @Transactional(readOnly=true)
 public List<Map<String,Object>> prescriptions(Authentication a,@PathVariable UUID visitId){
  UUID c=clinician(a).clinicId();patient(c,visitId,false,false);
  return db.queryForList("SELECT id,medication,dosage,instructions,status,author_id,created_at FROM encounter_prescription_draft "+
   "WHERE clinic_id=? AND visit_id=? ORDER BY created_at,id",c,visitId);
 }
 @PostMapping("/prescriptions") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> prescription(Authentication a,@PathVariable UUID visitId,@RequestBody PrescriptionInput input){
  AuthPrincipal p=clinician(a);
  if(input==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Prescription draft required");
  valid(input.medication(),300,"Medication");valid(input.dosage(),500,"Dosage");valid(input.instructions(),2000,"Instructions");
  Object patient=patient(p.clinicId(),visitId,true,true);
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO encounter_prescription_draft(id,clinic_id,visit_id,patient_id,medication,dosage,instructions,author_id) "+
   "VALUES(?,?,?,?,?,?,?,?)",id,p.clinicId(),visitId,patient,input.medication().trim(),input.dosage().trim(),input.instructions().trim(),p.userId());
  return db.queryForMap("SELECT id,medication,dosage,instructions,status,author_id,created_at FROM encounter_prescription_draft WHERE clinic_id=? AND id=?",p.clinicId(),id);
 }
 @GetMapping("/documents") @Transactional(readOnly=true)
 public List<Map<String,Object>> documents(Authentication a,@PathVariable UUID visitId){
  UUID c=clinician(a).clinicId();patient(c,visitId,false,false);
  return db.queryForList("SELECT id,filename,external_reference,content_type,added_by,created_at FROM encounter_document_reference "+
   "WHERE clinic_id=? AND visit_id=? ORDER BY created_at,id",c,visitId);
 }
 @PostMapping("/documents") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> document(Authentication a,@PathVariable UUID visitId,@RequestBody DocumentInput input){
  AuthPrincipal p=clinician(a);
  if(input==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Document metadata required");
  valid(input.filename(),255,"Filename");valid(input.externalReference(),512,"Reference");valid(input.contentType(),120,"Content type");
  // File references must be supplied by the approved secure storage module; no arbitrary URLs accepted.
  if(!input.externalReference().matches("[a-zA-Z0-9/_\\-.]{1,512}")||input.externalReference().contains(".."))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid storage reference");
  Object patient=patient(p.clinicId(),visitId,true,true);
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO encounter_document_reference(id,clinic_id,visit_id,patient_id,filename,external_reference,content_type,added_by) "+
   "VALUES(?,?,?,?,?,?,?,?)",id,p.clinicId(),visitId,patient,input.filename().trim(),input.externalReference().trim(),input.contentType().trim(),p.userId());
  return db.queryForMap("SELECT id,filename,external_reference,content_type,added_by,created_at FROM encounter_document_reference WHERE clinic_id=? AND id=?",p.clinicId(),id);
 }
}
