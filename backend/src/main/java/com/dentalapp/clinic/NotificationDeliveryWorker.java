package com.dentalapp.clinic;

import com.dentalapp.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicitly gated, tenant-scoped queue dispatch. No external delivery by default. */
@Component
public final class NotificationDeliveryWorker {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final ProviderNotificationTransport provider;
 private final boolean enabled;
 private final String clinicIds;
 public NotificationDeliveryWorker(JdbcTemplate db,org.springframework.transaction.PlatformTransactionManager manager,
  ProviderNotificationTransport provider,@Value("${app.notifications.worker-enabled:false}") boolean enabled,
  @Value("${app.notifications.worker-clinic-ids:}") String clinicIds) {
  this.db=db;this.tx=new TransactionTemplate(manager);this.provider=provider;this.enabled=enabled;this.clinicIds=clinicIds;
 }
 @Scheduled(fixedDelayString="${app.notifications.worker-delay-ms:60000}")
 public void poll(){
  if(!enabled)return;
  // Clinic IDs are selected solely from the public clinic registry; every notification read/write
  // happens inside the tenant context and a fresh JDBC transaction.
  List<UUID> clinics=Arrays.stream(clinicIds.split(",")).map(String::trim).filter(s->!s.isEmpty()).map(UUID::fromString).distinct().limit(1000).toList();
  for(UUID clinic:clinics)TenantContext.runAs(clinic,()->{for(int i=0;i<10;i++)if(!dispatchOne(clinic))break;});
 }
 boolean dispatchOne(UUID clinic){
  UUID id=tx.execute(s->{
   List<UUID> ids=db.queryForList("""
    SELECT id FROM notification_delivery
    WHERE clinic_id=? AND status IN ('PENDING','FAILED')
      AND scheduled_for<=now() AND (next_attempt_at IS NULL OR next_attempt_at<=now())
      AND attempts<10 ORDER BY scheduled_for,id FOR UPDATE SKIP LOCKED LIMIT 1
    """,UUID.class,clinic);
   if(ids.isEmpty())return null;
   UUID next=ids.get(0);
   db.update("UPDATE notification_delivery SET status='CLAIMED',lease_until=now()+interval '60 seconds',attempts=attempts+1,last_attempt_at=now() WHERE clinic_id=? AND id=?",clinic,next);
   return next;
  });
  if(id==null)return false;
  record Claim(UUID patient,UUID appointment,String channel,String destination,UUID key,Instant appointmentStart){}
  Claim claim=tx.execute(s->{
   var rows=db.queryForList("""
     SELECT d.patient_id,d.appointment_id,d.channel,d.idempotency_key,
       p.email,p.phone,p.deleted_at,a.starts_at AS appointment_start,a.status AS appointment_status,
       d.category,d.scheduled_for,np.enabled,np.email_enabled,np.sms_enabled,np.quiet_start,np.quiet_end,
       c.timezone,co.consented
     FROM notification_delivery d JOIN patient p ON (p.clinic_id=d.clinic_id AND p.id=d.patient_id)
       LEFT JOIN appointment a ON (a.clinic_id=d.clinic_id AND a.id=d.appointment_id)
       JOIN clinic c ON c.id=d.clinic_id
       LEFT JOIN notification_policy np ON np.clinic_id=d.clinic_id
       LEFT JOIN notification_consent co ON (co.clinic_id=d.clinic_id AND co.patient_id=d.patient_id AND co.channel=d.channel)
     WHERE d.clinic_id=? AND d.id=? AND d.status='CLAIMED' FOR UPDATE OF d
    """,clinic,id);
   if(rows.isEmpty())return null;
   var r=rows.get(0);
   String channel=(String)r.get("channel"),destination=(String)r.get(channel.equals("EMAIL")?"email":"phone");
   boolean valid=channel.equals("EMAIL")?NotificationDeliveryRules.validEmailDestination(destination):NotificationDeliveryRules.validSmsDestination(destination);
   boolean active=Boolean.TRUE.equals(r.get("enabled"))&&Boolean.TRUE.equals(r.get(channel.equals("EMAIL")?"email_enabled":"sms_enabled"));
   boolean appointmentOk=r.get("appointment_id")==null||(
     Set.of("BOOKED","CONFIRMED").contains(r.get("appointment_status")) &&
     ((Timestamp)r.get("appointment_start")).toInstant().isAfter(Instant.now()));
   if(!valid||!active||!Boolean.TRUE.equals(r.get("consented"))||r.get("deleted_at")!=null||!appointmentOk){
    db.update("UPDATE notification_delivery SET status='SUPPRESSED',suppressed_at=now(),lease_until=NULL,failure_reason='No longer eligible' WHERE clinic_id=? AND id=?",clinic,id);
    return null;
   }
   ZoneId zone=ZoneId.of((String)r.get("timezone"));
   LocalTime start=((java.sql.Time)r.get("quiet_start")).toLocalTime(),end=((java.sql.Time)r.get("quiet_end")).toLocalTime();
   Instant allowed=NotificationDeliveryRules.afterQuietHours(Instant.now(),zone,start,end);
   if(allowed.isAfter(Instant.now())){
    db.update("UPDATE notification_delivery SET status='PENDING',scheduled_for=?,lease_until=NULL,attempts=GREATEST(0,attempts-1) WHERE clinic_id=? AND id=?",Timestamp.from(allowed),clinic,id);
    return null;
   }
   Instant appointmentStart=r.get("appointment_start")==null?null:((Timestamp)r.get("appointment_start")).toInstant();
   return new Claim((UUID)r.get("patient_id"),(UUID)r.get("appointment_id"),channel,destination,(UUID)r.get("idempotency_key"),appointmentStart);
  });
  if(claim==null)return true;
  try{
   // Generic messages intentionally exclude diagnoses, procedures and patient names.
   NotificationTransport.Channel channel=NotificationTransport.Channel.valueOf(claim.channel());
   var result=provider.deliver(new NotificationTransport.Delivery(clinic,claim.patient(),channel,claim.destination(),
      claim.appointment()==null?"Please contact your clinic regarding your upcoming recall.":"Please contact your clinic regarding your upcoming appointment.",claim.key()));
   tx.executeWithoutResult(s->db.update("""
    UPDATE notification_delivery SET status='ACCEPTED',provider_reference=?,provider_name=?,
      provider_status='ACCEPTED',sent_at=?,lease_until=NULL,next_attempt_at=NULL
    WHERE clinic_id=? AND id=? AND status='CLAIMED'
   """,result.providerReference(),channel==NotificationTransport.Channel.EMAIL?"resend":"twilio",
    Timestamp.from(result.acceptedAt()),clinic,id));
  }catch(RuntimeException e){
   tx.executeWithoutResult(s->db.update("""
    UPDATE notification_delivery SET status='SUPPRESSED',
      next_attempt_at=NULL,
      lease_until=NULL,failure_reason='Provider outcome uncertain; reconcile manually before any retry'
    WHERE clinic_id=? AND id=? AND status='CLAIMED'
   """,clinic,id));
  }
  return true;
 }
}
