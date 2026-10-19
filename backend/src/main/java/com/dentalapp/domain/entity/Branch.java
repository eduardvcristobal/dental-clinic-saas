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

@Entity @Table(name = "branch")
@Getter @Setter
public class Branch extends TenantEntity {
    @Column(nullable = false) private String name;
    private String address;
    private String phone;
    @Column(nullable = false) private boolean active = true;
}
