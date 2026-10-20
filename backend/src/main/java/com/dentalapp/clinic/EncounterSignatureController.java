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

/** M5.3: signature means authenticated electronic attestation, not a PKI digital signature. */
@RestController @RequestMapping("/api/encounters/{visitId}/signature")
public class EncounterSignatureController {
 private final JdbcTemplate db;
 public EncounterSignatureController(JdbcTemplate db){this.db=db;}
 public record SignInput(Long draftVersion) {}
 public record AmendInput(String reason,String amendment) {}
 private AuthPrincipal clinician(Authentication a) {
  if(a==null||!(a.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN","DENTIST").contains(p.role()))
   throw new AccessDeniedException("Clinical chart authorization required");
  return p;
 }
 private Map<String,Object> encounter(UUID clinic,UUID id,boolean lock) {
  var rows=db.queryForList("SELECT patient_id,encounter_status,locked_at FROM visit WHERE clinic_id=? AND id=?"+(lock?" FOR UPDATE":""),clinic,id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Encounter not found");
  return rows.get(0);
 }
 @GetMapping @Transactional(readOnly=true)
 public Map<String,Object> signed(Authentication a,@PathVariable UUID visitId) {
  UUID c=clinician(a).clinicId();encounter(c,visitId,false);
  var rows=db.queryForList("SELECT draft_version,subjective,objective,assessment,plan_notes,signed_by,signed_at "+
    "FROM encounter_signed_note WHERE clinic_id=? AND visit_id=?",c,visitId);
  return rows.isEmpty()?Map.of("signed",false):Map.of("signed",true,"note",rows.get(0));
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> sign(Authentication a,@PathVariable UUID visitId,@RequestBody SignInput input) {
  AuthPrincipal p=clinician(a);
  if(input==null||input.draftVersion()==null||input.draftVersion()<1)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Saved draft version required");
  Map<String,Object> v=encounter(p.clinicId(),visitId,true);
  if(!"IN_PROGRESS".equals(v.get("encounter_status"))||v.get("locked_at")!=null)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Encounter is finalized or locked");
  var drafts=db.queryForList("SELECT subjective,objective,assessment,plan_notes,version FROM encounter_soap_draft "+
   "WHERE clinic_id=? AND visit_id=? FOR UPDATE",p.clinicId(),visitId);
  if(drafts.isEmpty()||((Number)drafts.get(0).get("version")).longValue()!=input.draftVersion())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Saved draft version changed");
  var draft=drafts.get(0);
  if(Arrays.asList("subjective","objective","assessment","plan_notes").stream()
       .allMatch(k->((String)draft.get(k)).isBlank()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot sign an empty clinical note");
  int changed=db.update("UPDATE visit SET locked_at=now(),updated_at=now(),version=version+1 "+
    "WHERE clinic_id=? AND id=? AND locked_at IS NULL",p.clinicId(),visitId);
  if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Note already locked");
  db.update("INSERT INTO encounter_signed_note(clinic_id,visit_id,patient_id,draft_version,subjective,objective,assessment,plan_notes,signed_by) "+
    "VALUES(?,?,?,?,?,?,?,?,?)",p.clinicId(),visitId,v.get("patient_id"),input.draftVersion(),
    draft.get("subjective"),draft.get("objective"),draft.get("assessment"),draft.get("plan_notes"),p.userId());
  return signed(a,visitId);
 }
 @GetMapping("/amendments") @Transactional(readOnly=true)
 public List<Map<String,Object>> amendments(Authentication a,@PathVariable UUID visitId) {
  UUID c=clinician(a).clinicId();encounter(c,visitId,false);
  return db.queryForList("SELECT id,reason,amendment,author_id,created_at FROM encounter_note_amendment "+
    "WHERE clinic_id=? AND visit_id=? ORDER BY created_at,id",c,visitId);
 }
 @PostMapping("/amendments") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> amend(Authentication a,@PathVariable UUID visitId,@RequestBody AmendInput input) {
  AuthPrincipal p=clinician(a);
  if(input==null||input.reason()==null||input.reason().trim().length()<3||input.reason().length()>500||
    input.amendment()==null||input.amendment().isBlank()||input.amendment().length()>20000)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reason and amendment required");
  Map<String,Object> v=encounter(p.clinicId(),visitId,true);
  if(!Boolean.TRUE.equals(db.queryForObject(
   "SELECT EXISTS(SELECT 1 FROM encounter_signed_note WHERE clinic_id=? AND visit_id=?)",
   Boolean.class,p.clinicId(),visitId)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Sign note before recording an amendment");
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO encounter_note_amendment(id,clinic_id,visit_id,patient_id,reason,amendment,author_id) "+
   "VALUES(?,?,?,?,?,?,?)",id,p.clinicId(),visitId,v.get("patient_id"),input.reason().trim(),input.amendment().trim(),p.userId());
  return db.queryForMap("SELECT id,reason,amendment,author_id,created_at FROM encounter_note_amendment WHERE clinic_id=? AND id=?",p.clinicId(),id);
 }
}
