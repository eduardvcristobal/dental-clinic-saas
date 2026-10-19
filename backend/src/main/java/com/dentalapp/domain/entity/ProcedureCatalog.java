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

@Entity @Table(name = "procedure_catalog")
@Getter @Setter
public class ProcedureCatalog extends TenantEntity {
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String name;
    private String category;
    @Column(name = "default_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultPrice = BigDecimal.ZERO;
    @Column(name = "requires_tooth", nullable = false) private boolean requiresTooth;
    @Column(name = "requires_surface", nullable = false) private boolean requiresSurface;
    @Column(nullable = false) private boolean active = true;
}
