package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** M8 notification governance. Safe by default: no external email/SMS transport is invoked. */
@RestController @RequestMapping("/api/notifications")
public class NotificationController {
 private final JdbcTemplate db;
 public NotificationController(JdbcTemplate db){this.db=db;}
 public record ConsentInput(UUID patientId,String channel,boolean consented){}
 public record PolicyInput(boolean enabled,boolean emailEnabled,boolean smsEnabled,int reminderHoursBefore){}
 public record RecallInput(UUID patientId,LocalDate dueDate,String reason){}
 public record ResponseInput(String response){}
 private AuthPrincipal staff(Authentication a){
  if(a==null||!(a.getPrincipal() instanceof AuthPrincipal p)||!Set.of("OWNER","ADMIN","FRONT_DESK","DENTIST").contains(p.role()))
   throw new AccessDeniedException("Clinic staff required");
  return p;
 }
 private AuthPrincipal manager(Authentication a){
  var p=staff(a);if(!Set.of("OWNER","ADMIN").contains(p.role()))throw new AccessDeniedException("Clinic manager required");
  return p;
 }
 private void check(boolean ok,String reason){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
 @GetMapping("/policy") @Transactional(readOnly=true)
 public Map<String,Object> policy(Authentication a){
  var p=staff(a);var rows=db.queryForList("SELECT enabled,email_enabled,sms_enabled,reminder_hours_before FROM notification_policy WHERE clinic_id=?",p.clinicId());
  if(rows.isEmpty())return Map.of("enabled",false,"email_enabled",false,"sms_enabled",false,"reminder_hours_before",24);
  return rows.get(0);
 }
 @PutMapping("/policy") @Transactional
 public Map<String,Object> configure(Authentication a,@RequestBody PolicyInput in){
  var p=manager(a);check(in!=null&&in.reminderHoursBefore()>=1&&in.reminderHoursBefore()<=720,"Invalid reminder lead time");
  // Activation flags configure intent; no provider credentials means no transport.
  db.update("INSERT INTO notification_policy(clinic_id,enabled,email_enabled,sms_enabled,reminder_hours_before) VALUES(?,?,?,?,?) "+
   "ON CONFLICT(clinic_id) DO UPDATE SET enabled=excluded.enabled,email_enabled=excluded.email_enabled,sms_enabled=excluded.sms_enabled,reminder_hours_before=excluded.reminder_hours_before,updated_at=now()",
   p.clinicId(),in.enabled(),in.emailEnabled(),in.smsEnabled(),in.reminderHoursBefore());
  return policy(a);
 }
 @PutMapping("/consents") @Transactional
 public Map<String,Object> consent(Authentication a,@RequestBody ConsentInput in){
  var p=manager(a);check(in!=null&&in.patientId()!=null&&Set.of("EMAIL","SMS").contains(in.channel()),"Valid patient and channel required");
  check(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=?)",
   Boolean.class,p.clinicId(),in.patientId())),"Patient outside clinic");
  db.update("INSERT INTO notification_consent(clinic_id,patient_id,channel,consented) VALUES(?,?,?,?) "+
   "ON CONFLICT(clinic_id,patient_id,channel) DO UPDATE SET consented=excluded.consented,updated_at=now()",
   p.clinicId(),in.patientId(),in.channel(),in.consented());
  if(!in.consented())db.update("UPDATE notification_delivery SET status='SUPPRESSED',failure_reason='Consent withdrawn' WHERE clinic_id=? AND patient_id=? AND channel=? AND status IN('PENDING','FAILED')",
   p.clinicId(),in.patientId(),in.channel());
  return Map.of("patientId",in.patientId(),"channel",in.channel(),"consented",in.consented());
 }
 @GetMapping("/consents/{patientId}") @Transactional(readOnly=true)
 public List<Map<String,Object>> consents(Authentication a,@PathVariable UUID patientId){
  var p=staff(a);return db.queryForList("SELECT channel,consented,updated_at FROM notification_consent WHERE clinic_id=? AND patient_id=?",p.clinicId(),patientId);
 }
 @PostMapping("/appointments/{appointmentId}/schedule") @Transactional
 public Map<String,Object> schedule(Authentication a,@PathVariable UUID appointmentId,@RequestParam String channel){
  var p=staff(a);check(Set.of("EMAIL","SMS").contains(channel),"Channel required");
  var rows=db.queryForList("SELECT patient_id,starts_at,status FROM appointment WHERE clinic_id=? AND id=? FOR UPDATE",p.clinicId(),appointmentId);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found");
  var row=rows.get(0);UUID patient=(UUID)row.get("patient_id");
  check(Set.of("BOOKED","CONFIRMED").contains(row.get("status")),"Inactive appointment cannot be reminded");
  boolean optIn=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM notification_consent WHERE clinic_id=? AND patient_id=? AND channel=? AND consented)",
   Boolean.class,p.clinicId(),patient,channel));
  boolean enabled=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM notification_policy WHERE clinic_id=? AND enabled AND "+(channel.equals("EMAIL")?"email_enabled":"sms_enabled")+")",
   Boolean.class,p.clinicId()));
  check(optIn&&enabled,"Notification channel must be enabled and patient consent verified");
  Integer hours=db.queryForObject("SELECT reminder_hours_before FROM notification_policy WHERE clinic_id=?",Integer.class,p.clinicId());
  Instant start=((java.sql.Timestamp)row.get("starts_at")).toInstant();
  Instant due=start.minus(Duration.ofHours(hours));
  check(start.isAfter(Instant.now())&&due.isAfter(Instant.now()),"Reminder time has passed");
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO notification_delivery(id,clinic_id,patient_id,appointment_id,channel,category,scheduled_for) VALUES(?,?,?,?,?,'APPOINTMENT_REMINDER',?) "+
   "ON CONFLICT(clinic_id,appointment_id,channel,category) DO UPDATE SET scheduled_for=excluded.scheduled_for,status='PENDING',attempts=0,next_attempt_at=NULL,lease_until=NULL,failure_reason=NULL WHERE notification_delivery.status IN('PENDING','FAILED','SUPPRESSED')",
   id,p.clinicId(),patient,appointmentId,channel,java.sql.Timestamp.from(due));
  return Map.of("appointmentId",appointmentId,"channel",channel,"scheduledFor",due,"deliveryMode","DISABLED_PENDING_PROVIDER");
 }
 @PostMapping("/recalls") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> recall(Authentication a,@RequestBody RecallInput in){
  var p=staff(a);check(in!=null&&in.patientId()!=null&&in.dueDate()!=null&&in.reason()!=null&&in.reason().length()<=200&&!in.reason().isBlank(),"Valid recall required");
  check(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=?)",Boolean.class,p.clinicId(),in.patientId())),"Patient outside clinic");
  UUID id=UUID.randomUUID();db.update("INSERT INTO recall(id,clinic_id,patient_id,due_date,reason) VALUES(?,?,?,?,?)",id,p.clinicId(),in.patientId(),in.dueDate(),in.reason().trim());
  return Map.of("id",id,"patientId",in.patientId(),"dueDate",in.dueDate());
 }
 @GetMapping("/recalls") @Transactional(readOnly=true)
 public List<Map<String,Object>> recalls(Authentication a){
  var p=staff(a);return db.queryForList("SELECT id,patient_id,due_date,completed FROM recall WHERE clinic_id=? AND NOT completed ORDER BY due_date,id LIMIT 200",p.clinicId());
 }
 @GetMapping("/queue") @Transactional(readOnly=true)
 public List<Map<String,Object>> queue(Authentication a){
  var p=staff(a);return db.queryForList("SELECT id,patient_id,appointment_id,recall_id,channel,category,status,scheduled_for,attempts,failure_reason FROM notification_delivery WHERE clinic_id=? ORDER BY created_at DESC LIMIT 100",p.clinicId());
 }
 @PostMapping("/responses/{deliveryId}") @Transactional
 public Map<String,Object> response(Authentication a,@PathVariable UUID deliveryId,@RequestBody ResponseInput in){
  var p=staff(a);check(in!=null&&Set.of("CONFIRM","CANCEL_REQUEST","RESCHEDULE_REQUEST").contains(in.response()),"Invalid response");
  var rows=db.queryForList("SELECT appointment_id FROM notification_delivery WHERE clinic_id=? AND id=?",p.clinicId(),deliveryId);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery not found");
  db.update("INSERT INTO notification_response(clinic_id,delivery_id,response) VALUES(?,?,?) ON CONFLICT(clinic_id,delivery_id) DO NOTHING",
   p.clinicId(),deliveryId,in.response());
  // A response is a staff-recorded request. Scheduling conflicts and confirmation remain governed by M3.
  return Map.of("deliveryId",deliveryId,"response",in.response(),"appointmentStatusChanged",false);
 }
}
