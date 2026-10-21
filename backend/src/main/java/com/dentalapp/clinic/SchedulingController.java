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
    public record AvailabilityInput(UUID id,UUID dentistId,UUID branchId,Integer dayOfWeek,LocalTime startsAt,LocalTime endsAt) {}
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
            p.clinicId(),id,p.userId(),type,oldStatus,newStatus,ts(oldStart),ts(newStart),reason);
    }
    private void notice(AuthPrincipal p,UUID id,String type) {
        db.update("INSERT INTO appointment_notification_outbox(clinic_id,appointment_id,event_type) VALUES (?,?,?)",p.clinicId(),id,type);
    }
    private void ensurePolicy(UUID c) {
        // First branch/booking use lazily establishes defaults (GET /policy is read-only).
        db.update("INSERT INTO scheduling_policy(clinic_id) VALUES(?) ON CONFLICT DO NOTHING",c);
    }
    private ZoneId zone(UUID clinic) {
        String value=db.queryForObject("SELECT timezone FROM clinic WHERE id=?",String.class,clinic);
        return ZoneId.of(value);
    }
    private static java.sql.Timestamp ts(Instant value) { return value==null?null:java.sql.Timestamp.from(value); }
    private static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.time.OffsetDateTime od) return od.toInstant();
        return ((java.util.Date)value).toInstant();
    }
    private void validateWindow(Instant from,Instant to) {
        require(from!=null && to!=null && to.isAfter(from) && Duration.between(from,to).toHours()<25,"Invalid time interval");
    }
    private void scopeBooking(UUID clinic,BookingInput b) {
        require(b.patientId()!=null && b.branchId()!=null && b.dentistId()!=null,"Patient, branch and dentist required");
        require(exists("patient",clinic,b.patientId()) && Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM patient WHERE clinic_id=? AND id=? AND deleted_at IS NULL)",Boolean.class,clinic,b.patientId())),"Active patient not found");
        require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM branch WHERE clinic_id=? AND id=? AND active)",Boolean.class,clinic,b.branchId())) && Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM dentist WHERE clinic_id=? AND id=? AND active)",Boolean.class,clinic,b.dentistId())),"Branch or dentist unavailable");
        if(b.chairId()!=null) require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM chair WHERE clinic_id=? AND id=? AND branch_id=? AND active)",Boolean.class,clinic,b.chairId(),b.branchId())),"Chair unavailable in branch");
        if(b.appointmentTypeId()!=null) require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment_type WHERE clinic_id=? AND id=? AND active)",Boolean.class,clinic,b.appointmentTypeId())),"Appointment type unavailable");
    }
    private void resourceBlocks(UUID clinic,BookingInput b) {
        require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM dentist_time_off WHERE clinic_id=? AND dentist_id=? AND starts_at<? AND ends_at>?)",Boolean.class,clinic,b.dentistId(),ts(b.endsAt()),ts(b.startsAt()))),"Dentist unavailable");
        if(b.chairId()!=null) require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM chair_block WHERE clinic_id=? AND chair_id=? AND starts_at<? AND ends_at>?)",Boolean.class,clinic,b.chairId(),ts(b.endsAt()),ts(b.startsAt()))),"Chair unavailable");
    }
    private void validateSchedule(UUID clinic,BookingInput b,UUID selfId) {
        validateWindow(b.startsAt(),b.endsAt()); scopeBooking(clinic,b);
        long minutes=Duration.between(b.startsAt(),b.endsAt()).toMinutes();
        require(minutes>=5&&minutes<=480,"Booking duration must be 5-480 minutes");
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
        // Buffered guard time widens the reserved interval against neighbouring appointments for
        // the same dentist and chair. Safe under concurrency because the dentist and chair rows
        // are already locked FOR UPDATE by the caller before validation runs.
        int before=0,after=0;
        if(b.appointmentTypeId()!=null) {
            Map<String,Object> type=db.queryForMap("SELECT buffer_before_minutes,buffer_after_minutes FROM appointment_type WHERE clinic_id=? AND id=?",clinic,b.appointmentTypeId());
            before=((Number)type.get("buffer_before_minutes")).intValue();after=((Number)type.get("buffer_after_minutes")).intValue();
        }
        Instant guardStart=b.startsAt().minusSeconds(before*60L),guardEnd=b.endsAt().plusSeconds(after*60L);
        // selfId excludes the appointment being rescheduled from its own conflict window.
        require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment WHERE clinic_id=? AND dentist_id=? AND starts_at<? AND ends_at>? AND status NOT IN ('CANCELLED','NO_SHOW') AND id<>COALESCE(?::uuid,gen_random_uuid()))",Boolean.class,clinic,b.dentistId(),ts(guardEnd),ts(guardStart),selfId)),"Surrounding appointments conflict with the requested interval or its buffers");
        if(b.chairId()!=null) require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment WHERE clinic_id=? AND chair_id=? AND starts_at<? AND ends_at>? AND status NOT IN ('CANCELLED','NO_SHOW') AND id<>COALESCE(?::uuid,gen_random_uuid()))",Boolean.class,clinic,b.chairId(),ts(guardEnd),ts(guardStart),selfId)),"Chair guard windows conflict");
    }

    // M3.1 configuration
    @GetMapping("/types") @Transactional(readOnly=true)
    public List<Map<String,Object>> types(Authentication a) { return db.queryForList("SELECT t.id,t.name,t.default_minutes,t.buffer_before_minutes,t.buffer_after_minutes,t.color,t.active,(SELECT COUNT(*) FROM appointment a WHERE a.clinic_id=t.clinic_id AND a.appointment_type_id=t.id) AS appointment_count,(SELECT COUNT(*) FROM appointment_waitlist w WHERE w.clinic_id=t.clinic_id AND w.appointment_type_id=t.id) AS waitlist_count FROM appointment_type t WHERE t.clinic_id=? ORDER BY t.name",clinic(a)); }
    @PostMapping("/types") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> type(Authentication a,@RequestBody TypeInput in) {
        UUID c=manager(a).clinicId();require(in.name()!=null && !in.name().isBlank() && in.name().length()<=120,"Invalid name");
        int minutes=in.defaultMinutes()==null?30:in.defaultMinutes(),before=in.bufferBeforeMinutes()==null?0:in.bufferBeforeMinutes(),after=in.bufferAfterMinutes()==null?0:in.bufferAfterMinutes();
        require(minutes>=5&&minutes<=480&&before>=0&&before<=240&&after>=0&&after<=240,"Invalid duration or buffer");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO appointment_type(id,clinic_id,name,default_minutes,buffer_before_minutes,buffer_after_minutes,color) VALUES(?,?,?,?,?,?,?)",id,c,in.name().trim(),minutes,before,after,in.color());
        return db.queryForMap("SELECT id,name,default_minutes,buffer_before_minutes,buffer_after_minutes,color,active FROM appointment_type WHERE id=? AND clinic_id=?",id,c);
    }
    @PutMapping("/types/{id}") @Transactional
    public Map<String,Object> updateType(Authentication a,@PathVariable UUID id,@RequestBody TypeInput in) {
        UUID c=manager(a).clinicId();require(in.name()!=null&&!in.name().isBlank()&&in.name().length()<=120,"Invalid name");
        int minutes=in.defaultMinutes()==null?30:in.defaultMinutes(),before=in.bufferBeforeMinutes()==null?0:in.bufferBeforeMinutes(),after=in.bufferAfterMinutes()==null?0:in.bufferAfterMinutes();
        require(minutes>=5&&minutes<=480&&before>=0&&before<=240&&after>=0&&after<=240,"Invalid duration or buffer");
        require(exists("appointment_type",c,id),"Appointment type not found");
        db.update("UPDATE appointment_type SET name=?,default_minutes=?,buffer_before_minutes=?,buffer_after_minutes=?,color=COALESCE(?,color),updated_at=now(),version=version+1 WHERE clinic_id=? AND id=?",in.name().trim(),minutes,before,after,in.color(),c,id);
        return db.queryForMap("SELECT id,name,default_minutes,buffer_before_minutes,buffer_after_minutes,color,active FROM appointment_type WHERE id=? AND clinic_id=?",id,c);
    }
    @DeleteMapping("/types/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void deleteType(Authentication a,@PathVariable UUID id) {
        UUID c=manager(a).clinicId();require(exists("appointment_type",c,id),"Appointment type not found");
        boolean used=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment WHERE clinic_id=? AND appointment_type_id=?) OR EXISTS(SELECT 1 FROM appointment_waitlist WHERE clinic_id=? AND appointment_type_id=?)",Boolean.class,c,id,c,id));
        require(!used,"Appointment type has appointment or waitlist history and cannot be deleted");
        try{db.update("DELETE FROM appointment_type WHERE clinic_id=? AND id=?",c,id);}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Appointment type has been used and cannot be deleted",e);}
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
    public List<Map<String,Object>> availability(Authentication a,@RequestParam(required=false) UUID branchId,@RequestParam(required=false) UUID dentistId) {
        UUID c=clinic(a);StringBuilder sql=new StringBuilder("SELECT da.id,da.dentist_id,da.branch_id,b.name AS branch_name,da.day_of_week,da.starts_at,da.ends_at FROM dentist_availability da JOIN branch b ON b.id=da.branch_id AND b.clinic_id=da.clinic_id WHERE da.clinic_id=?");List<Object> args=new ArrayList<>();args.add(c);
        if(branchId!=null){require(exists("branch",c,branchId),"Unknown branch");sql.append(" AND da.branch_id=?");args.add(branchId);}
        if(dentistId!=null){require(exists("dentist",c,dentistId),"Unknown dentist");sql.append(" AND da.dentist_id=?");args.add(dentistId);}
        sql.append(" ORDER BY da.dentist_id,da.day_of_week,da.starts_at,b.name");return db.queryForList(sql.toString(),args.toArray());
    }
    @PostMapping("/dentist-availability") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> setAvailability(Authentication a,@RequestBody AvailabilityInput in) {
        UUID c=manager(a).clinicId();require(in.dentistId()!=null&&in.branchId()!=null&&exists("dentist",c,in.dentistId())&&exists("branch",c,in.branchId()),"Unknown dentist or branch");
        require(in.dayOfWeek()!=null&&in.dayOfWeek()>=1&&in.dayOfWeek()<=7&&in.startsAt()!=null&&in.endsAt()!=null&&in.startsAt().isBefore(in.endsAt()),"Invalid availability");
        // Serialize availability edits per dentist so overlapping hours across branches cannot race.
        db.queryForObject("SELECT id FROM dentist WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,c,in.dentistId());
        UUID existingId=in.id();
        if(existingId!=null)require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM dentist_availability WHERE id=? AND clinic_id=? AND dentist_id=? AND branch_id=? AND day_of_week=?)",Boolean.class,existingId,c,in.dentistId(),in.branchId(),in.dayOfWeek())),"Saved availability was not found. Refresh and try again");
        String overlapSql="SELECT EXISTS(SELECT 1 FROM dentist_availability WHERE clinic_id=? AND dentist_id=? AND day_of_week=? AND starts_at<? AND ends_at>?"+(existingId==null?"":" AND id<>?")+")";
        List<Object> overlapArgs=new ArrayList<>(List.of(c,in.dentistId(),in.dayOfWeek(),in.endsAt(),in.startsAt()));
        if(existingId!=null)overlapArgs.add(existingId);
        require(!Boolean.TRUE.equals(db.queryForObject(overlapSql,Boolean.class,overlapArgs.toArray())),"These hours overlap another saved schedule for this dentist. Choose a non-overlapping time");
        UUID id=existingId==null?UUID.randomUUID():existingId;
        if(existingId==null)db.update("INSERT INTO dentist_availability(id,clinic_id,dentist_id,branch_id,day_of_week,starts_at,ends_at) VALUES(?,?,?,?,?,?,?)",id,c,in.dentistId(),in.branchId(),in.dayOfWeek(),in.startsAt(),in.endsAt());
        else db.update("UPDATE dentist_availability SET starts_at=?,ends_at=? WHERE id=? AND clinic_id=?",in.startsAt(),in.endsAt(),id,c);
        return db.queryForMap("SELECT * FROM dentist_availability WHERE id=? AND clinic_id=?",id,c);
    }
    @DeleteMapping("/dentist-availability/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void removeAvailability(Authentication a,@PathVariable UUID id) {
        UUID c=manager(a).clinicId();
        int updated=db.update("DELETE FROM dentist_availability WHERE id=? AND clinic_id=?",id,c);
        if(updated==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Availability not found");
    }
    @PostMapping("/dentist-time-off") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> timeOff(Authentication a,@RequestBody BlockInput in) {
        UUID c=manager(a).clinicId();validateWindow(in.startsAt(),in.endsAt());require(in.resourceId()!=null&&exists("dentist",c,in.resourceId()),"Unknown dentist");
        db.queryForObject("SELECT id FROM dentist WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,c,in.resourceId());
        require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment WHERE clinic_id=? AND dentist_id=? AND starts_at<? AND ends_at>? AND status NOT IN ('CANCELLED','NO_SHOW'))",Boolean.class,c,in.resourceId(),ts(in.endsAt()),ts(in.startsAt()))),"Time off conflicts with booked appointments");
        UUID id=UUID.randomUUID();db.update("INSERT INTO dentist_time_off(id,clinic_id,dentist_id,starts_at,ends_at,reason) VALUES(?,?,?,?,?,?)",id,c,in.resourceId(),ts(in.startsAt()),ts(in.endsAt()),in.reason());
        return db.queryForMap("SELECT * FROM dentist_time_off WHERE id=? AND clinic_id=?",id,c);
    }
    // M3.3 chair resource maintenance
    @GetMapping("/chair-blocks") @Transactional(readOnly=true)
    public List<Map<String,Object>> chairBlocks(Authentication a,@RequestParam UUID branchId) {
        UUID c=clinic(a);require(exists("branch",c,branchId),"Unknown branch");
        return db.queryForList("SELECT cb.id,cb.chair_id,ch.name AS chair_name,ch.branch_id,cb.starts_at,cb.ends_at,cb.reason FROM chair_block cb JOIN chair ch ON ch.id=cb.chair_id AND ch.clinic_id=cb.clinic_id WHERE cb.clinic_id=? AND ch.branch_id=? ORDER BY cb.starts_at",c,branchId);
    }
    @PostMapping("/chair-blocks") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> chairBlock(Authentication a,@RequestBody BlockInput in) {
        UUID c=manager(a).clinicId();validateWindow(in.startsAt(),in.endsAt());require(in.resourceId()!=null&&exists("chair",c,in.resourceId()),"Unknown chair");
        db.queryForObject("SELECT id FROM chair WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,c,in.resourceId());
        require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment WHERE clinic_id=? AND chair_id=? AND starts_at<? AND ends_at>? AND status NOT IN ('CANCELLED','NO_SHOW'))",Boolean.class,c,in.resourceId(),ts(in.endsAt()),ts(in.startsAt()))),"Chair block conflicts with booked appointments");
        UUID id=UUID.randomUUID();db.update("INSERT INTO chair_block(id,clinic_id,chair_id,starts_at,ends_at,reason) VALUES(?,?,?,?,?,?)",id,c,in.resourceId(),ts(in.startsAt()),ts(in.endsAt()),in.reason());
        return db.queryForMap("SELECT * FROM chair_block WHERE id=? AND clinic_id=?",id,c);
    }
    @PutMapping("/chair-blocks/{id}") @Transactional
    public Map<String,Object> updateChairBlock(Authentication a,@PathVariable UUID id,@RequestBody BlockInput in) {
        UUID c=manager(a).clinicId();validateWindow(in.startsAt(),in.endsAt());require(in.resourceId()!=null&&exists("chair",c,in.resourceId()),"Unknown chair");
        require(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM chair_block WHERE id=? AND clinic_id=?)",Boolean.class,id,c)),"Chair block not found");
        db.queryForObject("SELECT id FROM chair WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,c,in.resourceId());
        require(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM appointment WHERE clinic_id=? AND chair_id=? AND starts_at<? AND ends_at>? AND status NOT IN ('CANCELLED','NO_SHOW'))",Boolean.class,c,in.resourceId(),ts(in.endsAt()),ts(in.startsAt()))),"Chair block conflicts with booked appointments");
        db.update("UPDATE chair_block SET chair_id=?,starts_at=?,ends_at=?,reason=? WHERE id=? AND clinic_id=?",in.resourceId(),ts(in.startsAt()),ts(in.endsAt()),in.reason(),id,c);
        return db.queryForMap("SELECT * FROM chair_block WHERE id=? AND clinic_id=?",id,c);
    }
    @DeleteMapping("/chair-blocks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void deleteChairBlock(Authentication a,@PathVariable UUID id) {
        int deleted=db.update("DELETE FROM chair_block WHERE id=? AND clinic_id=?",id,manager(a).clinicId());
        if(deleted==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Chair block not found");
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
        UUID c=clinic(a);require(from!=null&&to!=null&&to.isAfter(from)&&Duration.between(from,to).toDays()<=32,"Invalid calendar range");
        return db.queryForList("SELECT a.id,a.branch_id,a.patient_id,p.first_name,p.last_name,a.dentist_id,d.full_name AS dentist_name,a.chair_id,a.appointment_type_id,a.starts_at,a.ends_at,a.status,a.reason,a.version FROM appointment a JOIN patient p ON p.id=a.patient_id AND p.clinic_id=a.clinic_id JOIN dentist d ON d.id=a.dentist_id AND d.clinic_id=a.clinic_id WHERE a.clinic_id=? AND a.branch_id=? AND a.starts_at<? AND a.ends_at>? ORDER BY a.starts_at,a.id",c,branchId,ts(to),ts(from));
    }
    @PostMapping("/appointments") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> book(Authentication a,@RequestBody BookingInput in) {
        AuthPrincipal p=staff(a);UUID c=p.clinicId();
        // Validate all referenced resources are scoped to this clinic BEFORE row locks so a
        // cross-clinic reference fails fast with 400 instead of an empty FOR UPDATE lookup.
        scopeBooking(c,in);ensurePolicy(c);
        // Resource row locks synchronize booking with creation of chair and dentist blocks.
        db.queryForObject("SELECT id FROM dentist WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,c,in.dentistId());
        if(in.chairId()!=null) db.queryForObject("SELECT id FROM chair WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,c,in.chairId());
        validateSchedule(c,in,null);UUID id=UUID.randomUUID();
        try { db.update("INSERT INTO appointment(id,clinic_id,branch_id,patient_id,dentist_id,chair_id,appointment_type_id,starts_at,ends_at,status,reason,created_by) VALUES(?,?,?,?,?,?,?,?,?,'BOOKED',?,?)",id,c,in.branchId(),in.patientId(),in.dentistId(),in.chairId(),in.appointmentTypeId(),ts(in.startsAt()),ts(in.endsAt()),in.reason(),p.userId()); }
        catch(DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Slot already booked or resource constraints violated"); }
        event(p,id,"BOOKED",null,"BOOKED",null,in.startsAt(),in.reason());notice(p,id,"BOOKED");
        // Waitlist closure: a direct booking inside the entry's preferred window finalises it.
        // OFFERED entries whose offer lapsed cannot reserve the slot and are included like WAITING.
        if(in.appointmentTypeId()!=null)
            db.update("UPDATE appointment_waitlist SET status='BOOKED' WHERE clinic_id=? AND patient_id=? AND branch_id=? AND status IN ('WAITING','OFFERED') AND preferred_after<=? AND preferred_before>=? AND (appointment_type_id IS NULL OR appointment_type_id=?)",c,in.patientId(),in.branchId(),ts(in.endsAt()),ts(in.startsAt()),in.appointmentTypeId());
        else
            db.update("UPDATE appointment_waitlist SET status='BOOKED' WHERE clinic_id=? AND patient_id=? AND branch_id=? AND status IN ('WAITING','OFFERED') AND preferred_after<=? AND preferred_before>=?",c,in.patientId(),in.branchId(),ts(in.endsAt()),ts(in.startsAt()));
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
        event(p,id,"STATUS",current,next,instant(old.get("starts_at")),instant(old.get("starts_at")),input.reason());
        if(next.equals("CANCELLED")||next.equals("CONFIRMED"))notice(p,id,next);
        return appointment(a,id);
    }
    @PostMapping("/appointments/{id}/reschedule") @Transactional
    public Map<String,Object> reschedule(Authentication a,@PathVariable UUID id,@RequestBody RescheduleInput input) {
        AuthPrincipal p=staff(a);Map<String,Object> old=appointment(a,id);String status=(String)old.get("status");
        require(Set.of("BOOKED","CONFIRMED").contains(status),"Appointment cannot be rescheduled");
        BookingInput candidate=new BookingInput((UUID)old.get("patient_id"),(UUID)old.get("branch_id"),(UUID)old.get("dentist_id"),(UUID)old.get("chair_id"),(UUID)old.get("appointment_type_id"),input.startsAt(),input.endsAt(),input.reason());
        db.queryForObject("SELECT id FROM dentist WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,p.clinicId(),candidate.dentistId());
        if(candidate.chairId()!=null) db.queryForObject("SELECT id FROM chair WHERE clinic_id=? AND id=? FOR UPDATE",UUID.class,p.clinicId(),candidate.chairId());
        validateSchedule(p.clinicId(),candidate,id);
        try { int n=db.update("UPDATE appointment SET starts_at=?,ends_at=?,reason=?,updated_at=now(),version=version+1 WHERE id=? AND clinic_id=? AND version=?",ts(input.startsAt()),ts(input.endsAt()),input.reason(),id,p.clinicId(),((Number)old.get("version")).longValue());if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Appointment changed"); }
        catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Slot already booked");}
        event(p,id,"RESCHEDULED",status,status,instant(old.get("starts_at")),input.startsAt(),input.reason());notice(p,id,"RESCHEDULED");return appointment(a,id);
    }
    @GetMapping("/appointments/{id}/events") @Transactional(readOnly=true)
    public List<Map<String,Object>> events(Authentication a,@PathVariable UUID id) {
        appointment(a,id);return db.queryForList("SELECT id,event_type,old_status,new_status,old_starts_at,new_starts_at,reason,created_at FROM appointment_event WHERE clinic_id=? AND appointment_id=? ORDER BY created_at,id",clinic(a),id);
    }
    // M3.7 arrival queue (status derived from appointment lifecycle)
    @GetMapping("/queue") @Transactional(readOnly=true)
    public List<Map<String,Object>> queue(Authentication a,@RequestParam UUID branchId) {
        UUID c=clinic(a);LocalDate day=LocalDate.now(zone(c));Instant start=day.atStartOfDay(zone(c)).toInstant(),end=day.plusDays(1).atStartOfDay(zone(c)).toInstant();
        return db.queryForList("SELECT a.id,a.patient_id,p.first_name,p.last_name,a.starts_at,a.status,a.chair_id FROM appointment a JOIN patient p ON p.id=a.patient_id AND p.clinic_id=a.clinic_id WHERE a.clinic_id=? AND a.branch_id=? AND a.starts_at>=? AND a.starts_at<? AND a.status IN ('ARRIVED','IN_CHAIR') ORDER BY a.starts_at",c,branchId,ts(start),ts(end));
    }
    // M3.8 waitlist
    @PostMapping("/waitlist") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> waitlist(Authentication a,@RequestBody WaitlistInput in) {
        UUID c=staff(a).clinicId();require(in.patientId()!=null&&in.branchId()!=null&&exists("patient",c,in.patientId())&&exists("branch",c,in.branchId()),"Invalid patient or branch");
        require(in.preferredAfter()!=null&&in.preferredBefore()!=null&&in.preferredBefore().isAfter(in.preferredAfter()),"Invalid preference range");
        if(in.appointmentTypeId()!=null)require(exists("appointment_type",c,in.appointmentTypeId()),"Unknown type");
        UUID id=UUID.randomUUID();db.update("INSERT INTO appointment_waitlist(id,clinic_id,patient_id,branch_id,appointment_type_id,preferred_after,preferred_before) VALUES(?,?,?,?,?,?,?)",id,c,in.patientId(),in.branchId(),in.appointmentTypeId(),ts(in.preferredAfter()),ts(in.preferredBefore()));
        return db.queryForMap("SELECT id,patient_id,branch_id,appointment_type_id,preferred_after,preferred_before,status FROM appointment_waitlist WHERE clinic_id=? AND id=?",c,id);
    }
    @GetMapping("/waitlist") @Transactional(readOnly=true)
    public List<Map<String,Object>> waitlistEntries(Authentication a,@RequestParam UUID branchId) {
        return db.queryForList("SELECT id,patient_id,branch_id,appointment_type_id,preferred_after,preferred_before,status,offered_until FROM appointment_waitlist WHERE clinic_id=? AND branch_id=? AND status IN ('WAITING','OFFERED') ORDER BY created_at LIMIT 100",clinic(a),branchId);
    }
    @PostMapping("/waitlist/{id}/cancel") @Transactional
    public Map<String,Object> cancelWaitlist(Authentication a,@PathVariable UUID id) {
        UUID c=staff(a).clinicId();
        int changed=db.update("UPDATE appointment_waitlist SET status='CANCELLED' WHERE clinic_id=? AND id=? AND status IN ('WAITING','OFFERED')",c,id);
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Waitlist entry missing or already finalized");
        return db.queryForMap("SELECT id,patient_id,branch_id,status FROM appointment_waitlist WHERE clinic_id=? AND id=?",c,id);
    }
    @PostMapping("/waitlist/{id}/offer") @Transactional
    public Map<String,Object> offerWaitlist(Authentication a,@PathVariable UUID id,@RequestParam(defaultValue="60") int minutes) {
        UUID c=staff(a).clinicId();require(minutes>=5&&minutes<=1440,"Invalid offer lifetime");
        int changed=db.update("UPDATE appointment_waitlist SET status='OFFERED',offered_until=now()+(? * interval '1 minute') WHERE clinic_id=? AND id=? AND status='WAITING'",minutes,c,id);
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Waitlist entry unavailable");
        return db.queryForMap("SELECT id,patient_id,branch_id,status,offered_until FROM appointment_waitlist WHERE clinic_id=? AND id=?",c,id);
    }
    @PostMapping("/waitlist/expire-offers") @Transactional
    public Map<String,Object> expireOffers(Authentication a) {
        UUID c=manager(a).clinicId();
        int expired=db.update("UPDATE appointment_waitlist SET status='EXPIRED' WHERE clinic_id=? AND status='OFFERED' AND offered_until<now()",c);
        return Map.of("expired",expired);
    }
    @GetMapping("/appointment-notifications") @Transactional(readOnly=true)
    public List<Map<String,Object>> pendingNotifications(Authentication a) {
        manager(a);return db.queryForList("SELECT id,appointment_id,event_type,state,created_at FROM appointment_notification_outbox WHERE clinic_id=? ORDER BY created_at DESC LIMIT 100",clinic(a));
    }
    // M3.9 delivery is deferred to M8; outbox is internal and never directly sends messages.
}
