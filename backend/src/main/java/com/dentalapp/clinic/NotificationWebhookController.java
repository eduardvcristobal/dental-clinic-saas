package com.dentalapp.clinic;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Provider-authenticated callback ingress. No unauthenticated event may update patient data. */
@RestController
@RequestMapping("/api/notification-webhooks")
public final class NotificationWebhookController {
 private final String resendSecret,twilioToken,twilioUrl;
 public NotificationWebhookController(
  @Value("${app.notifications.resend-webhook-secret:}") String resendSecret,
  @Value("${app.notifications.twilio-auth-token:}") String twilioToken,
  @Value("${app.notifications.twilio-webhook-url:}") String twilioUrl){
  this.resendSecret=resendSecret;this.twilioToken=twilioToken;this.twilioUrl=twilioUrl;
 }
 @PostMapping("/resend")
 public Map<String,Object> resend(@RequestHeader(value="svix-id",required=false) String id,
  @RequestHeader(value="svix-timestamp",required=false) String timestamp,
  @RequestHeader(value="svix-signature",required=false) String signature,@RequestBody String raw){
  if(!ResendWebhookVerifier.verify(resendSecret,id,timestamp,signature,raw,Instant.now()))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
  // Delivery correlation/tenant resolution is intentionally not performed until a verified
  // event parser and replay-safe persistent event processor have been reviewed.
  return Map.of("authenticated",true,"reconciled",false);
 }
 @PostMapping(path="/twilio",consumes="application/x-www-form-urlencoded")
 public Map<String,Object> twilio(@RequestHeader(value="X-Twilio-Signature",required=false) String signature,
     @RequestParam Map<String,String> form){
  if(!TwilioWebhookVerifier.verify(twilioToken,twilioUrl,form,signature))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
  return Map.of("authenticated",true,"reconciled",false);
 }
}
