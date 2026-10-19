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

@Entity @Table(name = "visit")
@Getter @Setter
public class Visit extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY) private Appointment appointment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Dentist dentist;
    @Column(name = "started_at", nullable = false) private Instant startedAt = Instant.now();
    @Column(name = "ended_at") private Instant endedAt;
    /** SOAP notes. */
    private String subjective;
    private String objective;
    private String assessment;
    @Column(name = "plan_notes") private String planNotes;
    /** Once set, the service layer must reject edits to the notes. */
    @Column(name = "locked_at") private Instant lockedAt;

    @OneToMany(mappedBy = "visit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VisitProcedure> procedures = new ArrayList<>();
}
