package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** M3 appointment endpoints. All reads/writes are clinic-scoped; PostgreSQL exclusion constraints
 * remain the authoritative defense against concurrent dentist/chair double-booking. */
@RestController @RequestMapping("/api/scheduling")
public class SchedulingController {
    private final JdbcTemplate db;
    public SchedulingController(JdbcTemplate db) { this.db=db; }
    public record TypeInput(String name,Integer defaultMinutes,Integer bufferBeforeMinutes,Integer bufferAfterMinutes,String color) {}
    public record PolicyInput(Integer slotMinutes,Integer minimumNoticeMinutes,Integer bookingHorizonDays) {}
    public record AvailabilityInput(UUID dentistId,UUID branchId,Integer dayOfWeek,LocalTime startsAt,LocalTime endsAt) {}
    public record BlockInput(UUID resourceId,Instant startsAt,Instant endsAt,String reason) {}
    public record BookingInput(UUID patientId,UUID branchId,UUID dentistId,UUID chairId,UUID appointmentTypeId,
                               Instant startsAt,Instant endsAt,String reason) {}
    public record StatusInput(String status,String reason) {}
    public record RescheduleInput(Instant startsAt,Instant endsAt,String reason) {}
    public record WaitlistInput(UUID patientId,UUID branchId,UUID appointmentTypeId,Instant preferredAfter,Instant preferredBefore) {}

