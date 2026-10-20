package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** M6 treatment planning; estimates are not invoices or payment demands. */
@RestController @RequestMapping("/api/treatment-plans")
public class TreatmentPlanController {
 private final JdbcTemplate db;
 public TreatmentPlanController(JdbcTemplate db){this.db=db;}
 public record PlanInput(UUID patientId,UUID dentistId,String title){}
 public record CatalogInput(String code,String name,String category,BigDecimal defaultPrice,Boolean requiresTooth,Boolean requiresSurface){}
 public record ItemInput(UUID procedureId,Integer toothNumber,String surface,Integer phase,Integer quantity,BigDecimal unitPrice,BigDecimal discount){}
 public record TransitionInput(String status,Long expectedVersion,String consentReference){}
 public record ProgressInput(String status,UUID visitId){}
 private AuthPrincipal clinician(Authentication a){
  if(a==null||!(a.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN","DENTIST").contains(p.role()))
   throw new AccessDeniedException("Clinical treatment access required");
  return p;
 }
 private void require(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private Map<String,Object> plan(UUID clinic,UUID id,boolean lock){
  var rows=db.queryForList("SELECT id,patient_id,dentist_id,title,status,version,accepted_at,consent_reference FROM treatment_plan WHERE clinic_id=? AND id=?"+(lock?" FOR UPDATE":""),clinic,id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Treatment plan not found");
  return rows.get(0);
 }
 private void event(AuthPrincipal a,UUID plan,Object patient,String kind,String details){
  db.update("INSERT INTO treatment_plan_event(clinic_id,plan_id,patient_id,actor_id,event_type,details) VALUES(?,?,?,?,?,?)",
   a.clinicId(),plan,patient,a.userId(),kind,details);
 }
 private AuthPrincipal manager(Authentication a){
  AuthPrincipal p=clinician(a);
  if(!Set.of("OWNER","ADMIN").contains(p.role())) throw new AccessDeniedException("Procedure catalog administration requires clinic manager");
  return p;
 }
 @PostMapping("/catalog") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> addProcedure(Authentication a,@RequestBody CatalogInput in){
  AuthPrincipal p=manager(a);
  require(in!=null&&in.code()!=null&&!in.code().isBlank()&&in.code().length()<=80&&
   in.name()!=null&&!in.name().isBlank()&&in.name().length()<=255&&
   in.defaultPrice()!=null&&in.defaultPrice().signum()>=0&&in.defaultPrice().scale()<=2&&
   in.defaultPrice().compareTo(new BigDecimal("9999999999.99"))<=0,"Invalid procedure");
  UUID id=UUID.randomUUID();
  try{
   db.update("INSERT INTO procedure_catalog(id,clinic_id,code,name,category,default_price,requires_tooth,requires_surface) "+
    "VALUES(?,?,?,?,?,?,?,?)",id,p.clinicId(),in.code().trim(),in.name().trim(),in.category(),
    in.defaultPrice(),Boolean.TRUE.equals(in.requiresTooth()),Boolean.TRUE.equals(in.requiresSurface()));
  }catch(DataIntegrityViolationException err){
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Procedure code already in use");
  }
  return db.queryForMap("SELECT id,code,name,category,default_price,requires_tooth,requires_surface,active "+
   "FROM procedure_catalog WHERE clinic_id=? AND id=?",p.clinicId(),id);
 }
 @PostMapping("/catalog/{procedureId}/deactivate") @Transactional
 public Map<String,Object> deactivateProcedure(Authentication a,@PathVariable UUID procedureId){
  AuthPrincipal p=manager(a);
  int updated=db.update("UPDATE procedure_catalog SET active=false,updated_at=now(),version=version+1 "+
   "WHERE clinic_id=? AND id=? AND active=true",p.clinicId(),procedureId);
  if(updated!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active procedure not found");
  return db.queryForMap("SELECT id,code,name,active FROM procedure_catalog WHERE clinic_id=? AND id=?",p.clinicId(),procedureId);
 }
 @GetMapping("/dentists") @Transactional(readOnly=true)
 public List<Map<String,Object>> dentists(Authentication a){
  UUID c=clinician(a).clinicId();
  return db.queryForList("SELECT id,full_name FROM dentist WHERE clinic_id=? AND active=true ORDER BY full_name",c);
 }
 @GetMapping("/catalog") @Transactional(readOnly=true)
 public List<Map<String,Object>> catalog(Authentication a){
  UUID c=clinician(a).clinicId();
  return db.queryForList("SELECT id,code,name,category,default_price,requires_tooth,requires_surface FROM procedure_catalog "+
   "WHERE clinic_id=? AND active=true ORDER BY category,name LIMIT 500",c);
 }
 @GetMapping("/patients/{patientId}") @Transactional(readOnly=true)
 public List<Map<String,Object>> patientPlans(Authentication a,@PathVariable UUID patientId){
  UUID c=clinician(a).clinicId();
  if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL)",
    Boolean.class,c,patientId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
  return db.queryForList("SELECT id,title,status,version,created_at,accepted_at FROM treatment_plan "+
   "WHERE clinic_id=? AND patient_id=? ORDER BY created_at DESC LIMIT 100",c,patientId);
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> create(Authentication a,@RequestBody PlanInput in){
  AuthPrincipal p=clinician(a);
  require(in!=null&&in.patientId()!=null&&in.dentistId()!=null&&in.title()!=null&&
    !in.title().isBlank()&&in.title().length()<=255,"Patient, dentist and title required");
  require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL)",
   Boolean.class,p.clinicId(),in.patientId())),"Active patient required");
  require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM dentist WHERE clinic_id=? AND id=? AND active)",
   Boolean.class,p.clinicId(),in.dentistId())),"Active dentist required");
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO treatment_plan(id,clinic_id,patient_id,dentist_id,title) VALUES(?,?,?,?,?)",
   id,p.clinicId(),in.patientId(),in.dentistId(),in.title().trim());
  event(p,id,in.patientId(),"CREATED",null);
  return plan(p.clinicId(),id,false);
 }
 @GetMapping("/{id}") @Transactional(readOnly=true)
 public Map<String,Object> detail(Authentication a,@PathVariable UUID id){
  AuthPrincipal p=clinician(a);var data=new LinkedHashMap<String,Object>(plan(p.clinicId(),id,false));
  var items=db.queryForList("SELECT i.id,i.procedure_id,p.name AS procedure_name,i.tooth_number,i.surface,i.phase,i.quantity,"+
   "i.unit_price,i.discount,i.status,i.completed_visit_id FROM treatment_plan_item i "+
   "JOIN procedure_catalog p ON p.clinic_id=i.clinic_id AND p.id=i.procedure_id "+
   "WHERE i.clinic_id=? AND i.plan_id=? ORDER BY i.phase,i.created_at,i.id",p.clinicId(),id);
  data.put("items",items);
  BigDecimal total=BigDecimal.ZERO;
  for(var item:items){
   var price=(BigDecimal)item.get("unit_price");var discount=(BigDecimal)item.get("discount");
   var qty=BigDecimal.valueOf(((Number)item.get("quantity")).longValue());
   total=total.add(price.multiply(qty).subtract(discount));
  }
  data.put("estimate_total",total);
  return data;
 }
 @PostMapping("/{id}/items") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> addItem(Authentication a,@PathVariable UUID id,@RequestBody ItemInput in){
  AuthPrincipal p=clinician(a);var plan=plan(p.clinicId(),id,true);
  if(!"DRAFT".equals(plan.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft plans can change");
  require(in!=null&&in.procedureId()!=null&&in.phase()!=null&&in.phase()>0&&in.quantity()!=null&&
   in.quantity()>0&&in.quantity()<=1000&&in.unitPrice()!=null&&in.unitPrice().signum()>=0&&
   in.discount()!=null&&in.discount().signum()>=0&&in.unitPrice().scale()<=2&&in.discount().scale()<=2,
   "Invalid item pricing, phase or quantity");
  BigDecimal total=in.unitPrice().multiply(BigDecimal.valueOf(in.quantity()));
  require(in.discount().compareTo(total)<=0,"Discount cannot exceed line total");
  var procedure=db.queryForList("SELECT requires_tooth,requires_surface FROM procedure_catalog WHERE clinic_id=? AND id=? AND active=true",
   p.clinicId(),in.procedureId());
  if(procedure.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Procedure unavailable");
  var proc=procedure.get(0);
  require(!Boolean.TRUE.equals(proc.get("requires_tooth"))||in.toothNumber()!=null,"Tooth required");
  require(!Boolean.TRUE.equals(proc.get("requires_surface"))||(in.surface()!=null&&!in.surface().isBlank()),"Surface required");
  if(in.toothNumber()!=null)
   require(com.dentalapp.odontogram.ToothNotation.validFdi(in.toothNumber())&&
     com.dentalapp.odontogram.ToothNotation.validSurface(in.toothNumber(),in.surface()),"Invalid tooth/surface");
  UUID item=UUID.randomUUID();
  db.update("INSERT INTO treatment_plan_item(id,clinic_id,plan_id,procedure_id,tooth_number,surface,phase,quantity,unit_price,discount) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?)",item,p.clinicId(),id,in.procedureId(),in.toothNumber(),in.surface(),in.phase(),
   in.quantity(),in.unitPrice(),in.discount());
  event(p,id,plan.get("patient_id"),"ITEM_ADDED",item.toString());
  return detail(a,id);
 }
 @PostMapping("/{id}/transition") @Transactional
 public Map<String,Object> transition(Authentication a,@PathVariable UUID id,@RequestBody TransitionInput in){
  AuthPrincipal p=clinician(a);
  require(in!=null&&in.expectedVersion()!=null&&in.expectedVersion()>=0&&in.status()!=null,"Status and version required");
  var record=plan(p.clinicId(),id,true);String previous=(String)record.get("status");
  Map<String,String> allowed=Map.of("DRAFT","PROPOSED","PROPOSED","ACCEPTED,DECLINED","ACCEPTED","IN_PROGRESS","IN_PROGRESS","COMPLETED");
  require(Arrays.asList(allowed.getOrDefault(previous,"").split(",")).contains(in.status()),"Invalid status transition");
  if("PROPOSED".equals(in.status()) && !Boolean.TRUE.equals(db.queryForObject(
   "SELECT EXISTS(SELECT 1 FROM treatment_plan_item WHERE clinic_id=? AND plan_id=?)",
   Boolean.class,p.clinicId(),id)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"A treatment plan must contain at least one procedure");
  if("ACCEPTED".equals(in.status()))require(in.consentReference()!=null&&in.consentReference().trim().length()>=3&&
   in.consentReference().length()<=256,"Patient consent evidence reference required");
  if("COMPLETED".equals(in.status())&&Boolean.TRUE.equals(db.queryForObject(
   "SELECT EXISTS(SELECT 1 FROM treatment_plan_item WHERE clinic_id=? AND plan_id=? AND status NOT IN ('COMPLETED','CANCELLED'))",
   Boolean.class,p.clinicId(),id)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Unfinished treatment items remain");
  int changed=db.update("UPDATE treatment_plan SET status=?,version=version+1,updated_at=now(),"+
   "accepted_at=CASE WHEN ?='ACCEPTED' THEN now() ELSE accepted_at END,"+
   "consent_reference=CASE WHEN ?='ACCEPTED' THEN ? ELSE consent_reference END,"+
   "consent_recorded_at=CASE WHEN ?='ACCEPTED' THEN now() ELSE consent_recorded_at END,"+
   "consent_recorded_by=CASE WHEN ?='ACCEPTED' THEN ? ELSE consent_recorded_by END "+
   "WHERE clinic_id=? AND id=? AND version=?",
   in.status(),in.status(),in.status(),in.consentReference(),in.status(),in.status(),p.userId(),p.clinicId(),id,in.expectedVersion());
  if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Treatment plan version changed");
  event(p,id,record.get("patient_id"),in.status().equals("IN_PROGRESS")?"STARTED":in.status(),null);
  return detail(a,id);
 }
 @PostMapping("/{id}/items/{itemId}/progress") @Transactional
 public Map<String,Object> progress(Authentication a,@PathVariable UUID id,@PathVariable UUID itemId,@RequestBody ProgressInput in){
  AuthPrincipal p=clinician(a);var plan=plan(p.clinicId(),id,true);
  if(!"IN_PROGRESS".equals(plan.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Plan not in progress");
  require(in!=null&&Set.of("SCHEDULED","COMPLETED").contains(in.status()),"Invalid progress");
  var items=db.queryForList("SELECT status FROM treatment_plan_item WHERE clinic_id=? AND plan_id=? AND id=? FOR UPDATE",
   p.clinicId(),id,itemId);
  if(items.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plan item not found");
  require("PLANNED".equals(items.get(0).get("status"))||
   ("COMPLETED".equals(in.status())&&"SCHEDULED".equals(items.get(0).get("status"))),"Item already finalized");
  if("COMPLETED".equals(in.status())){
   require(in.visitId()!=null&&Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM visit WHERE clinic_id=? AND id=? AND patient_id=? AND encounter_status='COMPLETED')",
    Boolean.class,p.clinicId(),in.visitId(),plan.get("patient_id"))),"Completed encounter for same patient required");
  }
  db.update("UPDATE treatment_plan_item SET status=?,completed_visit_id=?,updated_at=now(),version=version+1 "+
   "WHERE clinic_id=? AND plan_id=? AND id=?",in.status(),"COMPLETED".equals(in.status())?in.visitId():null,p.clinicId(),id,itemId);
  event(p,id,plan.get("patient_id"),"COMPLETED".equals(in.status())?"ITEM_COMPLETED":"ITEM_SCHEDULED",itemId.toString());
  return detail(a,id);
 }
 @GetMapping("/{id}/history") @Transactional(readOnly=true)
 public List<Map<String,Object>> history(Authentication a,@PathVariable UUID id){
  AuthPrincipal p=clinician(a);plan(p.clinicId(),id,false);
  return db.queryForList("SELECT id,event_type,details,actor_id,event_at FROM treatment_plan_event WHERE clinic_id=? AND plan_id=? ORDER BY event_at,id",p.clinicId(),id);
 }
 @GetMapping("/{id}/billing-preview") @Transactional(readOnly=true)
 public Map<String,Object> billingPreview(Authentication a,@PathVariable UUID id){
  Map<String,Object> plan=detail(a,id);
  // Preview only: billing is M7, no invoice or payment is created here.
  String currency=db.queryForObject("SELECT currency FROM clinic WHERE id=?",String.class,clinician(a).clinicId());
  return Map.of("planId",id,"currency",currency==null?"PHP":currency.trim(),"estimateTotal",plan.get("estimate_total"),"invoiceCreated",false);
 }
}
