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

@Entity @Table(name = "chair")
@Getter @Setter
public class Chair extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Branch branch;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private boolean active = true;
}
