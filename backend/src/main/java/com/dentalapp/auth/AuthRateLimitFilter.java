package com.dentalapp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Small per-instance backstop; production deployments should also rate-limit at a shared edge. */
@Component
public final class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_KEYS = 10_000;
    private final int loginLimit;
    private final int refreshLimit;
    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) { return size() > MAX_KEYS; }
    };

    @Autowired
    public AuthRateLimitFilter(
            @Value("${app.auth.login-requests-per-minute:10}") int loginLimit,
            @Value("${app.auth.refresh-requests-per-minute:30}") int refreshLimit) {
        this(loginLimit, refreshLimit, Clock.systemUTC());
    }

    AuthRateLimitFilter(int loginLimit, int refreshLimit, Clock clock) {
        if (loginLimit < 1 || refreshLimit < 1) throw new IllegalArgumentException("Auth rate limits must be positive");
        this.loginLimit = loginLimit;
        this.refreshLimit = refreshLimit;
        this.clock = clock;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit;
        if (!"POST".equals(request.getMethod())) { chain.doFilter(request, response); return; }
        if ("/api/auth/google".equals(path) || "/api/auth/platform-admin/google".equals(path)) limit = loginLimit;
        else if ("/api/auth/refresh".equals(path)) limit = refreshLimit;
        else { chain.doFilter(request, response); return; }

        if (!allow(path + ":" + request.getRemoteAddr(), limit, clock.instant())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    synchronized boolean allow(String key, int limit, Instant now) {
        Window window = windows.get(key);
        if (window == null || !now.isBefore(window.startedAt().plus(WINDOW))) {
            windows.put(key, new Window(now, 1));
            return true;
        }
        if (window.count() >= limit) return false;
        windows.put(key, new Window(window.startedAt(), window.count() + 1));
        return true;
    }

    private record Window(Instant startedAt, int count) {}
}
