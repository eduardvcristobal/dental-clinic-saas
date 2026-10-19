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

@Entity @Table(name = "patient")
@Getter @Setter
public class Patient extends TenantEntity {
    @Column(name = "chart_no", nullable = false) private String chartNo;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(name = "birth_date") private LocalDate birthDate;
    private String sex;
    private String phone;
    private String email;
    private String address;
    /** Parent or guardian for minors. */
    @ManyToOne(fetch = FetchType.LAZY) private Patient guardian;
    @Column(name = "emergency_name") private String emergencyName;
    @Column(name = "emergency_phone") private String emergencyPhone;
    @Column(name = "sms_opt_in", nullable = false) private boolean smsOptIn = true;
    private String notes;
    /** Soft delete (archive). Patient records are never hard-deleted. */
    @Column(name = "deleted_at") private Instant deletedAt;
}
