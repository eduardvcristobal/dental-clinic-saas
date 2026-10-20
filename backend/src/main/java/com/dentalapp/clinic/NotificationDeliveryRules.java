package com.dentalapp.clinic;

import java.time.*;
import java.util.regex.Pattern;

/** PHI-minimal notification delivery validation, independent of external providers. */
public final class NotificationDeliveryRules {
 private static final Pattern E164=Pattern.compile("^\\+[1-9][0-9]{1,14}$");
 private NotificationDeliveryRules(){}
 public static boolean validSmsDestination(String number){return number!=null&&E164.matcher(number).matches();}
 public static boolean validEmailDestination(String email){
  return email!=null&&email.length()<=254&&email.matches("^[A-Za-z0-9.!#$%&'*+/=?^_\u0060{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
 }
 /** Returns first permissible dispatch instant; quiet interval may cross midnight. */
 public static Instant afterQuietHours(Instant instant,ZoneId zone,LocalTime start,LocalTime end){
  if(start.equals(end))return instant;
  ZonedDateTime local=instant.atZone(zone);
  LocalTime now=local.toLocalTime();
  boolean quiet=start.isBefore(end)?(!now.isBefore(start)&&now.isBefore(end)):
      (!now.isBefore(start)||now.isBefore(end));
  if(!quiet)return instant;
  LocalDate endDate=local.toLocalDate();
  if(start.isAfter(end)&&!now.isBefore(start))endDate=endDate.plusDays(1);
  return ZonedDateTime.of(endDate,end,zone).toInstant();
 }
 public static Duration retryDelay(int attempts){
  if(attempts<1)throw new IllegalArgumentException("Attempt count must be positive");
  return Duration.ofSeconds(Math.min(3600L,30L << Math.min(7,attempts-1)));
 }
}
