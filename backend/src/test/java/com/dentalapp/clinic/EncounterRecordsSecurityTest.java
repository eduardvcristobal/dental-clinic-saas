package com.dentalapp.clinic;
import com.dentalapp.auth.AuthPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncounterRecordsSecurityTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final EncounterRecordsController api=new EncounterRecordsController(db);
 private UsernamePasswordAuthenticationToken as(String role){
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
 }
 @Test void frontDeskCannotReadPrescriptionDrafts(){
  assertThrows(AccessDeniedException.class,()->api.prescriptions(as("FRONT_DESK"),UUID.randomUUID()));
  verifyNoInteractions(db);
 }
 @Test void noAnonymousDocuments(){
  assertThrows(AccessDeniedException.class,()->api.documents(null,UUID.randomUUID()));
  verifyNoInteractions(db);
 }
 @Test void noArbitraryDocumentUrls(){
  assertThrows(ResponseStatusException.class,()->api.document(as("DENTIST"),UUID.randomUUID(),
   new EncounterRecordsController.DocumentInput("x.pdf","https://attacker.example/path","application/pdf")));
  verifyNoInteractions(db);
 }
 @Test void rejectIncompletePrescriptionDraft(){
  assertThrows(ResponseStatusException.class,()->api.prescription(as("DENTIST"),UUID.randomUUID(),
    new EncounterRecordsController.PrescriptionInput("","5mg","daily")));
  verifyNoInteractions(db);
 }
}
