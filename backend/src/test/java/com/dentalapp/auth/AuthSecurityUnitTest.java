package com.dentalapp.auth;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import com.dentalapp.tenancy.TenantContext;

class AuthSecurityUnitTest {
  @Test void refreshCookieRejectsInvalidShapes() {
    assertThrows(ResponseStatusException.class, () -> AuthService.cookieClinic(null));
    assertThrows(ResponseStatusException.class, () -> AuthService.cookieClinic("other-clinic"));
    assertThrows(ResponseStatusException.class, () -> AuthService.cookieClinic(UUID.randomUUID()+".tiny"));
  }
  @Test void refreshCookieExtractsTenantWithoutGrantingAuthorization() {
    UUID tenant=UUID.randomUUID();
    String cookie=tenant+"."+"a".repeat(43);
    assertEquals(tenant,AuthService.cookieClinic(cookie));
  }
  @Test void tokenHashDoesNotStoreRawCookie() {
    String sample=UUID.randomUUID()+"."+"b".repeat(43);
    assertEquals(AuthService.hash(sample), AuthService.hash(sample));
    assertNotEquals(sample, AuthService.hash(sample));
    assertNotEquals(AuthService.hash(sample), AuthService.hash(sample+"x"));
  }
  @Test void sensitiveAuthenticationDtosRedactIdentityAccessAndRefreshTokensInToString() {
    String idToken="synthetic-google-identity-token";
    String accessToken="synthetic-access-token";
    String refreshToken="synthetic-refresh-cookie";
    assertFalse(new AuthController.GoogleLogin("clinic",idToken).toString().contains(idToken));
    assertFalse(new AuthController.PlatformAdminGoogleLogin(idToken).toString().contains(idToken));
    assertTrue(new AuthService.Session(accessToken,java.util.Map.of(),java.util.Map.of()).toString().contains("[REDACTED]"));
    assertFalse(new AuthService.Session(accessToken,java.util.Map.of(),java.util.Map.of()).toString().contains(accessToken));
    assertFalse(new AuthService.Issued(new AuthService.Session(accessToken,java.util.Map.of(),java.util.Map.of()),refreshToken).toString().contains(refreshToken));
  }
  @Test void tenantContextRestoresPreviousValueAndClearsAfterFailure() {
    UUID outer=UUID.randomUUID(), inner=UUID.randomUUID();
    TenantContext.set(outer);
    assertThrows(IllegalStateException.class, () -> TenantContext.callAs(inner, () -> { throw new IllegalStateException(); }));
    assertEquals(outer,TenantContext.get());
    TenantContext.clear();
    TenantContext.runAs(inner, () -> assertEquals(inner,TenantContext.get()));
    assertNull(TenantContext.get());
  }
}
