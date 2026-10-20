package com.dentalapp.clinic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Resend/Svix signed raw-body verification. Reject malformed, stale and unsigned events. */
public final class ResendWebhookVerifier {
 private ResendWebhookVerifier(){}
 public static boolean verify(String secret,String id,String timestamp,String signatures,String rawBody,Instant now){
  if(secret==null||!secret.startsWith("whsec_")||id==null||id.isBlank()||id.length()>200
      ||timestamp==null||signatures==null||rawBody==null||rawBody.length()>65536)return false;
  try{
   long epoch=Long.parseLong(timestamp);
   if(Math.abs(now.getEpochSecond()-epoch)>300)return false;
   byte[] key=Base64.getDecoder().decode(secret.substring(6));
   Mac mac=Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(key,"HmacSHA256"));
   byte[] expected=mac.doFinal((id+"."+timestamp+"."+rawBody).getBytes(StandardCharsets.UTF_8));
   for(String signed:signatures.split(" ")){
    if(!signed.startsWith("v1,"))continue;
    byte[] actual=Base64.getDecoder().decode(signed.substring(3));
    if(MessageDigest.isEqual(expected,actual))return true;
   }
  }catch(Exception invalid){return false;}
  return false;
 }
}
