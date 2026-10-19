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
import lombok.AccessLevel;

@Entity @Table(name = "treatment_plan_item")
@Getter @Setter
public class TreatmentPlanItem extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private TreatmentPlan plan;
    public void setPlan(TreatmentPlan plan) { this.plan = plan; }
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "procedure_id") private ProcedureCatalog procedure;
    /** FDI tooth number (11-48 permanent, 51-85 primary). */
    @Column(name = "tooth_number") private Short toothNumber;
    private String surface;
    @Column(nullable = false) private short phase = 1;
    @Column(nullable = false) private int quantity = 1;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal discount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PlanItemStatus status = PlanItemStatus.PLANNED;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "completed_visit_id") private Visit completedVisit;
}
