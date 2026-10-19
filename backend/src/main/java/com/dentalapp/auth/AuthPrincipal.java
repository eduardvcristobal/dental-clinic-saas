package com.dentalapp.auth;

import com.dentalapp.tenancy.TenantPrincipal;
import java.util.UUID;
public record AuthPrincipal(UUID userId, UUID clinicId, String role) implements TenantPrincipal {
    @Override public UUID getClinicId() { return clinicId; }
}
