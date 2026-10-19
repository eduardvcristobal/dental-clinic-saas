package com.dentalapp.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthRateLimitFilterTest {
    @Test void appliesPerKeyLimitAndAllowsANewWindow() {
        var filter = new AuthRateLimitFilter(2, 3, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(filter.allow("google:127.0.0.1", 2, Instant.EPOCH)).isTrue();
        assertThat(filter.allow("google:127.0.0.1", 2, Instant.EPOCH.plusSeconds(1))).isTrue();
        assertThat(filter.allow("google:127.0.0.1", 2, Instant.EPOCH.plusSeconds(2))).isFalse();
        assertThat(filter.allow("google:127.0.0.2", 2, Instant.EPOCH.plusSeconds(2))).isTrue();
        assertThat(filter.allow("google:127.0.0.1", 2, Instant.EPOCH.plusSeconds(60))).isTrue();
    }

    @Test void rejectsNonPositiveLimits() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new AuthRateLimitFilter(0, 3, Clock.systemUTC()));
    }
}
