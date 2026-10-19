package com.dentalapp.tenancy;

import java.util.UUID;

/** Implement on your authenticated principal (e.g. from the JWT "clinic_id" claim). */
public interface TenantPrincipal {
    UUID getClinicId();
}
