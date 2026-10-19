package com.dentalapp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String googleClientId, String jwtSecret, boolean cookieSecure, String allowedOrigins) {}
