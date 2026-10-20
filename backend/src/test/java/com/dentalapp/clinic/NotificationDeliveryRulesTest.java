package com.dentalapp.clinic;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryRulesTest {
 @Test void smsMustBeE164(){
  assertTrue(NotificationDeliveryRules.validSmsDestination("+639171234567"));
  assertFalse(NotificationDeliveryRules.validSmsDestination("09171234567"));
  assertFalse(NotificationDeliveryRules.validSmsDestination("+01234"));
  assertFalse(NotificationDeliveryRules.validSmsDestination("+1234567890123456"));
  assertFalse(NotificationDeliveryRules.validSmsDestination(null));
 }
 @Test void validatesEmailWithoutGuessingDestination(){
  assertTrue(NotificationDeliveryRules.validEmailDestination("patient@example.org"));
  assertFalse(NotificationDeliveryRules.validEmailDestination("patient@example"));
  assertFalse(NotificationDeliveryRules.validEmailDestination(null));
 }
 @Test void overnightQuietWindowIsTimezoneAware(){
  ZoneId manila=ZoneId.of("Asia/Manila");
  Instant during=ZonedDateTime.of(2026,10,21,22,30,0,0,manila).toInstant();
  Instant expected=ZonedDateTime.of(2026,10,22,8,0,0,0,manila).toInstant();
  assertEquals(expected,NotificationDeliveryRules.afterQuietHours(during,manila,LocalTime.of(20,0),LocalTime.of(8,0)));
  assertEquals(expected,NotificationDeliveryRules.afterQuietHours(expected,manila,LocalTime.of(20,0),LocalTime.of(8,0)));
 }
 @Test void dayQuietWindowAndDstTransition(){
  var ny=ZoneId.of("America/New_York");
  Instant during=ZonedDateTime.of(2026,3,8,1,30,0,0,ny).toInstant();
  Instant expected=ZonedDateTime.of(2026,3,8,4,0,0,0,ny).toInstant();
  assertEquals(expected,NotificationDeliveryRules.afterQuietHours(during,ny,LocalTime.of(0,0),LocalTime.of(4,0)));
 }
 @Test void backoffIsBounded(){
  assertEquals(Duration.ofSeconds(30),NotificationDeliveryRules.retryDelay(1));
  assertEquals(Duration.ofSeconds(60),NotificationDeliveryRules.retryDelay(2));
  assertEquals(Duration.ofHours(1),NotificationDeliveryRules.retryDelay(10));
  assertThrows(IllegalArgumentException.class,()->NotificationDeliveryRules.retryDelay(0));
 }
 @Test void disabledTransportFailsClosed(){
  var adapter=new ProviderNotificationTransport(java.net.http.HttpClient.newHttpClient(),false,"","","","","");
  assertThrows(IllegalStateException.class,()->adapter.deliver(
    new NotificationTransport.Delivery(UUID.randomUUID(),UUID.randomUUID(),NotificationTransport.Channel.EMAIL,"patient@example.org","Reminder",UUID.randomUUID())));
 }
}
