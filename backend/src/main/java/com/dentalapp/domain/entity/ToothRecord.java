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

/** One finding or treatment on a tooth/surface. The odontogram is the latest state of these rows. */
@Entity @Table(name = "tooth_record")
@Getter @Setter
public class ToothRecord extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @Column(name = "tooth_number", nullable = false) private Short toothNumber;
    private String surface;
    /** caries, filling, crown, missing, implant, ... */
    @Column(name = "condition_code", nullable = false) private String conditionCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ToothStatus status = ToothStatus.EXISTING;
    @ManyToOne(fetch = FetchType.LAZY) private Visit visit;
    private String notes;
    @Column(name = "recorded_by") private UUID recordedBy;
}
