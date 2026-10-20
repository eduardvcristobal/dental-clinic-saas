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

/** M7.10: manually verified external refund disbursement, never a payment void or gateway payout. */
@RestController @RequestMapping("/api/billing")
public class BillingRefundController {
 private final JdbcTemplate db;
 public BillingRefundController(JdbcTemplate db){this.db=db;}
 public record RefundInput(BigDecimal amount,String method,String reference,String reason,UUID idempotencyKey){}
 private AuthPrincipal manager(Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN").contains(p.role()))
   throw new AccessDeniedException("Financial refunds require clinic manager");
  return p;
 }
 @PostMapping("/invoices/{invoiceId}/payments/{paymentId}/refunds")
 @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> refund(Authentication auth,@PathVariable UUID invoiceId,@PathVariable UUID paymentId,
  @RequestBody RefundInput in){
  var p=manager(auth);UUID c=p.clinicId();
  if(in==null||in.amount()==null||in.amount().signum()<=0||in.amount().scale()>2||
    in.idempotencyKey()==null||in.method()==null||
    !Set.of("CASH","CARD","BANK_TRANSFER","EWALLET","OTHER").contains(in.method())||
    in.reference()==null||in.reference().trim().length()<3||in.reference().length()>255||
    in.reason()==null||in.reason().trim().length()<3||in.reason().length()>500)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid amount, method, disbursement reference, reason and idempotency key required");
  var bills=db.queryForList("SELECT id,total,status FROM invoice WHERE clinic_id=? AND id=? FOR UPDATE",c,invoiceId);
  if(bills.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found");
  var payments=db.queryForList("SELECT id,amount,voided_at FROM payment WHERE clinic_id=? AND invoice_id=? AND id=? FOR UPDATE",c,invoiceId,paymentId);
  if(payments.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found");
  var prior=db.queryForList("SELECT id,invoice_id,payment_id,amount,method,reference,reason FROM payment_refund WHERE clinic_id=? AND idempotency_key=?",
   c,in.idempotencyKey());
  if(!prior.isEmpty()){
   var old=prior.get(0);
   if(!invoiceId.equals(old.get("invoice_id"))||!paymentId.equals(old.get("payment_id"))||
      ((BigDecimal)old.get("amount")).compareTo(in.amount())!=0||
      !in.method().equals(old.get("method"))||!in.reference().trim().equals(old.get("reference"))||
      !in.reason().trim().equals(old.get("reason")))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund idempotency key already used with different request");
   return old;
  }
  if("VOID".equals(bills.get(0).get("status"))||payments.get(0).get("voided_at")!=null)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot refund a void invoice or void payment");
  BigDecimal prev=db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment_refund WHERE clinic_id=? AND payment_id=?",
   BigDecimal.class,c,paymentId);
  BigDecimal original=(BigDecimal)payments.get(0).get("amount");
  if(prev.add(in.amount()).compareTo(original)>0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund exceeds unrefunded payment value");
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO payment_refund(id,clinic_id,invoice_id,payment_id,amount,method,reference,reason,idempotency_key,recorded_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
    id,c,invoiceId,paymentId,in.amount(),in.method(),in.reference().trim(),in.reason().trim(),in.idempotencyKey(),p.userId());
  BigDecimal received=db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment WHERE clinic_id=? AND invoice_id=? AND voided_at IS NULL",
   BigDecimal.class,c,invoiceId);
  BigDecimal returned=db.queryForObject("SELECT coalesce(sum(amount),0) FROM payment_refund WHERE clinic_id=? AND invoice_id=?",
   BigDecimal.class,c,invoiceId);
  BigDecimal net=received.subtract(returned),total=(BigDecimal)bills.get(0).get("total");
  db.update("UPDATE invoice SET status=?,version=version+1,updated_at=now() WHERE clinic_id=? AND id=?",
    net.signum()==0?"ISSUED":net.compareTo(total)>=0?"PAID":"PARTIALLY_PAID",c,invoiceId);
  db.update("INSERT INTO billing_event(clinic_id,invoice_id,actor_id,event_type,reference_id,reason) VALUES(?,?,?,'REFUND_RECORDED',?,?)",
    c,invoiceId,p.userId(),id,in.reason().trim());
  return db.queryForMap("SELECT id,invoice_id,payment_id,amount,method,reference,reason,refunded_at FROM payment_refund WHERE clinic_id=? AND id=?",c,id);
 }
 @GetMapping("/invoices/{invoiceId}/refunds") @Transactional(readOnly=true)
 public List<Map<String,Object>> history(Authentication auth,@PathVariable UUID invoiceId){
  var p=manager(auth);UUID c=p.clinicId();
  if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM invoice WHERE clinic_id=? AND id=?)",
    Boolean.class,c,invoiceId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found");
  return db.queryForList("SELECT id,payment_id,amount,method,reference,reason,refunded_at FROM payment_refund WHERE clinic_id=? AND invoice_id=? ORDER BY refunded_at,id",
    c,invoiceId);
 }
}
