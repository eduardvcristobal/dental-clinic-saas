package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Clinic-scoped financial records. Never accept clinic identity from a request body. */
@RestController @RequestMapping("/api/billing")
public class BillingController {
 private final JdbcTemplate db;
 public BillingController(JdbcTemplate db){this.db=db;}
 public record DraftInput(UUID patientId,UUID branchId,UUID planId,String notes){}
 public record LineInput(UUID procedureId,UUID planItemId,String description,Integer quantity,BigDecimal unitPrice,BigDecimal discount){}
 public record IssueInput(Long expectedVersion){}
 private AuthPrincipal cashier(Authentication a){
  if(a==null||!(a.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN","FRONT_DESK").contains(p.role()))
   throw new AccessDeniedException("Billing permission required");
  return p;
 }
 private void require(boolean condition,String reason){if(!condition)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
 private boolean exists(String table,UUID clinic,UUID id){
  return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE clinic_id=? AND id=?)",Boolean.class,clinic,id));
 }
 private Map<String,Object> invoice(UUID clinic,UUID id,boolean lock){
  var rows=db.queryForList("SELECT id,patient_id,branch_id,plan_id,invoice_no,status,currency_code,subtotal,discount,total,version,issued_at,voided_at,notes "+
   "FROM invoice WHERE clinic_id=? AND id=?"+(lock?" FOR UPDATE":""),clinic,id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found");
  return rows.get(0);
 }
 private void event(AuthPrincipal p,UUID id,String type,UUID reference,String reason){
  db.update("INSERT INTO billing_event(clinic_id,invoice_id,actor_id,event_type,reference_id,reason) VALUES(?,?,?,?,?,?)",
   p.clinicId(),id,p.userId(),type,reference,reason);
 }
 @GetMapping("/patients/{patientId}") @Transactional(readOnly=true)
 public List<Map<String,Object>> patientInvoices(Authentication a,@PathVariable UUID patientId){
  AuthPrincipal p=cashier(a);if(!exists("patient",p.clinicId(),patientId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
  return db.queryForList("SELECT id,invoice_no,status,currency_code,total,issued_at,created_at FROM invoice WHERE clinic_id=? AND patient_id=? ORDER BY created_at DESC LIMIT 100",
   p.clinicId(),patientId);
 }
 @GetMapping("/invoices/{id}") @Transactional(readOnly=true)
 public Map<String,Object> detail(Authentication a,@PathVariable UUID id){
  AuthPrincipal p=cashier(a);var out=new LinkedHashMap<String,Object>(invoice(p.clinicId(),id,false));
  out.put("lines",db.queryForList("SELECT id,description,quantity,unit_price,discount,line_total,plan_item_id,procedure_id FROM invoice_line WHERE clinic_id=? AND invoice_id=? ORDER BY created_at,id",p.clinicId(),id));
  out.put("events",db.queryForList("SELECT event_type,reference_id,reason,created_at FROM billing_event WHERE clinic_id=? AND invoice_id=? ORDER BY created_at,id",p.clinicId(),id));
  return out;
 }
 @PostMapping("/invoices") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> draft(Authentication a,@RequestBody DraftInput in){
  AuthPrincipal p=cashier(a);UUID c=p.clinicId();
  require(in!=null&&in.patientId()!=null&&in.branchId()!=null,"Patient and branch required");
  require(exists("patient",c,in.patientId())&&exists("branch",c,in.branchId()),"Patient and branch must belong to clinic");
  if(in.notes()!=null)require(in.notes().length()<=1000,"Notes too long");
  if(in.planId()!=null){
   require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM treatment_plan WHERE clinic_id=? AND id=? AND patient_id=? AND status IN ('ACCEPTED','IN_PROGRESS','COMPLETED'))",
    Boolean.class,c,in.planId(),in.patientId())),"Accepted treatment plan for patient required");
  }
  String currency=db.queryForObject("SELECT currency FROM clinic WHERE id=?",String.class,c);
  require(currency!=null&&currency.trim().matches("[A-Z]{3}"),"Clinic currency is invalid");
  UUID id=UUID.randomUUID();
  String invoiceNo="INV-"+id.toString().substring(0,8).toUpperCase(Locale.ROOT);
  db.update("INSERT INTO invoice(id,clinic_id,patient_id,branch_id,plan_id,invoice_no,notes,currency_code,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
   id,c,in.patientId(),in.branchId(),in.planId(),invoiceNo,in.notes(),currency.trim(),p.userId());
  event(p,id,"DRAFT_CREATED",null,null);
  return detail(a,id);
 }
 @PostMapping("/invoices/{id}/lines") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> addLine(Authentication a,@PathVariable UUID id,@RequestBody LineInput in){
  AuthPrincipal p=cashier(a);UUID c=p.clinicId();
  var bill=invoice(c,id,true);
  if(!"DRAFT".equals(bill.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Issued invoices are immutable");
  require(in!=null&&in.description()!=null&&!in.description().isBlank()&&in.description().length()<=255
   &&in.quantity()!=null&&in.quantity()>0&&in.quantity()<=1000&&validMoney(in.unitPrice())&&validMoney(in.discount()),
   "Invalid line details or money");
  BigDecimal gross=in.unitPrice().multiply(BigDecimal.valueOf(in.quantity()));
  require(in.discount().compareTo(gross)<=0,"Discount exceeds gross");
  if(in.procedureId()!=null)require(exists("procedure_catalog",c,in.procedureId()),"Procedure outside clinic");
  if(in.planItemId()!=null)require(Boolean.TRUE.equals(db.queryForObject(
   "SELECT EXISTS(SELECT 1 FROM treatment_plan_item i JOIN treatment_plan t ON t.clinic_id=i.clinic_id AND t.id=i.plan_id "+
   "WHERE i.clinic_id=? AND i.id=? AND t.patient_id=? AND t.id=? AND i.status='COMPLETED')",
   Boolean.class,c,in.planItemId(),bill.get("patient_id"),bill.get("plan_id"))),"Completed item on invoice plan required");
  UUID line=UUID.randomUUID();BigDecimal net=gross.subtract(in.discount());
  db.update("INSERT INTO invoice_line(id,clinic_id,invoice_id,procedure_id,plan_item_id,description,quantity,unit_price,discount,line_total) VALUES(?,?,?,?,?,?,?,?,?,?)",
   line,c,id,in.procedureId(),in.planItemId(),in.description().trim(),in.quantity(),in.unitPrice(),in.discount(),net);
  recalculate(c,id);event(p,id,"LINE_ADDED",line,null);
  return detail(a,id);
 }
 private boolean validMoney(BigDecimal x){return x!=null&&x.signum()>=0&&x.scale()<=2&&x.compareTo(new BigDecimal("9999999999.99"))<=0;}
 private void recalculate(UUID c,UUID id){
  BigDecimal total=db.queryForObject("SELECT coalesce(sum(line_total),0) FROM invoice_line WHERE clinic_id=? AND invoice_id=?",BigDecimal.class,c,id);
  db.update("UPDATE invoice SET subtotal=?,total=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND id=?",total,total,c,id);
 }
 @PostMapping("/invoices/{id}/issue") @Transactional
 public Map<String,Object> issue(Authentication a,@PathVariable UUID id,@RequestBody IssueInput in){
  AuthPrincipal p=cashier(a);UUID c=p.clinicId();var bill=invoice(c,id,true);
  require(in!=null&&in.expectedVersion()!=null&&in.expectedVersion()>=0,"Version required");
  if(!"DRAFT".equals(bill.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice already issued");
  require(((Number)bill.get("version")).longValue()==in.expectedVersion(),"Invoice version changed");
  BigDecimal total=(BigDecimal)bill.get("total");require(total.signum()>0,"Cannot issue empty invoice");
  db.update("UPDATE invoice SET status='ISSUED',issued_at=now(),updated_at=now(),version=version+1 WHERE clinic_id=? AND id=?",c,id);
  event(p,id,"ISSUED",null,null);
  return detail(a,id);
 }
}
