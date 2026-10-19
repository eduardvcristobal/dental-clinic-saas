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

@Entity @Table(name = "consent_form")
@Getter @Setter
public class ConsentForm extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    @Column(name = "form_type", nullable = false) private String formType;
    @Column(name = "signed_at") private Instant signedAt;
    @Column(name = "signed_by") private String signedBy;
    @ManyToOne(fetch = FetchType.LAZY) private FileAsset file;
}
