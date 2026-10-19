package com.dentalapp.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Base for every tenant-owned table. Hibernate fills clinic_id on insert from the current tenant and
 * appends "clinic_id = ?" to every query, find, update and delete, so cross-tenant access is impossible
 * through JPA. PostgreSQL RLS (V8) is the second layer for native SQL and bugs.
 */
@MappedSuperclass
@Getter @Setter
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;
}
