package com.dentalapp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String googleClientId, String jwtSecret, boolean cookieSecure, String allowedOrigins,
                             String googleJwkSetUri, String platformAdminEmails) {
    public boolean isPlatformAdmin(String email) {
        if (email == null || platformAdminEmails == null || platformAdminEmails.isBlank()) return false;
        return java.util.Arrays.stream(platformAdminEmails.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .anyMatch(allowed -> allowed.equalsIgnoreCase(email.trim()));
    }
}
