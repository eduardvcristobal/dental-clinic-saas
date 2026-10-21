package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.auth.GoogleIdentityVerifier;
import com.dentalapp.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** M1.2: invitations are claimed by Google-verified email; no public role assignment. */
@RestController
@RequestMapping("/api/onboarding")
public class ClinicOnboardingController {
    private final JdbcTemplate db;
    private final GoogleIdentityVerifier google;
    private final TransactionTemplate tx;
    private final boolean selfServiceEnabled;
    private final String allowedOrigins;
    private final SecureRandom random = new SecureRandom();
    public ClinicOnboardingController(JdbcTemplate db, GoogleIdentityVerifier google, PlatformTransactionManager transactions,
            @Value("${app.onboarding.self-service-enabled:false}") boolean selfServiceEnabled,
            @Value("${app.auth.allowed-origins:}") String allowedOrigins) {
        this.db=db; this.google=google; this.tx=new TransactionTemplate(transactions);
        this.selfServiceEnabled=selfServiceEnabled; this.allowedOrigins=allowedOrigins;
    }
    public record CreateClinic(String googleIdToken,String slug,String name,String timezone,String branchName,String ownerName) {
        @Override public String toString() { return "CreateClinic[request=[REDACTED]]"; }
    }
    public record Invite(String email,String role,UUID branchId) {
        @Override public String toString() { return "Invite[email=[REDACTED], role=" + role + ", branchId=" + branchId + "]"; }
    }
    public record Accept(String googleIdToken,String token,String fullName) {
        @Override public String toString() { return "Accept[identityAndInvitation=[REDACTED], fullName=[REDACTED]]"; }
    }
    public record InvitationCreated(UUID id,String token,String expiresAt) {
        @Override public String toString() { return "InvitationCreated[id=" + id + ", token=[REDACTED], expiresAt=" + expiresAt + "]"; }
    }
    public record DentistInput(UUID userId,String fullName,String licenseNo,String specialty,String calendarColor) {}
    private static String required(String v,int max) {
        if(v==null || v.isBlank() || v.strip().length()>max) throw bad("Invalid input");
        return v.strip();
    }
    private static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static String email(String e) {return required(e,254).toLowerCase(Locale.ROOT);}
    private static String hash(String token) {
        try {return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private void origin(HttpServletRequest request) {
        String value=request.getHeader("Origin");
        if(value!=null && !value.isBlank() && java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).noneMatch(value::equals))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Origin is not allowed");
    }
    private AuthPrincipal manager(Authentication authentication) {
        if(authentication==null || !(authentication.getPrincipal() instanceof AuthPrincipal p) || !List.of("OWNER","ADMIN").contains(p.role()))
            throw new AccessDeniedException("Clinic administrator required");
        return p;
    }
    private void event(UUID clinic,UUID actor,String action,UUID subject,String detail) {
        db.update("INSERT INTO clinic_admin_event(clinic_id,actor_id,action,subject_id,detail) VALUES(?,?,?,?,?)",clinic,actor,action,subject,detail);
    }
    @GetMapping("/status")
    public Map<String,Boolean> registrationStatus() {
        return Map.of("selfServiceEnabled",selfServiceEnabled);
    }
    @PostMapping("/clinics") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody CreateClinic input,HttpServletRequest request) {
        origin(request);
        if(!selfServiceEnabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Clinic registration is not open");
        String verified=email(google.verifiedEmail(required(input.googleIdToken(),8192)));
        String slug=required(input.slug(),63);
        if(!slug.matches("[a-z0-9][a-z0-9-]{2,62}") || slug.endsWith("-")) throw bad("Invalid clinic slug");
        String timezone=required(input.timezone(),80);
        try {ZoneId.of(timezone);} catch(Exception e){throw bad("Invalid timezone");}
        String clinicName=required(input.name(),160), branchName=required(input.branchName(),160), ownerName=required(input.ownerName(),160);
        UUID clinic=UUID.randomUUID(), branch=UUID.randomUUID(), owner=UUID.randomUUID();
        try {
            return TenantContext.callAs(clinic, () -> tx.execute(status -> {
                // The clinic root is deliberately not RLS-protected, but its child records are.
                db.update("INSERT INTO clinic(id,slug,name,timezone,email) VALUES(?,?,?,?,?)",clinic,slug,clinicName,timezone,verified);
                db.update("INSERT INTO branch(id,clinic_id,name) VALUES(?,?,?)",branch,clinic,branchName);
                db.update("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES(?,?,?,'',?,'OWNER')",owner,clinic,verified,ownerName);
                db.update("INSERT INTO user_branch(clinic_id,user_id,branch_id) VALUES(?,?,?)",clinic,owner,branch);
                event(clinic,owner,"CLINIC_CREATED",clinic,"Initial clinic and owner provisioned");
                return Map.<String,Object>of("clinicId",clinic,"clinicSlug",slug,"branchId",branch,"ownerId",owner);
            }));
        } catch (DuplicateKeyException duplicate) {
            // The unique clinic-code constraint is the final authority in a concurrent race.
            throw new ResponseStatusException(HttpStatus.CONFLICT,"That clinic code is already in use.");
        }
    }
    @PostMapping("/invitations") @ResponseStatus(HttpStatus.CREATED)
    public InvitationCreated invite(Authentication auth,@RequestBody Invite input) {
        AuthPrincipal user=manager(auth);
        if(input==null) throw bad("Invitation details are required.");
        String invitedEmail=email(input.email());
        if(!invitedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw bad("Enter a valid email address.");
        String role=input.role()==null?"":input.role().strip();
        if(role.isEmpty()) throw bad("Choose a staff role.");
        if(!List.of("ADMIN","DENTIST","FRONT_DESK").contains(role)) throw bad("Choose Administrator, Dentist, or Front Desk.");
        if(input.branchId()==null) throw bad("Choose a branch.");
        return tx.execute(status -> {
            // Serialize invitation creation for this tenant so simultaneous duplicate submissions
            // cannot race past the pending-invitation check. Tenant scope still comes only from auth.
            if(db.queryForList("SELECT id FROM clinic WHERE id=? FOR UPDATE",user.clinicId()).isEmpty())
                throw bad("Clinic is unavailable.");
            Integer active=db.queryForObject("SELECT count(*) FROM branch WHERE clinic_id=? AND id=? AND active=true",Integer.class,user.clinicId(),input.branchId());
            if(active==null || active!=1) throw bad("Choose an active branch in your clinic.");
            Integer duplicate=db.queryForObject("""
                SELECT count(*) FROM staff_invitation
                WHERE clinic_id=? AND lower(email)=? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at>now()
                """,Integer.class,user.clinicId(),invitedEmail);
            if(duplicate!=null && duplicate>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"An active invitation already exists for this email.");
            byte[] bytes=new byte[32];random.nextBytes(bytes);
            String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String token=user.clinicId()+"."+secret;
            UUID id=UUID.randomUUID();
            db.update("INSERT INTO staff_invitation(id,clinic_id,email,role,branch_id,token_hash,invited_by,expires_at) VALUES(?,?,?,?,?,?,?,?)",
                id,user.clinicId(),invitedEmail,role,input.branchId(),hash(token),user.userId(),java.sql.Timestamp.from(Instant.now().plusSeconds(604800)));
            event(user.clinicId(),user.userId(),"INVITATION_CREATED",id,role);
            // One-time token is shown to the inviting manager only. Never persisted in plaintext.
            return new InvitationCreated(id,token,Instant.now().plusSeconds(604800).toString());
        });
    }
    @GetMapping("/invitations")
    public List<Map<String,Object>> invitations(Authentication auth) {
        UUID clinic=manager(auth).clinicId();
        return db.queryForList("SELECT id,email,role,branch_id,expires_at,accepted_at,revoked_at FROM staff_invitation WHERE clinic_id=? ORDER BY created_at DESC",clinic);
    }
    @PostMapping("/invitations/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth,@PathVariable UUID id) {
        AuthPrincipal p=manager(auth);
        int count=db.update("UPDATE staff_invitation SET revoked_at=now() WHERE id=? AND clinic_id=? AND accepted_at IS NULL AND revoked_at IS NULL",id,p.clinicId());
        if(count!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        event(p.clinicId(),p.userId(),"INVITATION_REVOKED",id,null);
    }
    @PostMapping("/accept")
    public Map<String,Object> accept(@RequestBody Accept input,HttpServletRequest request) {
        origin(request);
        String verified=email(google.verifiedEmail(required(input.googleIdToken(),8192)));
        String token=required(input.token(),100);
        if(!token.matches("[0-9a-fA-F-]{36}\\.[A-Za-z0-9_-]{43}")) throw bad("Invalid invitation token");
        UUID clinic;
        try {clinic=UUID.fromString(token.substring(0,36));} catch(Exception ex){throw bad("Invalid invitation token");}
        String name=required(input.fullName(),160);
        return TenantContext.callAs(clinic,()->tx.execute(status -> {
            var invites=db.queryForList("SELECT id,email,role,branch_id FROM staff_invitation WHERE clinic_id=? AND token_hash=? AND revoked_at IS NULL AND accepted_at IS NULL AND expires_at>now() FOR UPDATE",clinic,hash(token));
            if(invites.size()!=1 || !verified.equalsIgnoreCase((String)invites.get(0).get("email")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid or expired invitation");
            Map<String,Object> inv=invites.get(0);
            Integer prior=db.queryForObject("SELECT count(*) FROM app_user WHERE clinic_id=? AND lower(email)=?",Integer.class,clinic,verified);
            if(prior!=null && prior>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Staff already exists");
            UUID staff=UUID.randomUUID();
            db.update("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES(?,?,?,'',?,?)",staff,clinic,verified,name,inv.get("role"));
            db.update("INSERT INTO user_branch(clinic_id,user_id,branch_id) VALUES(?,?,?)",clinic,staff,inv.get("branch_id"));
            db.update("UPDATE staff_invitation SET accepted_at=now() WHERE clinic_id=? AND id=?",clinic,inv.get("id"));
            event(clinic,staff,"INVITATION_ACCEPTED",(UUID)inv.get("id"),null);
            return Map.<String,Object>of("clinicId",clinic,"userId",staff,"role",inv.get("role"));
        }));
    }
    @GetMapping("/dentists")
    public List<Map<String,Object>> dentists(Authentication auth) {
        AuthPrincipal p=manager(auth);
        return db.queryForList("SELECT id,user_id,full_name,license_no,specialty,calendar_color,active FROM dentist WHERE clinic_id=? ORDER BY full_name",p.clinicId());
    }
    @PostMapping("/dentists") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createDentist(Authentication auth,@RequestBody DentistInput input) {
        AuthPrincipal p=manager(auth);
        if(input.userId()==null) throw bad("Dentist staff account required");
        String fullName=required(input.fullName(),160),license=required(input.licenseNo(),100);
        return tx.execute(status->{
            Integer valid=db.queryForObject("SELECT count(*) FROM app_user WHERE clinic_id=? AND id=? AND role='DENTIST' AND active=true",Integer.class,p.clinicId(),input.userId());
            if(valid==null||valid!=1) throw bad("Select an active dentist from your clinic");
            UUID id=UUID.randomUUID();
            db.update("INSERT INTO dentist(id,clinic_id,user_id,full_name,license_no,specialty,calendar_color) VALUES(?,?,?,?,?,?,?)",
                id,p.clinicId(),input.userId(),fullName,license,
                input.specialty()==null?null:required(input.specialty(),150),input.calendarColor()==null?null:required(input.calendarColor(),30));
            event(p.clinicId(),p.userId(),"DENTIST_CREATED",id,null);
            return db.queryForMap("SELECT id,user_id,full_name,license_no,specialty,calendar_color,active FROM dentist WHERE clinic_id=? AND id=?",p.clinicId(),id);
        });
    }
}
