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

/** M7.12 — configuration/readiness only, not a government-approved tax invoice issuer. */
@RestController @RequestMapping("/api/billing/fiscal-readiness")
public class FiscalReadinessController {
 private final JdbcTemplate db;
 public FiscalReadinessController(JdbcTemplate db){this.db=db;}
 public record Profile(String jurisdiction,String registeredName,String registeredAddress,
                       String taxRegistrationNo,String taxCategory,String permitReference){}
 private AuthPrincipal manager(Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN").contains(p.role()))
   throw new AccessDeniedException("Clinic administrator required for fiscal configuration");
  return p;
 }
 private List<Map<String,Object>> current(UUID clinic){
  return db.queryForList("SELECT jurisdiction,registered_name,registered_address,tax_registration_no,tax_category,permit_reference,reviewed_at FROM fiscal_profile WHERE clinic_id=?",clinic);
 }
 @GetMapping @Transactional(readOnly=true)
 public Map<String,Object> status(Authentication auth){
  var p=manager(auth);var entries=current(p.clinicId());
  var missing=new ArrayList<String>();
  if(entries.isEmpty())missing.add("Clinic legal registration and fiscal profile");
  else {
   var item=entries.get(0);
   if(item.get("permit_reference")==null)missing.add("Tax-authority permit/registered document issuance verification");
  }
  // Regulatory review must be external and jurisdiction-specific even with a completed profile.
  missing.add("Local tax professional review, official serial authorization, fiscal invoice workflow, tax and discount computations");
  return Map.of("profile",entries,"requirementsRemaining",missing,
    "documentStatus","NOT_AUTHORIZED","officialReceiptEnabled",false,
    "notice","Internal billing statement only. Not an official tax invoice or official receipt.");
 }
 @PutMapping @Transactional
 public Map<String,Object> configure(Authentication auth,@RequestBody Profile in){
  var p=manager(auth);
  if(in==null||in.jurisdiction()==null||!in.jurisdiction().matches("[A-Z]{2}")||
   in.registeredName()==null||in.registeredName().trim().length()<3||in.registeredName().length()>200||
   in.registeredAddress()==null||in.registeredAddress().trim().length()<5||in.registeredAddress().length()>500||
   in.taxRegistrationNo()==null||in.taxRegistrationNo().trim().length()<3||in.taxRegistrationNo().length()>80||
   in.taxCategory()==null||!Set.of("VAT","NON_VAT","OTHER").contains(in.taxCategory())||
   (in.permitReference()!=null&&(in.permitReference().trim().length()<3||in.permitReference().length()>150)))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid fiscal registration details required");
  db.update("INSERT INTO fiscal_profile(clinic_id,jurisdiction,registered_name,registered_address,tax_registration_no,tax_category,permit_reference,reviewed_by,reviewed_at) "+
    "VALUES(?,?,?,?,?,?,?,?,now()) ON CONFLICT(clinic_id) DO UPDATE SET jurisdiction=excluded.jurisdiction,registered_name=excluded.registered_name,"+
    "registered_address=excluded.registered_address,tax_registration_no=excluded.tax_registration_no,tax_category=excluded.tax_category,"+
    "permit_reference=excluded.permit_reference,reviewed_by=excluded.reviewed_by,reviewed_at=excluded.reviewed_at,updated_at=now()",
    p.clinicId(),in.jurisdiction(),in.registeredName().trim(),in.registeredAddress().trim(),in.taxRegistrationNo().trim(),
    in.taxCategory(),in.permitReference()==null?null:in.permitReference().trim(),p.userId());
  return status(auth);
 }
}
