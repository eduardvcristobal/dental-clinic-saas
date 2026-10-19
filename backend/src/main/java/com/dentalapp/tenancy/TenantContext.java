package com.dentalapp.tenancy;

import java.util.UUID;

/** Holds the current clinic (tenant) for this thread. Set by TenantFilter, or by jobs via runAs(). */
public final class TenantContext {
    /** Used when no tenant is set; matches no rows, so unauthenticated code fails closed. */
    public static final UUID NONE = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID get() { return CURRENT.get(); }
    public static void set(UUID clinicId) { CURRENT.set(clinicId); }
    public static void clear() { CURRENT.remove(); }

    public static void runAs(UUID clinicId, Runnable task) {
        UUID previous = CURRENT.get();
        CURRENT.set(clinicId);
        try { task.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
