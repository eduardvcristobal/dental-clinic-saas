package com.dentalapp.tenancy;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID id = TenantContext.get();
        return id != null ? id : TenantContext.NONE;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
