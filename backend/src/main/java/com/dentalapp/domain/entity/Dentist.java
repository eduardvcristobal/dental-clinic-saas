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

@Entity @Table(name = "dentist")
@Getter @Setter
public class Dentist extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY) private AppUser user;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(name = "license_no") private String licenseNo;
    private String specialty;
    @Column(name = "calendar_color") private String calendarColor;
    @Column(nullable = false) private boolean active = true;
}
