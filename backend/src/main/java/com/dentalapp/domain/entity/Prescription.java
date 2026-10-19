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

@Entity @Table(name = "prescription")
@Getter @Setter
public class Prescription extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Visit visit;
    @Column(nullable = false) private String medication;
    private String dosage;
    private String instructions;
}
