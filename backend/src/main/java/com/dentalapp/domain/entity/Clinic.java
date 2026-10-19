package com.dentalapp.domain.entity;

import com.dentalapp.common.*;
import com.dentalapp.domain.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The tenant root: one row per customer clinic. Not tenant-scoped itself (login resolves it by slug). */
@Entity @Table(name = "clinic")
@Getter @Setter
public class Clinic extends BaseEntity {
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String timezone = "Asia/Manila";
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency = "PHP";
    private String phone;
    private String email;
    private String address;
    @Column(name = "plan_code", nullable = false) private String planCode = "TRIAL";
    @Enumerated(EnumType.STRING) @Column(name = "subscription_status", nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIALING;
    @Column(name = "trial_ends_at") private Instant trialEndsAt;
    @Column(nullable = false) private boolean active = true;
}
