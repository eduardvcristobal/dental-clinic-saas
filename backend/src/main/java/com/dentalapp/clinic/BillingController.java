package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 public record PaymentInput(BigDecimal amount,String method,String reference,UUID idempotencyKey){}
 public record InstallmentInput(LocalDate dueDate,BigDecimal amount){}
 public record VoidInput(String reason){}
 public record PlanChargeInput(UUID planItemId){}
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
  require(in.planItemId()==null,"Import completed treatment items with /plan-charge to preserve recorded prices");
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
 @GetMapping("/invoices/{id}/balance") @Transactional(readOnly=true)
 public Map<String,Object> balance(Authentication a,@PathVariable UUID id){
  AuthPrincipal p=cashier(a);var bill=invoice(p.clinicId(),id,false);
  BigDecimal paid=db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment WHERE clinic_id=? AND invoice_id=? AND voided_at IS NULL",
   BigDecimal.class,p.clinicId(),id);
  BigDecimal total=(BigDecimal)bill.get("total");
  return Map.of("invoiceId",id,"currency",bill.get("currency_code"),"total",total,"paid",paid,"balance",total.subtract(paid));
 }
 @PostMapping("/invoices/{id}/payments") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> payment(Authentication a,@PathVariable UUID id,@RequestBody PaymentInput in){
  AuthPrincipal p=cashier(a);UUID c=p.clinicId();var bill=invoice(c,id,true);
  require(in!=null&&validMoney(in.amount())&&in.amount().signum()>0&&in.method()!=null&&
   Set.of("CASH","CARD","BANK_TRANSFER","EWALLET","HMO","OTHER").contains(in.method())&&in.idempotencyKey()!=null,
   "Positive amount, valid payment method and idempotency key required");
  require(in.reference()==null||in.reference().length()<=255,"Reference too long");
  var previous=db.queryForList("SELECT id,amount,method,reference FROM payment WHERE clinic_id=? AND invoice_id=? AND idempotency_key=?",
   c,id,in.idempotencyKey());
  if(!previous.isEmpty()){
   var old=previous.get(0);
   if(((BigDecimal)old.get("amount")).compareTo(in.amount())!=0 || !Objects.equals(old.get("method"),in.method()) ||
       !Objects.equals(old.get("reference"),in.reference()))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key reused with different payment");
   return balance(a,id);
  }
  if(!Set.of("ISSUED","PARTIALLY_PAID").contains(bill.get("status")))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice is not collectible");
  BigDecimal paid=db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment WHERE clinic_id=? AND invoice_id=? AND voided_at IS NULL",
   BigDecimal.class,c,id);
  BigDecimal total=(BigDecimal)bill.get("total");
  if(paid.add(in.amount()).compareTo(total)>0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment exceeds outstanding balance");
  UUID paymentId=UUID.randomUUID();
  db.update("INSERT INTO payment(id,clinic_id,invoice_id,patient_id,amount,method,reference,received_by,idempotency_key) "+
   "VALUES(?,?,?,?,?,?,?,?,?)",paymentId,c,id,bill.get("patient_id"),in.amount(),in.method(),in.reference(),p.userId(),in.idempotencyKey());
  db.update("UPDATE invoice SET status=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND id=?",
   paid.add(in.amount()).compareTo(total)==0?"PAID":"PARTIALLY_PAID",c,id);
  event(p,id,"PAYMENT_RECORDED",paymentId,null);
  return balance(a,id);
 }

 @GetMapping("/invoices/{id}/installments") @Transactional(readOnly=true)
 public List<Map<String,Object>> installments(Authentication a,@PathVariable UUID id){
  AuthPrincipal p=cashier(a);invoice(p.clinicId(),id,false);
  return db.queryForList("SELECT id,due_date,amount,paid FROM payment_schedule WHERE clinic_id=? AND invoice_id=? ORDER BY due_date,id",p.clinicId(),id);
 }
 @PostMapping("/invoices/{id}/installments") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public List<Map<String,Object>> addInstallment(Authentication a,@PathVariable UUID id,@RequestBody InstallmentInput in){
  AuthPrincipal p=cashier(a);UUID c=p.clinicId();var bill=invoice(c,id,true);
  require(in!=null&&in.dueDate()!=null&&validMoney(in.amount())&&in.amount().signum()>0,
   "Valid due date and positive amount required");
  if(!Set.of("ISSUED","PARTIALLY_PAID").contains(bill.get("status")))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice cannot be scheduled");
  BigDecimal scheduled=db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment_schedule WHERE clinic_id=? AND invoice_id=?",
   BigDecimal.class,c,id);
  BigDecimal outstanding=(BigDecimal)bill.get("total");
  outstanding=outstanding.subtract(db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment WHERE clinic_id=? AND invoice_id=? AND voided_at IS NULL",
   BigDecimal.class,c,id));
  if(scheduled.add(in.amount()).compareTo(outstanding)>0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Installments exceed outstanding balance");
  UUID installment=UUID.randomUUID();
  db.update("INSERT INTO payment_schedule(id,clinic_id,invoice_id,due_date,amount) VALUES(?,?,?,?,?)",
   installment,c,id,in.dueDate(),in.amount());
  event(p,id,"INSTALLMENT_ADDED",installment,null);
  return installments(a,id);
 }

 @GetMapping("/invoices/{id}/payments") @Transactional(readOnly=true)
 public List<Map<String,Object>> paymentHistory(Authentication a,@PathVariable UUID id){
  AuthPrincipal p=cashier(a);invoice(p.clinicId(),id,false);
  return db.queryForList("SELECT id,amount,method,reference,paid_at,voided_at,void_reason FROM payment "+
   "WHERE clinic_id=? AND invoice_id=? ORDER BY paid_at,id",p.clinicId(),id);
 }
 @GetMapping("/invoices/{id}/receipts/{paymentId}") @Transactional(readOnly=true)
 public Map<String,Object> receipt(Authentication a,@PathVariable UUID id,@PathVariable UUID paymentId){
  AuthPrincipal p=cashier(a);var bill=invoice(p.clinicId(),id,false);
  var rows=db.queryForList("SELECT id,amount,method,reference,paid_at,voided_at FROM payment WHERE clinic_id=? AND invoice_id=? AND id=?",
   p.clinicId(),id,paymentId);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found");
  var out=new LinkedHashMap<String,Object>(rows.get(0));
  out.put("invoiceNo",bill.get("invoice_no"));out.put("currency",bill.get("currency_code"));
  out.put("patientId",bill.get("patient_id"));out.put("receiptStatus",out.get("voided_at")==null?"VALID":"VOID");
  out.put("notice","Payment acknowledgment only. Not an official tax invoice or receipt.");
  return out;
 }
 @GetMapping("/patients/{patientId}/statement") @Transactional(readOnly=true)
 public List<Map<String,Object>> statement(Authentication a,@PathVariable UUID patientId){
  AuthPrincipal p=cashier(a);if(!exists("patient",p.clinicId(),patientId))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found");
  return db.queryForList("SELECT i.id,i.invoice_no,i.currency_code,i.status,i.total,"+
   "coalesce((SELECT sum(pay.amount) FROM payment pay WHERE pay.clinic_id=i.clinic_id AND pay.invoice_id=i.id AND pay.voided_at IS NULL),0) AS paid,"+
   "i.total-coalesce((SELECT sum(pay.amount) FROM payment pay WHERE pay.clinic_id=i.clinic_id AND pay.invoice_id=i.id AND pay.voided_at IS NULL),0) AS balance "+
   "FROM invoice i WHERE i.clinic_id=? AND i.patient_id=? ORDER BY i.created_at DESC LIMIT 200",p.clinicId(),patientId);
 }

 private AuthPrincipal financeManager(Authentication a){
  AuthPrincipal p=cashier(a);
  if(!Set.of("OWNER","ADMIN").contains(p.role()))throw new AccessDeniedException("Financial reversal requires manager");
  return p;
 }
 private void reason(VoidInput in){require(in!=null&&in.reason()!=null&&in.reason().trim().length()>=3&&in.reason().length()<=500,"Reason required (3–500 characters)");}
 @PostMapping("/invoices/{id}/payments/{paymentId}/void") @Transactional
 public Map<String,Object> voidPayment(Authentication a,@PathVariable UUID id,@PathVariable UUID paymentId,@RequestBody VoidInput in){
  AuthPrincipal p=financeManager(a);reason(in);UUID c=p.clinicId();var bill=invoice(c,id,true);
  var existing=db.queryForList("SELECT id,voided_at FROM payment WHERE clinic_id=? AND invoice_id=? AND id=? FOR UPDATE",c,id,paymentId);
  if(existing.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found");
  if(existing.get(0).get("voided_at")!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment already voided");
  if("VOID".equals(bill.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice already voided");
  db.update("UPDATE payment SET voided_at=now(),void_reason=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND invoice_id=? AND id=?",
   in.reason().trim(),c,id,paymentId);
  BigDecimal paid=db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment WHERE clinic_id=? AND invoice_id=? AND voided_at IS NULL",
   BigDecimal.class,c,id);
  BigDecimal total=(BigDecimal)bill.get("total");
  db.update("UPDATE invoice SET status=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND id=?",
   paid.signum()==0?"ISSUED":paid.compareTo(total)==0?"PAID":"PARTIALLY_PAID",c,id);
  event(p,id,"PAYMENT_VOIDED",paymentId,in.reason().trim());
  return balance(a,id);
 }
 @PostMapping("/invoices/{id}/void") @Transactional
 public Map<String,Object> voidInvoice(Authentication a,@PathVariable UUID id,@RequestBody VoidInput in){
  AuthPrincipal p=financeManager(a);reason(in);UUID c=p.clinicId();var bill=invoice(c,id,true);
  if("VOID".equals(bill.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invoice already voided");
  if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM payment WHERE clinic_id=? AND invoice_id=? AND voided_at IS NULL)",
   Boolean.class,c,id)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Void or refund active collections first");
  db.update("UPDATE invoice SET status='VOID',voided_at=now(),void_reason=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND id=?",
   in.reason().trim(),c,id);
  event(p,id,"INVOICE_VOIDED",null,in.reason().trim());
  return detail(a,id);
 }

 @PostMapping("/invoices/{id}/plan-charge") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> importPlanCharge(Authentication a,@PathVariable UUID id,@RequestBody PlanChargeInput in){
  AuthPrincipal p=cashier(a);UUID c=p.clinicId();var bill=invoice(c,id,true);
  if(!"DRAFT".equals(bill.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft invoice can import charges");
  require(in!=null&&in.planItemId()!=null,"Completed plan item required");
  require(bill.get("plan_id")!=null,"Invoice must be linked to a treatment plan");
  var rows=db.queryForList("SELECT i.id,i.procedure_id,i.quantity,i.unit_price,i.discount,p.name AS procedure_name "+
   "FROM treatment_plan_item i JOIN treatment_plan t ON t.clinic_id=i.clinic_id AND t.id=i.plan_id "+
   "JOIN procedure_catalog p ON p.clinic_id=i.clinic_id AND p.id=i.procedure_id "+
   "WHERE i.clinic_id=? AND i.id=? AND i.plan_id=? AND t.patient_id=? AND i.status='COMPLETED'",
   c,in.planItemId(),bill.get("plan_id"),bill.get("patient_id"));
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Completed treatment item from same patient and plan required");
  var item=rows.get(0);
  UUID line=UUID.randomUUID();BigDecimal gross=((BigDecimal)item.get("unit_price")).multiply(BigDecimal.valueOf(((Number)item.get("quantity")).longValue()));
  BigDecimal discount=(BigDecimal)item.get("discount");BigDecimal net=gross.subtract(discount);
  if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM invoice_line WHERE clinic_id=? AND plan_item_id=?)",
   Boolean.class,c,in.planItemId())))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Treatment item already billed");
  db.update("INSERT INTO invoice_line(id,clinic_id,invoice_id,procedure_id,plan_item_id,description,quantity,unit_price,discount,line_total) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?)",line,c,id,item.get("procedure_id"),in.planItemId(),item.get("procedure_name"),
   item.get("quantity"),item.get("unit_price"),discount,net);
  recalculate(c,id);event(p,id,"LINE_ADDED",line,"Imported completed treatment item "+in.planItemId());
  return detail(a,id);
 }

}
