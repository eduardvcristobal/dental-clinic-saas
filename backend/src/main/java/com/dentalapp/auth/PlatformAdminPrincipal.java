package com.dentalapp.auth;

import java.util.UUID;

/** A global platform identity intentionally has no clinic/tenant identifier. */
public record PlatformAdminPrincipal(UUID userId, String email) {}
