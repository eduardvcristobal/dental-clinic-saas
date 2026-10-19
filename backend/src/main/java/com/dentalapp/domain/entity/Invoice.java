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

@Entity @Table(name = "invoice")
@Getter @Setter
public class Invoice extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY) private Visit visit;
    @Column(name = "invoice_no", nullable = false) private String invoiceNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InvoiceStatus status = InvoiceStatus.DRAFT;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    private String notes;
    @Column(name = "created_by") private UUID createdBy;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    public void addLine(InvoiceLine line) { line.setInvoice(this); lines.add(line); }
}
