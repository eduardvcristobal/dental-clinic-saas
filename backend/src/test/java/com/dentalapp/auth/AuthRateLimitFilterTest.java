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

    @Test void patientActionQuotaHasSeparateLimitAndKeys(){
        var filter = new AuthRateLimitFilter(10, 30, 2, Clock.systemUTC());
        var time = Instant.parse("2026-10-21T01:00:00Z");
        assertThat(filter.allow("/api/patient-actions/redeem:192.0.2.5", 2, time)).isTrue();
        assertThat(filter.allow("/api/patient-actions/redeem:192.0.2.5", 2, time.plusSeconds(1))).isTrue();
        assertThat(filter.allow("/api/patient-actions/redeem:192.0.2.5", 2, time.plusSeconds(2))).isFalse();
        assertThat(filter.allow("/api/patient-actions/redeem:192.0.2.6", 2, time.plusSeconds(2))).isTrue();
        assertThat(filter.allow("/api/patient-actions/redeem:192.0.2.5", 2, time.plusSeconds(60))).isTrue();
    }

    @Test void rejectsInvalidPublicActionThrottle() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new AuthRateLimitFilter(10, 30, 0, Clock.systemUTC()));
    }

    @Test void rejectsNonPositiveLimits() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new AuthRateLimitFilter(0, 3, Clock.systemUTC()));
    }
}
