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

@Entity @Table(name = "appointment_type")
@Getter @Setter
public class AppointmentType extends TenantEntity {
    @Column(nullable = false) private String name;
    @Column(name = "default_minutes", nullable = false) private int defaultMinutes = 30;
    private String color;
    @Column(nullable = false) private boolean active = true;
}
