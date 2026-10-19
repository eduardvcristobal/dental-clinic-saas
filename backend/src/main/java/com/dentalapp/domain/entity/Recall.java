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

@Entity @Table(name = "recall")
@Getter @Setter
public class Recall extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    private String reason = "Routine checkup";
    @Column(name = "contacted_at") private Instant contactedAt;
    @Column(nullable = false) private boolean completed = false;
}
