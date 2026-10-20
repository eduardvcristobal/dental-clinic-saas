package com.dentalapp.clinic;

import java.time.Instant;
import java.util.UUID;

/** M8.3–M8.4 transport contract. No production provider implementation or secrets in source. */
public interface NotificationTransport {
 enum Channel { EMAIL, SMS }
 record Delivery(UUID clinicId,UUID patientId,Channel channel,String destination,String message,UUID idempotencyKey){}
 record Outcome(boolean accepted,String providerReference,Instant acceptedAt){}
 Outcome deliver(Delivery delivery);
}
/** Fail-closed placeholder. Queue processing must never simulate a successful remote send. */
final class DisabledNotificationTransport implements NotificationTransport {
 @Override public Outcome deliver(Delivery delivery){
  throw new IllegalStateException("External notification transport is not configured; no message has been sent");
 }
}
