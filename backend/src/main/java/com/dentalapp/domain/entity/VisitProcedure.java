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

@Entity @Table(name = "visit_procedure")
@Getter @Setter
public class VisitProcedure extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Visit visit;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "plan_item_id") private TreatmentPlanItem planItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "procedure_id") private ProcedureCatalog procedure;
    @Column(name = "tooth_number") private Short toothNumber;
    private String surface;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
}
