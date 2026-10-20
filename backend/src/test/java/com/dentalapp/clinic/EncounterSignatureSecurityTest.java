package com.dentalapp.clinic;
import com.dentalapp.auth.AuthPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EncounterSignatureSecurityTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final EncounterSignatureController controller=new EncounterSignatureController(db);
 private UsernamePasswordAuthenticationToken as(String role) {
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
 }
 @Test void frontDeskCannotSign(){assertThrows(AccessDeniedException.class,()->
  controller.sign(as("FRONT_DESK"),UUID.randomUUID(),new EncounterSignatureController.SignInput(1L)));
  verifyNoInteractions(db);
 }
 @Test void anonymousCannotViewSignedNote(){assertThrows(AccessDeniedException.class,()->
  controller.signed(null,UUID.randomUUID()));verifyNoInteractions(db);
 }
 @Test void missingVersionRejected(){assertThrows(ResponseStatusException.class,()->
  controller.sign(as("DENTIST"),UUID.randomUUID(),new EncounterSignatureController.SignInput(null)));
  verifyNoInteractions(db);
 }
 @Test void invalidAmendmentReasonRejected(){assertThrows(ResponseStatusException.class,()->
  controller.amend(as("DENTIST"),UUID.randomUUID(),new EncounterSignatureController.AmendInput("a","synthetic amendment")));
  verifyNoInteractions(db);
 }
 @Test void crossClinicVisitIsNotVisible(){
  UUID id=UUID.randomUUID(),clinic=UUID.randomUUID();
  var authentication=new UsernamePasswordAuthenticationToken(
    new AuthPrincipal(UUID.randomUUID(),clinic,"DENTIST"),null);
  when(db.queryForList(anyString(),eq(clinic),eq(id))).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->controller.signed(authentication,id));
  verify(db).queryForList(contains("clinic_id=? AND id=?"),eq(clinic),eq(id));
 }
}
