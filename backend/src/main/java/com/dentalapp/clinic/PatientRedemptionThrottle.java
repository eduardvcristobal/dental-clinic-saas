package com.dentalapp.clinic;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Fail-closed, bounded per-tenant process-local limiter for unauthenticated redemptions. */
final class PatientRedemptionThrottle {
 private PatientRedemptionThrottle(){}
 private static final int MAX_BUCKETS=10000;
 private static final int MAX_PER_MINUTE=30;
 private record Bucket(long minute,AtomicInteger attempts){}
 private static final ConcurrentHashMap<UUID,Bucket> attempts=new ConcurrentHashMap<>();
 static boolean allow(UUID clinic,Instant now){
  if(clinic==null||now==null)return false;
  long minute=now.getEpochSecond()/60;
  if(attempts.size()>MAX_BUCKETS){
   attempts.entrySet().removeIf(e->e.getValue().minute()!=minute);
   if(attempts.size()>MAX_BUCKETS)return false;
  }
  Bucket bucket=attempts.compute(clinic,(id,previous)->
    previous==null||previous.minute()!=minute?
      new Bucket(minute,new AtomicInteger()):previous);
  return bucket.attempts().incrementAndGet()<=MAX_PER_MINUTE;
 }
}
