package com.dentalapp.clinic;

import com.dentalapp.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Signed callbacks are correlated only inside explicitly configured tenant RLS contexts. */
@Component
public final class NotificationWebhookReconciler {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final List<UUID> clinics;
 public NotificationWebhookReconciler(JdbcTemplate db,PlatformTransactionManager manager,
     @Value("${app.notifications.worker-clinic-ids:}") String ids){
  this.db=db;this.tx=new TransactionTemplate(manager);
  this.clinics=Arrays.stream(ids.split(",")).map(String::trim).filter(s->!s.isEmpty())
     .map(UUID::fromString).distinct().limit(1000).toList();
 }
 public boolean record(String provider,String reference,String eventId,String eventType,Instant occurredAt){
  if(!Set.of("resend","twilio").contains(provider)||reference==null||!reference.matches("[A-Za-z0-9_-]{8,128}")
      ||eventId==null||eventId.length()>200||eventId.isBlank()
      ||!Set.of("ACCEPTED","SENT","DELIVERED","BOUNCED","FAILED","COMPLAINED","UNSUBSCRIBED").contains(eventType)
      ||occurredAt==null)return false;
  for(UUID clinic:clinics){
   boolean found=TenantContext.callAs(clinic,()->Boolean.TRUE.equals(tx.execute(s->{
    List<UUID> deliveries=db.queryForList("""
      SELECT id FROM notification_delivery WHERE clinic_id=? AND provider_name=? AND provider_reference=?
      FOR UPDATE
      """,UUID.class,clinic,provider,reference);
    if(deliveries.isEmpty())return false;
    UUID id=deliveries.get(0);
    int inserted=db.update("""
      INSERT INTO notification_delivery_event(clinic_id,delivery_id,provider_name,provider_event_id,event_type,occurred_at)
      VALUES(?,?,?,?,?,?) ON CONFLICT(provider_name,provider_event_id) DO NOTHING
      """,clinic,id,provider,eventId,eventType,Timestamp.from(occurredAt));
    if(inserted==0)return true;
    String next= switch(eventType){
     case "DELIVERED" -> "DELIVERED";
     case "BOUNCED","FAILED","COMPLAINED","UNSUBSCRIBED" -> "SUPPRESSED";
     default -> "ACCEPTED";
    };
    db.update("""
      UPDATE notification_delivery SET status=CASE
        WHEN status IN ('DELIVERED','SUPPRESSED') THEN status
        ELSE ? END,
        provider_status=?, provider_event_at=?
      WHERE clinic_id=? AND id=?
      """,next,eventType,Timestamp.from(occurredAt),clinic,id);
    if(Set.of("COMPLAINED","UNSUBSCRIBED").contains(eventType)){
      db.update("""
       UPDATE notification_consent SET consented=false,updated_at=now()
       WHERE clinic_id=? AND (patient_id,channel) IN
         (SELECT patient_id,channel FROM notification_delivery WHERE clinic_id=? AND id=?)
      """,clinic,clinic,id);
    }
    return true;
   })));
   if(found)return true;
  }
  return false;
 }
}
