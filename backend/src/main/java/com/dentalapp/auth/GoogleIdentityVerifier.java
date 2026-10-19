package com.dentalapp.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GoogleIdentityVerifier {
    private final JwtDecoder decoder;
    private final String audience;
    GoogleIdentityVerifier(AuthProperties p) {
        this.audience = p.googleClientId();
        decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
    }
    public String verifiedEmail(String idToken) {
        if (audience==null || audience.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google login is not configured");
        try {
            Jwt jwt = decoder.decode(idToken);
            if (!("https://accounts.google.com".equals(String.valueOf(jwt.getIssuer())) ||
                  "accounts.google.com".equals(String.valueOf(jwt.getIssuer()))) ||
                !jwt.getAudience().contains(audience) ||
                !Boolean.TRUE.equals(jwt.getClaim("email_verified")) ||
                jwt.getExpiresAt()==null || !jwt.getExpiresAt().isAfter(java.time.Instant.now()))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google identity");
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            return email;
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google identity");
        }
    }
}
