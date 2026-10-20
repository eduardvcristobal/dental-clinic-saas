package com.dentalapp.clinic;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientActionControllerSecurityTest {
 @Test void disabledPatientActionsDoNotQueryOrPersist(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  PlatformTransactionManager tm=mock(PlatformTransactionManager.class);
  PatientActionController c=new PatientActionController(db,tm,new PatientActionTokens(),false);
  assertThrows(ResponseStatusException.class,()->c.redeem(
    new PatientActionController.RedeemInput(UUID.randomUUID(),new PatientActionTokens().generate())));
  verifyNoInteractions(db,tm);
 }
 @Test void unauthorizedIssuanceIsRejected(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  PlatformTransactionManager tm=mock(PlatformTransactionManager.class);
  PatientActionController c=new PatientActionController(db,tm,new PatientActionTokens(),true);
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->c.issue(null,
    new PatientActionController.IssueInput(UUID.randomUUID(),"CONFIRM")));
  verifyNoInteractions(db,tm);
 }
}
