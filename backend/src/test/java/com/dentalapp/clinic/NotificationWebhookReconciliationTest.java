package com.dentalapp.clinic;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationWebhookReconciliationTest {
 @Test void verifiedResendEventRoutesToReconciler() throws Exception {
  String key="synthetic-webhook-secret-key-32bytes";
  String secret="whsec_"+Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
  var reconciler=mock(NotificationWebhookReconciler.class);
  var controller=new NotificationWebhookController(secret,"","",reconciler);
  String id="evt_valid_test",ts=Long.toString(Instant.now().getEpochSecond());
  String raw="{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"email_12345678\"}}";
  Mac h=Mac.getInstance("HmacSHA256");h.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  String sig="v1,"+Base64.getEncoder().encodeToString(h.doFinal((id+"."+ts+"."+raw).getBytes(StandardCharsets.UTF_8)));
  when(reconciler.record(eq("resend"),eq("email_12345678"),eq(id),eq("DELIVERED"),any(Instant.class))).thenReturn(true);
  controller.resend(id,ts,sig,raw);
  verify(reconciler).record(eq("resend"),eq("email_12345678"),eq(id),eq("DELIVERED"),any(Instant.class));
  when(reconciler.record(eq("resend"),eq("email_12345678"),eq(id),eq("DELIVERED"),any(Instant.class))).thenReturn(false);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.resend(id,ts,sig,raw));

 }
 @Test void invalidEventsDoNotInvokeReconciler(){
  var reconciler=mock(NotificationWebhookReconciler.class);
  var controller=new NotificationWebhookController("","","",reconciler);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,
    ()->controller.resend("evt","123","fake","{}"));
  verifyNoInteractions(reconciler);
 }
}
