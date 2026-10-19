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

@Entity @Table(name = "app_user")
@Getter @Setter
public class AppUser extends TenantEntity {
    @Column(nullable = false) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "last_login_at") private Instant lastLoginAt;

    /** Branches this user may work in. user_branch.clinic_id fills itself from the session tenant. */
    @ManyToMany
    @JoinTable(name = "user_branch",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "branch_id"))
    private Set<Branch> branches = new HashSet<>();
}
