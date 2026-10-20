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

/** M7.11 deterministic FIFO installment allocation; no mutable paid flags. */
@RestController @RequestMapping("/api/billing")
public class BillingReconciliationController {
 private final JdbcTemplate db;
 public BillingReconciliationController(JdbcTemplate db){this.db=db;}
 private UUID clinic(Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p)||
    !Set.of("OWNER","ADMIN","FRONT_DESK").contains(p.role()))
   throw new AccessDeniedException("Billing permission required");
  return p.clinicId();
 }
 @GetMapping("/invoices/{id}/installment-reconciliation")
 @Transactional(readOnly=true)
 public Map<String,Object> reconcile(Authentication auth,@PathVariable UUID id,@RequestParam LocalDate asOf){
  UUID c=clinic(auth);
  var inv=db.queryForList("SELECT total,currency_code,status FROM invoice WHERE clinic_id=? AND id=?",c,id);
  if(inv.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice not found");
  if(asOf==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explicit clinic-local asOf date required");
  BigDecimal collected=db.queryForObject(
   "SELECT coalesce(sum(amount),0) FROM payment WHERE clinic_id=? AND invoice_id=? AND voided_at IS NULL",
   BigDecimal.class,c,id);
  BigDecimal refunded=db.queryForObject(
   "SELECT coalesce(sum(amount),0) FROM payment_refund WHERE clinic_id=? AND invoice_id=?",
   BigDecimal.class,c,id);
  BigDecimal net=collected.subtract(refunded).max(BigDecimal.ZERO);
  var rows=db.queryForList("SELECT id,due_date,amount FROM payment_schedule WHERE clinic_id=? AND invoice_id=? ORDER BY due_date,id",c,id);
  BigDecimal left=net, overdue=BigDecimal.ZERO,scheduled=BigDecimal.ZERO;
  var schedule=new ArrayList<Map<String,Object>>();
  for(var row:rows){
   BigDecimal amount=(BigDecimal)row.get("amount");scheduled=scheduled.add(amount);
   BigDecimal applied=left.min(amount);left=left.subtract(applied);
   BigDecimal due=amount.subtract(applied);
   LocalDate date=((java.sql.Date)row.get("due_date")).toLocalDate();
   if(date.isBefore(asOf))overdue=overdue.add(due);
   var item=new LinkedHashMap<String,Object>();
   item.put("id",row.get("id"));item.put("dueDate",date);item.put("amount",amount);
   item.put("allocated",applied);item.put("remaining",due);
   item.put("status",due.signum()==0?"PAID":date.isBefore(asOf)?"OVERDUE":"DUE");
   schedule.add(item);
  }
  BigDecimal invoiceTotal=(BigDecimal)inv.get(0).get("total");
  return Map.of("invoiceId",id,"asOf",asOf,"currency",inv.get(0).get("currency_code"),
   "netCollected",net,"balance",invoiceTotal.subtract(net),"scheduledTotal",scheduled,
   "unallocatedCollections",left,"overdue",overdue,"installments",schedule);
 }
}
