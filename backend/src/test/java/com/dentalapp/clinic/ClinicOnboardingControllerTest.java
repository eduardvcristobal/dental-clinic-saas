package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.auth.GoogleIdentityVerifier;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClinicOnboardingControllerTest {
 private final JdbcTemplate db = mock(JdbcTemplate.class);
 private final GoogleIdentityVerifier google = mock(GoogleIdentityVerifier.class);
 private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
 private final ClinicOnboardingController controller = new ClinicOnboardingController(db, google, transactions, false, "http://localhost:5173");
 private UsernamePasswordAuthenticationToken login(String role) {
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), role),"", List.of());
 }
 @Test void selfServiceDisabledByDefault() {
  var error=assertThrows(ResponseStatusException.class,()->controller.create(
   new ClinicOnboardingController.CreateClinic("token","example","Clinic","Asia/Manila","Main","Owner"),new MockHttpServletRequest()));
  assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode()); verifyNoInteractions(db,google,transactions);
 }
 @Test void onlyManagersCanInvite() {
  assertThrows(AccessDeniedException.class,()->controller.invite(login("DENTIST"),new ClinicOnboardingController.Invite("dentist@example.com","DENTIST",UUID.randomUUID())));
  assertThrows(AccessDeniedException.class,()->controller.invite(login("FRONT_DESK"),new ClinicOnboardingController.Invite("dentist@example.com","DENTIST",UUID.randomUUID())));
  verifyNoInteractions(db);
 }
 @Test void ownerRoleCannotBeInvited() {
  var error=assertThrows(ResponseStatusException.class,()->controller.invite(login("OWNER"),new ClinicOnboardingController.Invite("owner@example.com","OWNER",UUID.randomUUID())));
  assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode()); verifyNoInteractions(db);
 }
 @Test void badInvitationTokenDeniedBeforeTenantContext() {
  when(google.verifiedEmail("proof")).thenReturn("staff@example.com");
  var error=assertThrows(ResponseStatusException.class,()->controller.accept(new ClinicOnboardingController.Accept("proof","invalid","Staff"),new MockHttpServletRequest()));
  assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode()); verifyNoInteractions(db,transactions);
 }
 @Test void unauthorizedOriginRejected() {
  var request=new MockHttpServletRequest();request.addHeader("Origin","https://untrusted.example");
  var error=assertThrows(ResponseStatusException.class,()->controller.accept(new ClinicOnboardingController.Accept("proof","bad","Staff"),request));
  assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode()); verifyNoInteractions(google,db);
 }
}
