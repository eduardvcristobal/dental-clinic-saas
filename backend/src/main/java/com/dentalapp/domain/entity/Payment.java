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

/** Append-only ledger row. Never change the amount or delete; void with voidedAt + voidReason. */
@Entity @Table(name = "payment")
@Getter @Setter
public class Payment extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Invoice invoice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @Column(nullable = false, precision = 12, scale = 2, updatable = false) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod method;
    private String reference;
    @Column(name = "paid_at", nullable = false) private Instant paidAt = Instant.now();
    @Column(name = "received_by") private UUID receivedBy;
    @Column(name = "voided_at") private Instant voidedAt;
    @Column(name = "void_reason") private String voidReason;

    public boolean isVoided() { return voidedAt != null; }
}
