package com.dentalapp.clinic;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NotificationWebhookControllerTest {
 @Test void rejectsUnsignedAndUnconfiguredCallbacks(){
  var controller=new NotificationWebhookController("","","");
  assertThrows(ResponseStatusException.class,()->controller.resend(null,null,null,"{}"));
  assertThrows(ResponseStatusException.class,()->controller.twilio(null,Map.of()));
 }
 @Test void rejectsFakeSignaturesWithoutProviderSideEffects(){
  var controller=new NotificationWebhookController("whsec_c3ludGhldGlj","synthetic-token","https://example.invalid/hooks");
  assertThrows(ResponseStatusException.class,()->controller.resend("evt","100","v1,fake","{}"));
  assertThrows(ResponseStatusException.class,()->controller.twilio("fake",Map.of("MessageSid","SM123")));
 }
}
