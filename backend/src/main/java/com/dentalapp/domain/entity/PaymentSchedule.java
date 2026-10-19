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

/** One installment due date for an invoice. */
@Entity @Table(name = "payment_schedule")
@Getter @Setter
public class PaymentSchedule extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Invoice invoice;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private boolean paid = false;
}
