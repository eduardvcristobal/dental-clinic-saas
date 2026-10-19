package com.dentalapp.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/** Immutable record of who viewed or changed what. Insert-only. */
@Entity @Table(name = "audit_log")
@Getter @Setter
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @TenantId @Column(name = "clinic_id", nullable = false, updatable = false) private UUID clinicId;
    @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private String action;
    @Column(nullable = false) private String entity;
    @Column(name = "entity_id") private UUID entityId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, Object> details;
    @Column(name = "ip_address") private String ipAddress;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
