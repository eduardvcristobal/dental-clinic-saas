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

@Entity @Table(name = "treatment_plan")
@Getter @Setter
public class TreatmentPlan extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Dentist dentist;
    @Column(nullable = false) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PlanStatus status = PlanStatus.DRAFT;
    @Column(name = "accepted_at") private Instant acceptedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("phase ASC")
    private List<TreatmentPlanItem> items = new ArrayList<>();

    public void addItem(TreatmentPlanItem item) { item.setPlan(this); items.add(item); }
}
