package com.dentalapp.clinic;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatientActionTokensTest {
 @Test void tokensAreRandomOpaqueAndHashesCanBeCheckedWithoutPersistingPlaintext(){
  var tokens=new PatientActionTokens();
  String a=tokens.generate(),b=tokens.generate();
  assertEquals(43,a.length());
  assertNotEquals(a,b);
  assertEquals(32,tokens.hash(a).length);
  assertFalse(Arrays.equals(tokens.hash(a),tokens.hash(b)));
  assertTrue(tokens.matches(a,tokens.hash(a)));
  assertFalse(tokens.matches(b,tokens.hash(a)));
  assertFalse(tokens.matches("invalid",tokens.hash(a)));
  assertFalse(tokens.matches(a,new byte[12]));
 }
}
