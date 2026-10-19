package com.dentalapp.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {
    private final JdbcTemplate jdbc;

    PlatformAdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ClinicSummary(UUID id, String slug, String name, String planCode,
                                String subscriptionStatus, boolean active, Instant createdAt,
                                String accessScope) {}

    @GetMapping("/clinics")
    List<ClinicSummary> clinics() {
        return jdbc.query("""
            SELECT c.id,c.slug,c.name,c.plan_code,c.subscription_status,c.active,c.created_at
            FROM clinic c ORDER BY c.created_at DESC,c.slug
            """, (rs, row) -> new ClinicSummary((UUID) rs.getObject("id"), rs.getString("slug"),
                rs.getString("name"), rs.getString("plan_code"), rs.getString("subscription_status"),
                rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant(),
                "DIRECTORY_ONLY"));
    }
}
