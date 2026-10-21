package com.dentalapp.clinic;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportsControllerSecurityTest {
 @Test void anonymousReportIsDeniedBeforeAnyQuery(){
  var db=mock(JdbcTemplate.class);
  var controller=new ReportsController(db);
  assertThrows(AccessDeniedException.class,()->controller.summary(null,"2026-10-01","2026-10-31"));
  verifyNoInteractions(db);
 }
 @Test void invalidOrExcessiveRangeIsRejectedBeforeQueryForOwner(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  var auth=mock(org.springframework.security.core.Authentication.class);
  when(auth.getPrincipal()).thenReturn(new com.dentalapp.auth.AuthPrincipal(
   java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"OWNER"));
  var controller=new ReportsController(db);
  assertThrows(ResponseStatusException.class,()->controller.summary(auth,"2026-10-31","2026-10-01"));
  assertThrows(ResponseStatusException.class,()->controller.summary(auth,"2020-01-01","2026-10-31"));
  assertThrows(ResponseStatusException.class,()->controller.summary(auth,"not-a-date","2026-10-31"));
  verifyNoInteractions(db);
 }
 @Test void frontDeskCannotReadClinicAnalytics(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  var auth=mock(org.springframework.security.core.Authentication.class);
  when(auth.getPrincipal()).thenReturn(new com.dentalapp.auth.AuthPrincipal(
   java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"FRONT_DESK"));
  assertThrows(AccessDeniedException.class,()->new ReportsController(db).summary(auth,"2026-10-01","2026-10-31"));
  verifyNoInteractions(db);
 }
 @Test void invalidDatesAreRejectedAfterAuthorizationOnly(){
  var db=mock(JdbcTemplate.class);
  var controller=new ReportsController(db);
  assertThrows(AccessDeniedException.class,()->controller.summary(null,"invalid","2026-10-31"));
  verifyNoInteractions(db);
 }
}
