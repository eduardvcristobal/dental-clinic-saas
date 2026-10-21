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
 private final NotificationWebhookReconciler reconciler;
 @org.springframework.beans.factory.annotation.Autowired
 public NotificationWebhookController(@Value("${app.notifications.resend-webhook-secret:}") String resendSecret,
 @Value("${app.notifications.twilio-auth-token:}") String twilioToken,
 @Value("${app.notifications.twilio-webhook-url:}") String twilioUrl, NotificationWebhookReconciler reconciler){
 this.resendSecret=resendSecret;this.twilioToken=twilioToken;this.twilioUrl=twilioUrl;this.reconciler=reconciler;
 }

 public NotificationWebhookController(
  @Value("${app.notifications.resend-webhook-secret:}") String resendSecret,
  @Value("${app.notifications.twilio-auth-token:}") String twilioToken,
  @Value("${app.notifications.twilio-webhook-url:}") String twilioUrl){
  this.resendSecret=resendSecret;this.twilioToken=twilioToken;this.twilioUrl=twilioUrl;this.reconciler=null;
 }
 @PostMapping("/resend")
 public Map<String,Object> resend(@RequestHeader(value="svix-id",required=false) String id,
  @RequestHeader(value="svix-timestamp",required=false) String timestamp,
  @RequestHeader(value="svix-signature",required=false) String signature,@RequestBody String raw){
  if(!ResendWebhookVerifier.verify(resendSecret,id,timestamp,signature,raw,Instant.now()))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
  // Delivery correlation/tenant resolution is intentionally not performed until a verified
  // event parser and replay-safe persistent event processor have been reviewed.
  boolean reconciled=false;
  try{
   var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
   String event=json.path("type").asText("");
   String mapped=switch(event){
    case "email.sent" -> "SENT";case "email.delivered" -> "DELIVERED";
    case "email.bounced" -> "BOUNCED";case "email.failed" -> "FAILED";
    case "email.complained" -> "COMPLAINED";default -> "";
   };
   String ref=json.path("data").path("email_id").asText("");
   if(reconciler!=null&&!mapped.isEmpty())reconciled=reconciler.record("resend",ref,id,mapped,Instant.now());
  }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid callback JSON");
  }
  if(reconciler!=null&&!reconciled&&raw.contains("\\\"type\\\"")){
   // Known delivery events that arrive before the provider reference is committed
   // must receive a retryable response, not a false-positive HTTP 200 acknowledgement.
   var retryEvent=new com.fasterxml.jackson.databind.ObjectMapper();
   try{
    String kind=retryEvent.readTree(raw).path("type").asText("");
    if(Set.of("email.sent","email.delivered","email.bounced","email.failed","email.complained").contains(kind))
     throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Event not yet correlated");
   }catch(com.fasterxml.jackson.core.JsonProcessingException ignored){}
  }
  return Map.of("authenticated",true,"reconciled",reconciled);
 }
 @PostMapping(path="/twilio",consumes="application/x-www-form-urlencoded")
 public Map<String,Object> twilio(@RequestHeader(value="X-Twilio-Signature",required=false) String signature,
     @RequestParam Map<String,String> form){
  if(!TwilioWebhookVerifier.verify(twilioToken,twilioUrl,form,signature))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
  String status=form.getOrDefault("MessageStatus","");
  String event=switch(status){
   case "delivered" -> "DELIVERED";case "sent" -> "SENT";
   case "failed","undelivered" -> "FAILED";case "queued","accepted" -> "ACCEPTED";default -> "";
  };
  String reference=form.getOrDefault("MessageSid","");
  boolean reconciled=reconciler!=null&&!event.isEmpty()&&
   reconciler.record("twilio",reference,reference+"_"+event,event,Instant.now());
  if(reconciler!=null&&!event.isEmpty()&&!reconciled)
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Event not yet correlated");
  return Map.of("authenticated",true,"reconciled",reconciled);
 }
}
