package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.auth.GoogleIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fast negative tests. Full replay/concurrency require restricted-role PostgreSQL integration. */
class ClinicOnboardingSecurityRegressionTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    GoogleIdentityVerifier google = mock(GoogleIdentityVerifier.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    ClinicOnboardingController controller = new ClinicOnboardingController(db, google, manager, true, "http://localhost:5173");

    @Test void platformAdministratorCannotActAsClinicManager() {
        var auth = new UsernamePasswordAuthenticationToken("platform-admin", "", List.of());
        assertThrows(AccessDeniedException.class, () -> controller.invitations(auth));
        assertThrows(AccessDeniedException.class, () -> controller.dentists(auth));
        verifyNoInteractions(db);
    }

    @Test void publicClinicRegistrationRequiresApprovedOriginBeforeGoogleVerification() {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://attacker.invalid");
        assertThrows(ResponseStatusException.class, () -> controller.create(
                new ClinicOnboardingController.CreateClinic("fake", "example", "Clinic", "Asia/Manila", "Main", "Owner"), request));
        verifyNoInteractions(google, db);
    }

    @Test void publicClinicRegistrationIsDisabledUnlessExplicitlyEnabled() {
        var disabled = new ClinicOnboardingController(db, google, manager, false, "http://localhost:5173");
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://localhost:5173");
        var error = assertThrows(ResponseStatusException.class, () -> disabled.create(
                new ClinicOnboardingController.CreateClinic("fake", "example", "Clinic", "Asia/Manila", "Main", "Owner"), request));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(google, db);
    }

    @Test void staffCannotCreateAnotherOwnerOrInviteToInvalidRole() {
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "OWNER"), "", List.of());
        assertThrows(ResponseStatusException.class, () -> controller.invite(auth,
                new ClinicOnboardingController.Invite("first@invalid.test", "OWNER", UUID.randomUUID())));
        verifyNoInteractions(db);
    }
}
