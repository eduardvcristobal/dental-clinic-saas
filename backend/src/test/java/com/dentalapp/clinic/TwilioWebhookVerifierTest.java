package com.dentalapp.clinic;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwilioWebhookVerifierTest {
 @Test void onlyAuthenticExactUrlAndAllFormParametersAreAccepted() throws Exception {
  String secret="synthetic-twilio-token",url="https://example.invalid/api/callbacks/twilio";
  Map<String,String> form=Map.of("MessageSid","SM12345","MessageStatus","delivered");
  Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
  String signature=Base64.getEncoder().encodeToString(mac.doFinal(
   (url+"MessageSidSM12345MessageStatusdelivered").getBytes(StandardCharsets.UTF_8)));
  assertTrue(TwilioWebhookVerifier.verify(secret,url,form,signature));
  assertFalse(TwilioWebhookVerifier.verify(secret,url+"?x=1",form,signature));
  assertFalse(TwilioWebhookVerifier.verify(secret,url,Map.of("MessageSid","SM12345"),signature));
  assertFalse(TwilioWebhookVerifier.verify("wrong",url,form,signature));
  assertFalse(TwilioWebhookVerifier.verify(secret,"http://example.invalid/api/callbacks/twilio",form,signature));
 }
}
