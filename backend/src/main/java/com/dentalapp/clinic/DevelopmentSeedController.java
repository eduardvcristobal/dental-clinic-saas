package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.service.NumberingService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Explicitly gated generator of synthetic clinic records for local workflow testing. */
@RestController
@RequestMapping("/api/dev/seed")
public class DevelopmentSeedController {
    private final JdbcTemplate db;
    private final NumberingService numbering;
    private final boolean allowSeed;

    public DevelopmentSeedController(JdbcTemplate db, NumberingService numbering,
            @Value("${app.seed.allow-seed:false}") boolean allowSeed) {
        this.db = db;
        this.numbering = numbering;
        this.allowSeed = allowSeed;
    }

    private AuthPrincipal manager(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal user))
            throw new AccessDeniedException("Clinic staff authentication required");
        if (!List.of("OWNER", "ADMIN").contains(user.role()))
            throw new AccessDeniedException("Only clinic owners and administrators can seed test records");
        return user;
    }

    private void requireEnabled() {
        if (!allowSeed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Development data seeding is disabled");
    }

    @GetMapping("/status")
    public Map<String,Boolean> status(Authentication authentication) {
        manager(authentication);
        return Map.of("enabled", allowSeed);
    }

    @PostMapping("/patients") @Transactional
    public Map<String,Object> seedPatients(Authentication authentication,
            @RequestParam(defaultValue="10") int count) {
        requireEnabled();
        AuthPrincipal user = manager(authentication);
        if (count < 1 || count > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient seed count must be between 1 and 100");
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            String chart = numbering.nextChartNo();
            db.update("""
                INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name,email,sms_opt_in)
                VALUES(?,?,?,?,?,?,false)
                """, id, user.clinicId(), chart, "Seed", "Patient " + chart.substring(2),
                "seed-" + id + "@example.invalid");
            db.update("INSERT INTO audit_log(clinic_id,user_id,action,entity,entity_id) VALUES (?,?,?,'patient',?)",
                user.clinicId(), user.userId(), "PATIENT_SEEDED", id);
        }
        return Map.of("created", count, "recordType", "synthetic patients");
    }
}
