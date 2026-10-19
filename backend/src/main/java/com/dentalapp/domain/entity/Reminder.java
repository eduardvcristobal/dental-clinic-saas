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

@Entity @Table(name = "reminder")
@Getter @Setter
public class Reminder extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Appointment appointment;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ReminderChannel channel;
    @Column(name = "scheduled_for", nullable = false) private Instant scheduledFor;
    @Column(name = "sent_at") private Instant sentAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ReminderStatus status = ReminderStatus.SCHEDULED;
    @Column(name = "patient_reply") private String patientReply;
}
