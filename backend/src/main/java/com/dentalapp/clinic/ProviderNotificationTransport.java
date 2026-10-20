package com.dentalapp.clinic;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opt-in outbound integration. Does not send unless explicitly enabled and configured.
 * Email: Resend /emails API; SMS: Twilio Messages API. Neither accepted status nor
 * HTTP 2xx proves delivery to the patient; verified provider callbacks must reconcile it.
 */
@Component
public final class ProviderNotificationTransport implements NotificationTransport {
 private final HttpClient http;
 private final boolean enabled;
 private final String resendKey,fromEmail,twilioSid,twilioToken,fromPhone;
 public ProviderNotificationTransport(
  @Value("${app.notifications.outbound-enabled:false}") boolean enabled,
  @Value("${app.notifications.resend-api-key:}") String resendKey,
  @Value("${app.notifications.from-email:}") String fromEmail,
  @Value("${app.notifications.twilio-account-sid:}") String twilioSid,
  @Value("${app.notifications.twilio-auth-token:}") String twilioToken,
  @Value("${app.notifications.from-phone:}") String fromPhone) {
  this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),enabled,resendKey,fromEmail,twilioSid,twilioToken,fromPhone);
 }
 ProviderNotificationTransport(HttpClient http,boolean enabled,String resendKey,String fromEmail,String twilioSid,String twilioToken,String fromPhone){
  this.http=http;this.enabled=enabled;this.resendKey=resendKey;this.fromEmail=fromEmail;
  this.twilioSid=twilioSid;this.twilioToken=twilioToken;this.fromPhone=fromPhone;
 }
 @Override public Outcome deliver(Delivery delivery){
  if(!enabled)throw new IllegalStateException("Outbound delivery disabled");
  if(delivery==null||delivery.idempotencyKey()==null||delivery.message()==null||delivery.message().isBlank()
    ||delivery.message().length()>1000)throw new IllegalArgumentException("Invalid notification delivery");
  try{
   return delivery.channel()==Channel.EMAIL?email(delivery):sms(delivery);
  }catch(InterruptedException ex){
   Thread.currentThread().interrupt();
   throw new IllegalStateException("Provider request interrupted",ex);
  }catch(java.io.IOException ex){
   throw new IllegalStateException("Provider request failed",ex);
  }
 }
 private Outcome email(Delivery d) throws java.io.IOException,InterruptedException{
  if(resendKey.isBlank()||!NotificationDeliveryRules.validEmailDestination(fromEmail)||
     !NotificationDeliveryRules.validEmailDestination(d.destination()))
   throw new IllegalStateException("Email provider credentials or destination invalid");
  String payload="{\"from\":\""+json(fromEmail)+"\",\"to\":[\""+json(d.destination())+"\"],\"subject\":\"Appointment reminder\",\"text\":\""+json(d.message())+"\"}";
  HttpRequest req=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
    .timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+resendKey)
    .header("Content-Type","application/json").header("Idempotency-Key",d.idempotencyKey().toString())
    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
  return send(req,"resend");
 }
 private Outcome sms(Delivery d) throws java.io.IOException,InterruptedException{
  if(twilioSid.isBlank()||twilioToken.isBlank()||!NotificationDeliveryRules.validSmsDestination(fromPhone)
     ||!NotificationDeliveryRules.validSmsDestination(d.destination()))
   throw new IllegalStateException("SMS provider credentials or destination invalid");
  String body="To="+form(d.destination())+"&From="+form(fromPhone)+"&Body="+form(d.message());
  HttpRequest req=HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/"+twilioSid+"/Messages.json"))
    .timeout(Duration.ofSeconds(12))
    .header("Authorization","Basic "+Base64.getEncoder().encodeToString((twilioSid+":"+twilioToken).getBytes(StandardCharsets.UTF_8)))
    .header("Content-Type","application/x-www-form-urlencoded")
    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
  return send(req,"twilio");
 }
 private Outcome send(HttpRequest req,String provider) throws java.io.IOException,InterruptedException{
  HttpResponse<String> result=http.send(req,HttpResponse.BodyHandlers.ofString());
  if(result.statusCode()<200||result.statusCode()>=300)
   throw new IllegalStateException(provider+" rejected notification (HTTP "+result.statusCode()+")");
  // Do not falsely mark a message delivered: this acknowledges provider acceptance only.
  String ref=extractId(result.body());
  if(ref==null||ref.isBlank())throw new IllegalStateException(provider+" did not provide a reference");
  return new Outcome(true,ref,Instant.now());
 }
 private static String extractId(String response){
  // Minimal extraction for the simple provider success envelopes; never parse arbitrary message content as a ref.
  var matcher=java.util.regex.Pattern.compile("\"(?:id|sid)\"\\s*:\\s*\"([A-Za-z0-9_-]{8,128})\"").matcher(response);
  return matcher.find()?matcher.group(1):null;
 }
 private static String json(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
 private static String form(String s){return java.net.URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