    private AuthPrincipal staff(Authentication a) {
        if (a==null || !(a.getPrincipal() instanceof AuthPrincipal p)) throw new AccessDeniedException("Clinic staff required");
        return p;
    }
    private AuthPrincipal manager(Authentication a) {
        AuthPrincipal p=staff(a);
        if (!Set.of("OWNER","ADMIN").contains(p.role())) throw new AccessDeniedException("Clinic management required");
        return p;
    }
    private static void require(boolean ok,String error) {
        if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,error);
    }
    private boolean exists(String table,UUID clinic,UUID id) {
        // Internal fixed table names only; never interpolate user-supplied identifiers.
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE clinic_id=? AND id=?)",Boolean.class,clinic,id));
    }
    private UUID clinic(Authentication a) { return staff(a).clinicId(); }
    private void event(AuthPrincipal p,UUID id,String type,String oldStatus,String newStatus,Instant oldStart,Instant newStart,String reason) {
        db.update("INSERT INTO appointment_event(clinic_id,appointment_id,changed_by,event_type,old_status,new_status,old_starts_at,new_starts_at,reason) VALUES (?,?,?,?,?,?,?,?,?)",
            p.clinicId(),id,p.userId(),type,oldStatus,newStatus,oldStart,newStart,reason);
    }
    private void notice(AuthPrincipal p,UUID id,String type) {
        db.update("INSERT INTO appointment_notification_outbox(clinic_id,appointment_id,event_type) VALUES (?,?,?)",p.clinicId(),id,type);
    }
    private ZoneId zone(UUID clinic) {
        String value=db.queryForObject("SELECT timezone FROM clinic WHERE id=?",String.class,clinic);
        return ZoneId.of(value);
    }
    private void validateWindow(Instant from,Instant to) {
        require(from!=null && to!=null && to.isAfter(from) && Duration.between(from,to).toHours()<25,"Invalid time interval");
    }
    private void scopeBooking(UUID clinic,BookingInput b) {
        require(b.patientId()!=null && b.branchId()!=null && b.dentistId()!=null,"Patient, branch and dentist required");
        require(exists("patient",clinic,b.patientId()) && Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL)",Boolean.class,clinic,b.patientId())),"Active patient not found");
        require(exists("branch",clinic,b.branchId()) && exists("dentist",clinic,b.dentistId()),"Branch or dentist not found");
        if(b.chairId()!=null) require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM chair WHERE clinic_id=? AND id=? AND branch_id=? AND active)",Boolean.class,clinic,b.chairId(),b.branchId())),"Chair unavailable in branch");
        if(b.appointmentTypeId()!=null) require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment_type WHERE clinic_id=? AND id=? AND active)",Boolean.class,clinic,b.appointmentTypeId())),"Appointment type unavailable");
    }
    private void resourceBlocks(UUID clinic,BookingInput b) {
        require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM dentist_time_off WHERE clinic_id=? AND dentist_id=? AND starts_at<? AND ends_at>?)",Boolean.class,clinic,b.dentistId(),b.endsAt(),b.startsAt())),"Dentist unavailable");
        if(b.chairId()!=null) require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM chair_block WHERE clinic_id=? AND chair_id=? AND starts_at<? AND ends_at>?)",Boolean.class,clinic,b.chairId(),b.endsAt(),b.startsAt())),"Chair unavailable");
    }
    private void validateSchedule(UUID clinic,BookingInput b) {
        validateWindow(b.startsAt(),b.endsAt()); scopeBooking(clinic,b);
        Map<String,Object> policy=db.queryForMap("SELECT slot_minutes,minimum_notice_minutes,booking_horizon_days FROM scheduling_policy WHERE clinic_id=?",clinic);
        Instant now=Instant.now();
        int notice=((Number)policy.get("minimum_notice_minutes")).intValue();
        int horizon=((Number)policy.get("booking_horizon_days")).intValue();
        require(!b.startsAt().isBefore(now.plusSeconds(notice*60L)) && !b.startsAt().isAfter(now.plusSeconds(horizon*86400L)),"Outside booking window");
        ZoneId tz=zone(clinic); ZonedDateTime start=b.startsAt().atZone(tz),end=b.endsAt().atZone(tz);
        require(start.toLocalDate().equals(end.toLocalDate()),"Booking cannot span clinic-local dates");
        int weekday=start.getDayOfWeek().getValue();
        require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM branch_operating_hour WHERE clinic_id=? AND branch_id=? AND day_of_week=? AND opens_at<=? AND closes_at>=?)",Boolean.class,clinic,b.branchId(),weekday,start.toLocalTime(),end.toLocalTime())),"Outside branch operating hours");
        require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM dentist_availability WHERE clinic_id=? AND dentist_id=? AND branch_id=? AND day_of_week=? AND starts_at<=? AND ends_at>=?)",Boolean.class,clinic,b.dentistId(),b.branchId(),weekday,start.toLocalTime(),end.toLocalTime())),"Outside dentist availability");
        resourceBlocks(clinic,b);
    }

    // M3.1 configuration
    @GetMapping("/types") @Transactional(readOnly=true)
    public List<Map<String,Object>> types(Authentication a) { return db.queryForList("SELECT id,name,default_minutes,buffer_before_minutes,buffer_after_minutes,color,active FROM appointment_type WHERE clinic_id=? ORDER BY name",clinic(a)); }
    @PostMapping("/types") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> type(Authentication a,@RequestBody TypeInput in) {
        UUID c=manager(a).clinicId();require(in.name()!=null && !in.name().isBlank() && in.name().length()<=120,"Invalid name");
        int minutes=in.defaultMinutes()==null?30:in.defaultMinutes(),before=in.bufferBeforeMinutes()==null?0:in.bufferBeforeMinutes(),after=in.bufferAfterMinutes()==null?0:in.bufferAfterMinutes();
        require(minutes>=5&&minutes<=480&&before>=0&&before<=240&&after>=0&&after<=240,"Invalid duration or buffer");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO appointment_type(id,clinic_id,name,default_minutes,buffer_before_minutes,buffer_after_minutes,color) VALUES(?,?,?,?,?,?,?)",id,c,in.name().trim(),minutes,before,after,in.color());
        return db.queryForMap("SELECT id,name,default_minutes,buffer_before_minutes,buffer_after_minutes,color,active FROM appointment_type WHERE id=? AND clinic_id=?",id,c);
    }
    @GetMapping("/policy") @Transactional(readOnly=true)
    public Map<String,Object> policy(Authentication a) { UUID c=clinic(a);List<Map<String,Object>> rows=db.queryForList("SELECT slot_minutes,minimum_notice_minutes,booking_horizon_days FROM scheduling_policy WHERE clinic_id=?",c);return rows.isEmpty()?Map.of("slot_minutes",15,"minimum_notice_minutes",0,"booking_horizon_days",180):rows.get(0); }
    @PutMapping("/policy") @Transactional
    public Map<String,Object> updatePolicy(Authentication a,@RequestBody PolicyInput p) {
        UUID c=manager(a).clinicId();require(p.slotMinutes()!=null&&Set.of(5,10,15,20,30,60).contains(p.slotMinutes()),"Invalid slot minutes");
        require(p.minimumNoticeMinutes()!=null&&p.minimumNoticeMinutes()>=0&&p.minimumNoticeMinutes()<=43200,"Invalid notice");
        require(p.bookingHorizonDays()!=null&&p.bookingHorizonDays()>=1&&p.bookingHorizonDays()<=730,"Invalid horizon");
        db.update("INSERT INTO scheduling_policy(clinic_id,slot_minutes,minimum_notice_minutes,booking_horizon_days) VALUES(?,?,?,?) ON CONFLICT(clinic_id) DO UPDATE SET slot_minutes=excluded.slot_minutes,minimum_notice_minutes=excluded.minimum_notice_minutes,booking_horizon_days=excluded.booking_horizon_days,updated_at=now()",c,p.slotMinutes(),p.minimumNoticeMinutes(),p.bookingHorizonDays());
        return db.queryForMap("SELECT slot_minutes,minimum_notice_minutes,booking_horizon_days FROM scheduling_policy WHERE clinic_id=?",c);
    }
    // M3.2 dentist availability
    @GetMapping("/dentist-availability") @Transactional(readOnly=true)
    public List<Map<String,Object>> availability(Authentication a,@RequestParam UUID branchId) {
        UUID c=clinic(a);return db.queryForList("SELECT id,dentist_id,branch_id,day_of_week,starts_at,ends_at FROM dentist_availability WHERE clinic_id=? AND branch_id=? ORDER BY day_of_week,starts_at",c,branchId);
    }
    @PostMapping("/dentist-availability") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> setAvailability(Authentication a,@RequestBody AvailabilityInput in) {
        UUID c=manager(a).clinicId();require(in.dentistId()!=null&&in.branchId()!=null&&exists("dentist",c,in.dentistId())&&exists("branch",c,in.branchId()),"Unknown dentist or branch");
        require(in.dayOfWeek()!=null&&in.dayOfWeek()>=1&&in.dayOfWeek()<=7&&in.startsAt()!=null&&in.endsAt()!=null&&in.startsAt().isBefore(in.endsAt()),"Invalid availability");
        UUID id=UUID.randomUUID();db.update("INSERT INTO dentist_availability(id,clinic_id,dentist_id,branch_id,day_of_week,starts_at,ends_at) VALUES(?,?,?,?,?,?,?)",id,c,in.dentistId(),in.branchId(),in.dayOfWeek(),in.startsAt(),in.endsAt());
        return db.queryForMap("SELECT * FROM dentist_availability WHERE id=? AND clinic_id=?",id,c);
    }
    @PostMapping("/dentist-time-off") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> timeOff(Authentication a,@RequestBody BlockInput in) {
        UUID c=manager(a).clinicId();validateWindow(in.startsAt(),in.endsAt());require(in.resourceId()!=null&&exists("dentist",c,in.resourceId()),"Unknown dentist");
        UUID id=UUID.randomUUID();db.update("INSERT INTO dentist_time_off(id,clinic_id,dentist_id,starts_at,ends_at,reason) VALUES(?,?,?,?,?,?)",id,c,in.resourceId(),in.startsAt(),in.endsAt(),in.reason());
        return db.queryForMap("SELECT * FROM dentist_time_off WHERE id=? AND clinic_id=?",id,c);
    }
    // M3.3 chair resource maintenance
    @PostMapping("/chair-blocks") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> chairBlock(Authentication a,@RequestBody BlockInput in) {
        UUID c=manager(a).clinicId();validateWindow(in.startsAt(),in.endsAt());require(in.resourceId()!=null&&exists("chair",c,in.resourceId()),"Unknown chair");
        UUID id=UUID.randomUUID();db.update("INSERT INTO chair_block(id,clinic_id,chair_id,starts_at,ends_at,reason) VALUES(?,?,?,?,?,?)",id,c,in.resourceId(),in.startsAt(),in.endsAt(),in.reason());
        return db.queryForMap("SELECT * FROM chair_block WHERE id=? AND clinic_id=?",id,c);
    }
    // M3.4 booking and M3.5 calendar
    @GetMapping("/resources") @Transactional(readOnly=true)
    public Map<String,Object> resources(Authentication a,@RequestParam UUID branchId) {
        UUID c=clinic(a);require(exists("branch",c,branchId),"Unknown branch");
        return Map.of("dentists",db.queryForList("SELECT d.id,d.full_name FROM dentist d WHERE d.clinic_id=? AND d.active ORDER BY d.full_name",c),
          "chairs",db.queryForList("SELECT id,name FROM chair WHERE clinic_id=? AND branch_id=? AND active ORDER BY name",c,branchId));
    }
    @GetMapping("/calendar") @Transactional(readOnly=true)
    public List<Map<String,Object>> calendar(Authentication a,@RequestParam UUID branchId,@RequestParam Instant from,@RequestParam Instant to) {
        UUID c=clinic(a);validateWindow(from,to);require(Duration.between(from,to).toDays()<=32,"Calendar range too long");
        return db.queryForList("SELECT a.id,a.branch_id,a.patient_id,p.first_name,p.last_name,a.dentist_id,d.full_name AS dentist_name,a.chair_id,a.appointment_type_id,a.starts_at,a.ends_at,a.status,a.reason,a.version FROM appointment a JOIN patient p ON p.id=a.patient_id AND p.clinic_id=a.clinic_id JOIN dentist d ON d.id=a.dentist_id AND d.clinic_id=a.clinic_id WHERE a.clinic_id=? AND a.branch_id=? AND a.starts_at<? AND a.ends_at>? ORDER BY a.starts_at,a.id",c,branchId,to,from);
    }
    @PostMapping("/appointments") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> book(Authentication a,@RequestBody BookingInput in) {
        AuthPrincipal p=staff(a);UUID c=p.clinicId();
        // First creation for older clinics lazily establishes defaults (GET /policy is read-only).
        db.update("INSERT INTO scheduling_policy(clinic_id) VALUES(?) ON CONFLICT DO NOTHING",c);
        validateSchedule(c,in);UUID id=UUID.randomUUID();
        try { db.update("INSERT INTO appointment(id,clinic_id,branch_id,patient_id,dentist_id,chair_id,appointment_type_id,starts_at,ends_at,status,reason,created_by) VALUES(?,?,?,?,?,?,?,?,?,'BOOKED',?,?)",id,c,in.branchId(),in.patientId(),in.dentistId(),in.chairId(),in.appointmentTypeId(),in.startsAt(),in.endsAt(),in.reason(),p.userId()); }
        catch(DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Slot already booked or resource constraints violated"); }
        event(p,id,"BOOKED",null,"BOOKED",null,in.startsAt(),in.reason());notice(p,id,"BOOKED");
        return appointment(a,id);
    }
    @GetMapping("/appointments/{id}") @Transactional(readOnly=true)
    public Map<String,Object> appointment(Authentication a,@PathVariable UUID id) {
        UUID c=clinic(a);List<Map<String,Object>> rows=db.queryForList("SELECT id,patient_id,branch_id,dentist_id,chair_id,appointment_type_id,starts_at,ends_at,status,reason,version FROM appointment WHERE clinic_id=? AND id=?",c,id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found");return rows.get(0);
    }
    // M3.6 state transitions & rescheduling
    @PostMapping("/appointments/{id}/status") @Transactional
    public Map<String,Object> status(Authentication a,@PathVariable UUID id,@RequestBody StatusInput input) {
        AuthPrincipal p=staff(a);Map<String,Object> old=appointment(a,id);String current=(String)old.get("status"),next=input.status();
        Map<String,Set<String>> transitions=Map.of("BOOKED",Set.of("CONFIRMED","ARRIVED","CANCELLED","NO_SHOW"),"CONFIRMED",Set.of("ARRIVED","CANCELLED","NO_SHOW"),"ARRIVED",Set.of("IN_CHAIR","CANCELLED"),"IN_CHAIR",Set.of("COMPLETED"));
        require(next!=null&&transitions.getOrDefault(current,Set.of()).contains(next),"Unsupported status transition");
        int n=db.update("UPDATE appointment SET status=?,updated_at=now(),version=version+1 WHERE clinic_id=? AND id=? AND version=?",next,p.clinicId(),id,((Number)old.get("version")).longValue());
        if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Appointment changed");
        event(p,id,"STATUS",current,next,(Instant)old.get("starts_at"),(Instant)old.get("starts_at"),input.reason());
        if(next.equals("CANCELLED")||next.equals("CONFIRMED"))notice(p,id,next);
        return appointment(a,id);
    }
    @PostMapping("/appointments/{id}/reschedule") @Transactional
    public Map<String,Object> reschedule(Authentication a,@PathVariable UUID id,@RequestBody RescheduleInput input) {
        AuthPrincipal p=staff(a);Map<String,Object> old=appointment(a,id);String status=(String)old.get("status");
        require(Set.of("BOOKED","CONFIRMED").contains(status),"Appointment cannot be rescheduled");
        BookingInput candidate=new BookingInput((UUID)old.get("patient_id"),(UUID)old.get("branch_id"),(UUID)old.get("dentist_id"),(UUID)old.get("chair_id"),(UUID)old.get("appointment_type_id"),input.startsAt(),input.endsAt(),input.reason());
        validateSchedule(p.clinicId(),candidate);
        try { int n=db.update("UPDATE appointment SET starts_at=?,ends_at=?,reason=?,updated_at=now(),version=version+1 WHERE id=? AND clinic_id=? AND version=?",input.startsAt(),input.endsAt(),input.reason(),id,p.clinicId(),((Number)old.get("version")).longValue());if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Appointment changed"); }
        catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Slot already booked");}
        event(p,id,"RESCHEDULED",status,status,(Instant)old.get("starts_at"),input.startsAt(),input.reason());notice(p,id,"RESCHEDULED");return appointment(a,id);
    }
    @GetMapping("/appointments/{id}/events") @Transactional(readOnly=true)
    public List<Map<String,Object>> events(Authentication a,@PathVariable UUID id) {
        appointment(a,id);return db.queryForList("SELECT id,event_type,old_status,new_status,old_starts_at,new_starts_at,reason,created_at FROM appointment_event WHERE clinic_id=? AND appointment_id=? ORDER BY created_at,id",clinic(a),id);
    }
    // M3.7 arrival queue (status derived from appointment lifecycle)
    @GetMapping("/queue") @Transactional(readOnly=true)
    public List<Map<String,Object>> queue(Authentication a,@RequestParam UUID branchId) {
        UUID c=clinic(a);LocalDate day=LocalDate.now(zone(c));Instant start=day.atStartOfDay(zone(c)).toInstant(),end=day.plusDays(1).atStartOfDay(zone(c)).toInstant();
        return db.queryForList("SELECT a.id,a.patient_id,p.first_name,p.last_name,a.starts_at,a.status,a.chair_id FROM appointment a JOIN patient p ON p.id=a.patient_id AND p.clinic_id=a.clinic_id WHERE a.clinic_id=? AND a.branch_id=? AND a.starts_at>=? AND a.starts_at<? AND a.status IN ('ARRIVED','IN_CHAIR') ORDER BY a.starts_at",c,branchId,start,end);
    }
    // M3.8 waitlist
    @PostMapping("/waitlist") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> waitlist(Authentication a,@RequestBody WaitlistInput in) {
        UUID c=staff(a).clinicId();require(in.patientId()!=null&&in.branchId()!=null&&exists("patient",c,in.patientId())&&exists("branch",c,in.branchId()),"Invalid patient or branch");
        require(in.preferredAfter()!=null&&in.preferredBefore()!=null&&in.preferredBefore().isAfter(in.preferredAfter()),"Invalid preference range");
        if(in.appointmentTypeId()!=null)require(exists("appointment_type",c,in.appointmentTypeId()),"Unknown type");
        UUID id=UUID.randomUUID();db.update("INSERT INTO appointment_waitlist(id,clinic_id,patient_id,branch_id,appointment_type_id,preferred_after,preferred_before) VALUES(?,?,?,?,?,?,?)",id,c,in.patientId(),in.branchId(),in.appointmentTypeId(),in.preferredAfter(),in.preferredBefore());
        return db.queryForMap("SELECT id,patient_id,branch_id,appointment_type_id,preferred_after,preferred_before,status FROM appointment_waitlist WHERE clinic_id=? AND id=?",c,id);
    }
    @GetMapping("/waitlist") @Transactional(readOnly=true)
    public List<Map<String,Object>> waitlistEntries(Authentication a,@RequestParam UUID branchId) {
        return db.queryForList("SELECT id,patient_id,branch_id,appointment_type_id,preferred_after,preferred_before,status,offered_until FROM appointment_waitlist WHERE clinic_id=? AND branch_id=? AND status IN ('WAITING','OFFERED') ORDER BY created_at LIMIT 100",clinic(a),branchId);
    }
    // M3.9 delivery is deferred to M8; outbox is internal and never directly sends messages.
}
