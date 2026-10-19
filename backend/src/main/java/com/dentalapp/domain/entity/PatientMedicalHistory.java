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

@Entity @Table(name = "patient_medical_history")
@Getter @Setter
public class PatientMedicalHistory extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Patient patient;
    private String allergies;
    private String conditions;
    private String medications;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private Map<String, Object> questionnaire;
    @Column(name = "updated_by") private UUID updatedBy;
}
