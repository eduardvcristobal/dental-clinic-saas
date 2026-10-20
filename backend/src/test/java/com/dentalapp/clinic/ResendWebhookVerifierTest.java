package com.dentalapp.clinic;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResendWebhookVerifierTest {
 @Test void rejectsTamperingAndReplaysOutsideClockWindow() throws Exception {
  Instant now=Instant.parse("2026-10-21T05:00:00Z");
  String id="evt_synthetic_123",stamp=Long.toString(now.getEpochSecond()),payload="{\"type\":\"email.delivered\"}";
  byte[] secret="synthetic-secret-32-bytes-not-live".getBytes(StandardCharsets.UTF_8);
  String key="whsec_"+Base64.getEncoder().encodeToString(secret);
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
  String signature="v1,"+Base64.getEncoder().encodeToString(mac.doFinal((id+"."+stamp+"."+payload).getBytes(StandardCharsets.UTF_8)));
  assertTrue(ResendWebhookVerifier.verify(key,id,stamp,signature,payload,now));
  assertFalse(ResendWebhookVerifier.verify(key,id,stamp,signature,payload+" ",now));
  assertFalse(ResendWebhookVerifier.verify(key,id,stamp,signature,payload,now.plusSeconds(301)));
  assertFalse(ResendWebhookVerifier.verify("",id,stamp,signature,payload,now));
  assertFalse(ResendWebhookVerifier.verify(key,id,stamp,"v1,incorrect",payload,now));
 }
}
