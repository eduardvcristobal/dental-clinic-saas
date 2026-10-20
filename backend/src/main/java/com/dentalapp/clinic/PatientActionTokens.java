package com.dentalapp.clinic;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Opaque, high-entropy patient action token primitives.
 * Only SHA-256 hashes go to persistent storage; plaintext tokens should never be logged.
 */
@Component
public final class PatientActionTokens {
 private final SecureRandom random=new SecureRandom();
 public String generate(){
  byte[] bytes=new byte[32];
  random.nextBytes(bytes);
  return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
 }
 public byte[] hash(String token){
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid token");
  try{return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
 }
 public boolean matches(String token,byte[] storedHash){
  if(storedHash==null||storedHash.length!=32)return false;
  try{return MessageDigest.isEqual(hash(token),storedHash);}
  catch(IllegalArgumentException e){return false;}
 }
}
