package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Read-only, tenant-scoped reporting; all amounts are source-record currency, not converted FX. */
@RestController @RequestMapping("/api/reports")
public class ReportsController {
 private final JdbcTemplate db;
 public ReportsController(JdbcTemplate db){this.db=db;}
 private AuthPrincipal requireAdmin(Authentication auth){
  if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN").contains(p.role()))
   throw new AccessDeniedException("Reports require clinic administrator access");
  return p;
 }
 private record Window(java.sql.Timestamp from,java.sql.Timestamp to){}
 private Window dates(String from,String to){
  try{
   LocalDate start=LocalDate.parse(from),end=LocalDate.parse(to);
   if(end.isBefore(start)||start.plusYears(2).isBefore(end))
    throw new IllegalArgumentException();
   return new Window(java.sql.Timestamp.from(start.atStartOfDay().toInstant(ZoneOffset.UTC)),
     java.sql.Timestamp.from(end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
  }catch(RuntimeException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Date range must be ISO dates spanning at most two years");}
 }
 @GetMapping("/summary") @Transactional(readOnly=true)
 public Map<String,Object> summary(Authentication auth,@RequestParam String from,@RequestParam String to){
  UUID clinic=requireAdmin(auth).clinicId();Window w=dates(from,to);
  var appointment=db.queryForMap("""
    SELECT count(*) AS scheduled,
      count(*) FILTER(WHERE status='NO_SHOW') AS no_shows,
      count(*) FILTER(WHERE status='COMPLETED') AS completed
    FROM appointment WHERE clinic_id=? AND starts_at>=? AND starts_at<?
    """,clinic,w.from(),w.to());
  var revenue=db.queryForMap("""
    SELECT count(*) AS invoices,coalesce(sum(total),0) AS invoiced
    FROM invoice WHERE clinic_id=? AND status<>'VOID' AND status<>'DRAFT'
      AND issued_at>=? AND issued_at<?
    """,clinic,w.from(),w.to());
  var cash=db.queryForMap("""
    SELECT coalesce(sum(amount),0) AS gross_collections
    FROM payment WHERE clinic_id=? AND voided_at IS NULL AND paid_at>=? AND paid_at<?
    """,clinic,w.from(),w.to());
  var refunds=db.queryForMap("""
    SELECT coalesce(sum(amount),0) AS refunds
    FROM payment_refund WHERE clinic_id=? AND refunded_at>=? AND refunded_at<?
    """,clinic,w.from(),w.to());
  var patients=db.queryForMap("""
    SELECT count(*) AS registered FROM patient WHERE clinic_id=? AND created_at>=? AND created_at<?
    """,clinic,w.from(),w.to());
  var production=db.queryForMap("""
    SELECT count(*) AS completed_items,coalesce(sum(quantity*unit_price-discount),0) AS planned_value_completed
    FROM treatment_plan_item WHERE clinic_id=? AND status='COMPLETED'
      AND updated_at>=? AND updated_at<?
    """,clinic,w.from(),w.to());
  var ar=db.queryForMap("""
    SELECT coalesce(sum(greatest(0,i.total-coalesce(p.paid,0)+coalesce(r.refunded,0))),0) AS outstanding
    FROM invoice i
    LEFT JOIN LATERAL (SELECT sum(amount) paid FROM payment WHERE clinic_id=i.clinic_id AND invoice_id=i.id AND voided_at IS NULL AND paid_at<?) p ON true
    LEFT JOIN LATERAL (SELECT sum(amount) refunded FROM payment_refund WHERE clinic_id=i.clinic_id AND invoice_id=i.id AND refunded_at<?) r ON true
    WHERE i.clinic_id=? AND i.status NOT IN ('DRAFT','VOID') AND i.issued_at<?
    """,w.to(),w.to(),clinic,w.to());
  return Map.of("from",from,"to",to,"appointments",appointment,"revenue",revenue,
    "payments",cash,"refunds",refunds,"patients",patients,"production",production,"receivables",ar);
 }
}
