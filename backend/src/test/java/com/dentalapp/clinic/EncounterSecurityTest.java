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

class EncounterSecurityTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final EncounterController api=new EncounterController(db);
 private UsernamePasswordAuthenticationToken as(String role,UUID clinic) {
   return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,role),null);
 }
 @Test void frontDeskDeniedForReadingAndStarting() {
   var auth=as("FRONT_DESK",UUID.randomUUID());
   assertThrows(AccessDeniedException.class,()->api.get(auth,UUID.randomUUID()));
   assertThrows(AccessDeniedException.class,()->api.start(auth,new EncounterController.StartInput(UUID.randomUUID(),UUID.randomUUID(),null)));
   verifyNoInteractions(db);
 }
 @Test void anonymousRejected() {
   assertThrows(AccessDeniedException.class,()->api.history(null,UUID.randomUUID()));
   verifyNoInteractions(db);
 }
 @Test void noCrossClinicEncounterLookup() {
   UUID clinic=UUID.randomUUID(),id=UUID.randomUUID();
   when(db.queryForList(contains("FROM visit WHERE clinic_id=? AND id=?"),eq(clinic),eq(id))).thenReturn(List.of());
   assertThrows(ResponseStatusException.class,()->api.get(as("DENTIST",clinic),id));
   verify(db).queryForList(contains("clinic_id=? AND id=?"),eq(clinic),eq(id));
 }
 @Test void transitionRequiresOptimisticVersion() {
   assertThrows(ResponseStatusException.class,()->api.transition(as("DENTIST",UUID.randomUUID()),
      UUID.randomUUID(),new EncounterController.TransitionInput("COMPLETED",null,null)));
   verifyNoInteractions(db);
 }
 @Test void cannotCancelWithoutReason() {
   assertThrows(ResponseStatusException.class,()->api.transition(as("OWNER",UUID.randomUUID()),
     UUID.randomUUID(),new EncounterController.TransitionInput("CANCELLED","",0L)));
   verifyNoInteractions(db);
 }
 @Test void rejectsBadLifecycleState() {
   assertThrows(ResponseStatusException.class,()->api.transition(as("OWNER",UUID.randomUUID()),
     UUID.randomUUID(),new EncounterController.TransitionInput("IN_PROGRESS","invalid",0L)));
   verifyNoInteractions(db);
 }
}
