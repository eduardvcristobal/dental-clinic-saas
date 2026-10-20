package com.dentalapp.clinic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Twilio x-www-form-urlencoded signature verifier; caller supplies exact configured HTTPS URL. */
public final class TwilioWebhookVerifier {
 private TwilioWebhookVerifier(){}
 public static boolean verify(String authToken,String exactUrl,Map<String,String> params,String signature){
  if(authToken==null||authToken.isBlank()||exactUrl==null||!exactUrl.startsWith("https://")
    ||params==null||signature==null||signature.isBlank())return false;
  try{
   StringBuilder value=new StringBuilder(exactUrl);
   params.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->value.append(e.getKey()).append(e.getValue()));
   Mac hmac=Mac.getInstance("HmacSHA1");hmac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
   byte[] expected=hmac.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
   return MessageDigest.isEqual(expected,Base64.getDecoder().decode(signature));
  }catch(RuntimeException|java.security.GeneralSecurityException invalid){return false;}
 }
}
