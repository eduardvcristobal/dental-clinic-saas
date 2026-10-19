package com.dentalapp.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the tenant from the AUTHENTICATED principal only, never from a header or request parameter.
 * Register it AFTER your authentication filter in the security filter chain. For the login endpoint,
 * resolve the clinic by slug (ClinicRepository) and call TenantContext.set(...) in AuthService.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof TenantPrincipal p) {
                TenantContext.set(p.getClinicId());
            }
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
