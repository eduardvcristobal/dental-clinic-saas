package com.dentalapp.tenancy;

import java.util.UUID;

/** Holds the current clinic (tenant) for this thread. Always restored at request/job boundaries. */
public final class TenantContext {
    /** Used when no tenant is set; matches no rows, so unauthenticated code fails closed. */
    public static final UUID NONE = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLATFORM_ADMIN = ThreadLocal.withInitial(() -> false);

    private TenantContext() {}

    public static UUID get() { return CURRENT.get(); }
    public static void set(UUID clinicId) { CURRENT.set(clinicId); PLATFORM_ADMIN.set(false); }
    public static void setPlatformAdmin() { CURRENT.remove(); PLATFORM_ADMIN.set(true); }
    public static boolean isPlatformAdmin() { return Boolean.TRUE.equals(PLATFORM_ADMIN.get()); }
    public static void clear() { CURRENT.remove(); PLATFORM_ADMIN.remove(); }

    public static void runAs(UUID clinicId, Runnable task) {
        UUID previous = CURRENT.get();
        boolean previousPlatformAdmin = isPlatformAdmin();
        CURRENT.set(clinicId);
        PLATFORM_ADMIN.set(false);
        try { task.run(); }
        finally { restore(previous, previousPlatformAdmin); }
    }

    public static <T> T callAs(UUID clinicId, java.util.function.Supplier<T> task) {
        UUID previous = CURRENT.get();
        boolean previousPlatformAdmin = isPlatformAdmin();
        CURRENT.set(clinicId);
        PLATFORM_ADMIN.set(false);
        try { return task.get(); }
        finally { restore(previous, previousPlatformAdmin); }
    }

    private static void restore(UUID previous, boolean previousPlatformAdmin) {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        if (previousPlatformAdmin) PLATFORM_ADMIN.set(true); else PLATFORM_ADMIN.remove();
    }
}
