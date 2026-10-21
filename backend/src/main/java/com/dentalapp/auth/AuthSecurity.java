package com.dentalapp.auth;

import com.dentalapp.tenancy.TenantFilter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration @EnableWebSecurity @EnableConfigurationProperties(AuthProperties.class)
public class AuthSecurity {
    private byte[] secret(AuthProperties p) {
        byte[] value = p.jwtSecret() == null ? new byte[0] : p.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (value.length < 32) throw new IllegalStateException("APP_JWT_SECRET must have at least 32 UTF-8 bytes");
        return value;
    }
    @Bean JwtEncoder jwtEncoder(AuthProperties p) { return new NimbusJwtEncoder(new ImmutableSecret<>(secret(p))); }
    @Bean JwtDecoder jwtDecoder(AuthProperties p) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret(p), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("dental-clinic-saas"), jwt -> {
                    try {
                        UUIDFromClaim.require(jwt.getSubject());
                        String role = jwt.getClaimAsString("role");
                        if ("PLATFORM_ADMIN".equals(role)) {
                            if (!Boolean.TRUE.equals(jwt.getClaim("platform_admin")) || jwt.getClaims().containsKey("clinic_id"))
                                throw new IllegalArgumentException("invalid platform admin claims");
                        } else {
                            UUIDFromClaim.require(jwt.getClaimAsString("clinic_id"));
                            if (Boolean.TRUE.equals(jwt.getClaim("platform_admin")))
                                throw new IllegalArgumentException("tenant user must not have platform-admin claim");
                            if (role == null || !List.of("OWNER", "ADMIN", "DENTIST", "FRONT_DESK").contains(role))
                                throw new IllegalArgumentException("invalid role");
                        }
                        return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
                    } catch (RuntimeException invalid) {
                        return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid access token claims", null));
                    }
                }));
        return d;
    }
    @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        converter.setPrincipalClaimName("sub");
        return converter;
    }
    // TenantFilter must run ONLY inside Spring Security, after Bearer authentication.
    // Prevent Spring Boot from auto-registering the @Component as a servlet filter first.
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(AuthRateLimitFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, TenantFilter tenantFilter, AuthRateLimitFilter rateLimitFilter, JwtDecoder decoder) throws Exception {
        http.csrf(csrf -> csrf.disable()) // Access-token APIs use Bearer, never cookie authentication.
          .headers(headers -> headers.referrerPolicy(policy -> policy.policy(
              org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
          .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .cors(cors -> {})
          .authorizeHttpRequests(reg -> reg
             .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
             .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
             .requestMatchers("/api/onboarding/clinics", "/api/onboarding/accept", "/api/patient-actions/redeem", "/api/notification-webhooks/resend", "/api/notification-webhooks/twilio").permitAll()
             .requestMatchers("/api/auth/google", "/api/auth/platform-admin/google", "/api/auth/refresh", "/api/auth/logout").permitAll()
             .requestMatchers("/api/platform-admin/**").hasRole("PLATFORM_ADMIN")
             .anyRequest().authenticated())
          .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(jwt -> {
              Jwt token = jwt;
              if ("PLATFORM_ADMIN".equals(token.getClaimAsString("role"))) {
                  var principal = new PlatformAdminPrincipal(UUIDFromClaim.require(token.getSubject()), token.getClaimAsString("email"));
                  return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                      principal, token, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
              }
              AuthPrincipal principal = new AuthPrincipal(UUIDFromClaim.require(token.getSubject()),
                  UUIDFromClaim.require(token.getClaimAsString("clinic_id")), token.getClaimAsString("role"));
              return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                  principal, token, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
          })));
        http.addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
    @Bean UrlBasedCorsConfigurationSource corsConfigurationSource(AuthProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        if (props.allowedOrigins() != null && !props.allowedOrigins().isBlank())
            config.setAllowedOrigins(Arrays.stream(props.allowedOrigins().split(",")).map(String::trim).toList());
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    static final class UUIDFromClaim {
        static java.util.UUID require(String value) { return java.util.UUID.fromString(value); }
    }
}
