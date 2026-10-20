package com.dentalapp.clinic;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatientRedemptionThrottleTest {
 @Test void rateLimitIsBoundedAndResetsAtNextMinute(){
  UUID clinic=UUID.randomUUID();
  Instant first=Instant.parse("2026-10-21T00:00:00Z");
  for(int i=0;i<30;i++)assertTrue(PatientRedemptionThrottle.allow(clinic,first));
  assertFalse(PatientRedemptionThrottle.allow(clinic,first));
  assertTrue(PatientRedemptionThrottle.allow(clinic,first.plusSeconds(60)));
  assertFalse(PatientRedemptionThrottle.allow(null,first));
 }
 @Test void independentClinicsHaveIndependentBudgets(){
  Instant now=Instant.parse("2026-10-21T00:00:00Z");
  UUID first=UUID.randomUUID(),second=UUID.randomUUID();
  for(int i=0;i<31;i++)PatientRedemptionThrottle.allow(first,now);
  assertTrue(PatientRedemptionThrottle.allow(second,now));
 }
}
