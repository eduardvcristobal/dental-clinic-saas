package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationControllerSecurityTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final NotificationController controller=new NotificationController(db);
 private UsernamePasswordAuthenticationToken as(String role){
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
 }
 @Test void dentistCannotActivateExternalDelivery(){
  assertThrows(AccessDeniedException.class,()->controller.configure(as("DENTIST"),new NotificationController.PolicyInput(true,true,true,24)));
  verifyNoInteractions(db);
 }
 @Test void frontDeskCannotInventPatientConsent(){
  assertThrows(AccessDeniedException.class,()->controller.consent(as("FRONT_DESK"),new NotificationController.ConsentInput(UUID.randomUUID(),"EMAIL",true)));
  verifyNoInteractions(db);
 }
 @Test void missingAuthenticationRejected(){
  assertThrows(AccessDeniedException.class,()->controller.queue(null));
  verifyNoInteractions(db);
 }
 @Test void invalidChannelIsRejectedBeforeQueries(){
  assertThrows(ResponseStatusException.class,()->controller.schedule(as("FRONT_DESK"),UUID.randomUUID(),"SOCIAL"));
  verifyNoInteractions(db);
 }
 @Test void fakePatientResponseIsNotAccepted(){
  assertThrows(ResponseStatusException.class,()->controller.response(as("ADMIN"),UUID.randomUUID(),new NotificationController.ResponseInput("TREATMENT_COMPLETED")));
  verifyNoInteractions(db);
 }
 @Test void disabledProviderNeverReportsSuccess(){
  assertThrows(IllegalStateException.class,()->new DisabledNotificationTransport().deliver(
   new NotificationTransport.Delivery(UUID.randomUUID(),UUID.randomUUID(),NotificationTransport.Channel.EMAIL,
      "synthetic@example.invalid","Reminder",UUID.randomUUID())));
 }
}
