package com.dentalapp.auth;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

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
}
