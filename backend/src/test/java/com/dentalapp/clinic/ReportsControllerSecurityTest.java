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
 @Test void invalidDatesAreRejectedAfterAuthorizationOnly(){
  var db=mock(JdbcTemplate.class);
  var controller=new ReportsController(db);
  assertThrows(AccessDeniedException.class,()->controller.summary(null,"invalid","2026-10-31"));
  verifyNoInteractions(db);
 }
}
