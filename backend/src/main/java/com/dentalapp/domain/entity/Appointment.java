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

/** Double-booking of a dentist or chair is rejected by DB exclusion constraints (V3). */
@Entity @Table(name = "appointment")
@Getter @Setter
public class Appointment extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Dentist dentist;
    @ManyToOne(fetch = FetchType.LAZY) private Chair chair;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appointment_type_id") private AppointmentType appointmentType;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AppointmentStatus status = AppointmentStatus.BOOKED;
    private String reason;
    private String notes;
    @Column(name = "created_by") private UUID createdBy;
}
