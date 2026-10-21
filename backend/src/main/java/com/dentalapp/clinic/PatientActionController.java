package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Patient responses are requests only: NEVER directly cancel, confirm, or move an appointment.
 * Plaintext high-entropy tokens are returned once at issuance; only hashes are persisted.
 */
@RestController
@RequestMapping("/api/patient-actions")
public final class PatientActionController {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final PatientActionTokens tokens;
 private final boolean enabled;
 public record IssueInput(UUID deliveryId,String action){}
 public record RedeemInput(UUID clinicId,String token){}
 public PatientActionController(JdbcTemplate db,PlatformTransactionManager manager,PatientActionTokens tokens,
    @Value("${app.notifications.patient-actions-enabled:false}") boolean enabled){
   this.db=db;this.tx=new TransactionTemplate(manager);this.tokens=tokens;this.enabled=enabled;
 }
 @PostMapping("/issue")
 public Map<String,Object> issue(Authentication a,@RequestBody IssueInput in){
  if(a==null||!(a.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN","FRONT_DESK").contains(p.role()))
    throw new AccessDeniedException("Clinic staff only");
  if(in==null||in.deliveryId()==null||!Set.of("CONFIRM","CANCEL_REQUEST","RESCHEDULE_REQUEST","OPT_OUT").contains(in.action()))
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid action");
  if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Patient responses disabled");
  String plaintext=tokens.generate();
  byte[] hash=tokens.hash(plaintext);
  UUID id=tx.execute(s->{
   List<UUID> rows=db.queryForList("SELECT id FROM notification_delivery WHERE clinic_id=? AND id=? AND status IN ('ACCEPTED','DELIVERED') FOR UPDATE",UUID.class,p.clinicId(),in.deliveryId());
   if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Eligible delivery not found");
   // Serialize issuance against the delivery row and cap active links per delivery.
   // A staff account must not be able to mint an unlimited number of bearer tokens.
   Integer active=db.queryForObject("""
     SELECT count(*) FROM notification_patient_action
     WHERE clinic_id=? AND delivery_id=? AND consumed_at IS NULL AND expires_at>now()
     """,Integer.class,p.clinicId(),in.deliveryId());
   if(active!=null&&active>=5)
     throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many active patient links");
   UUID actionId=UUID.randomUUID();
   db.update("INSERT INTO notification_patient_action(id,clinic_id,delivery_id,token_hash,expires_at,requested_action) VALUES(?,?,?,?,?,?)",
     actionId,p.clinicId(),in.deliveryId(),hash,Timestamp.from(Instant.now().plus(Duration.ofHours(24))),in.action());
   return actionId;
  });
  return Map.of("id",id,"clinicId",p.clinicId(),"token",plaintext,"expiresInSeconds",86400);
 }
 @PostMapping("/redeem")
 public Map<String,Object> redeem(@RequestBody RedeemInput in){
   if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Patient responses disabled");
   if(in==null||in.clinicId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid patient action");
   // Avoid leaking whether a guessed token ever existed for this tenant.
   byte[] hash;
   try{hash=tokens.hash(in.token());}
   catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid patient action");}
   // In-memory guard is defense-in-depth; the persistent counter applies across replicas.
   if(!PatientRedemptionThrottle.allow(in.clinicId(),Instant.now()))
     throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many patient action attempts");
   return TenantContext.callAs(in.clinicId(),()->{
    // Commit rate budget independently so a rejected or expired token still consumes quota.
    int budget=tx.execute(s->db.queryForObject("""
      INSERT INTO notification_redemption_limit(clinic_id,bucket_start,attempts)
      VALUES (?,date_trunc('minute',now()),1)
      ON CONFLICT(clinic_id,bucket_start)
        DO UPDATE SET attempts=notification_redemption_limit.attempts+1
      RETURNING attempts
      """,Integer.class,in.clinicId()));
    if(budget>30)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many patient action attempts");
    return tx.execute(s->{
    var rows=db.queryForList("""
      SELECT id,delivery_id,requested_action FROM notification_patient_action
      WHERE clinic_id=? AND token_hash=? AND consumed_at IS NULL AND expires_at>now()
      FOR UPDATE
    """,in.clinicId(),hash);
    if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.GONE,"Invalid or expired action");
    var row=rows.get(0);
    UUID delivery=(UUID)row.get("delivery_id");
    String action=(String)row.get("requested_action");
    db.update("UPDATE notification_patient_action SET consumed_at=now() WHERE clinic_id=? AND id=? AND consumed_at IS NULL",in.clinicId(),row.get("id"));
    if(action.equals("OPT_OUT")){
      db.update("""
        UPDATE notification_consent SET consented=false,updated_at=now()
        WHERE clinic_id=? AND (patient_id,channel) IN
          (SELECT patient_id,channel FROM notification_delivery WHERE clinic_id=? AND id=?)
      """,in.clinicId(),in.clinicId(),delivery);
      db.update("""
        UPDATE notification_delivery SET status='SUPPRESSED',failure_reason='Patient opted out',suppressed_at=now()
        WHERE clinic_id=? AND status IN ('PENDING','FAILED','CLAIMED') AND (patient_id,channel) IN
          (SELECT patient_id,channel FROM notification_delivery WHERE clinic_id=? AND id=?)
      """,in.clinicId(),in.clinicId(),delivery);
    }else{
      db.update("INSERT INTO notification_response(clinic_id,delivery_id,response) VALUES(?,?,?) ON CONFLICT(clinic_id,delivery_id) DO NOTHING",
        in.clinicId(),delivery,action);
    }
    return Map.<String,Object>of("accepted",true,"appointmentChanged",false);
    });
   });
 }
}
