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

@Entity @Table(name = "invoice_line")
@Getter @Setter
public class InvoiceLine extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Invoice invoice;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "procedure_id") private ProcedureCatalog procedure;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "plan_item_id") private TreatmentPlanItem planItem;
    @Column(nullable = false) private String description;
    @Column(nullable = false) private int quantity = 1;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal discount = BigDecimal.ZERO;
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2) private BigDecimal lineTotal;
}
