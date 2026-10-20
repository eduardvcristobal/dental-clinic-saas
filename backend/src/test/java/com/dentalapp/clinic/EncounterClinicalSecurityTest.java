package com.dentalapp.clinic;
import com.dentalapp.auth.AuthPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EncounterClinicalSecurityTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final EncounterClinicalController api=new EncounterClinicalController(db);
 private UsernamePasswordAuthenticationToken auth(String role) {
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
 }
 @Test void frontDeskCannotReadClinicalNotes() {
  assertThrows(AccessDeniedException.class,()->api.soap(auth("FRONT_DESK"),UUID.randomUUID()));
  verifyNoInteractions(db);
 }
 @Test void frontDeskCannotWriteClinicalNotes() {
  assertThrows(AccessDeniedException.class,()->api.save(auth("FRONT_DESK"),UUID.randomUUID(),
   new EncounterClinicalController.SoapInput("s","o","a","p",0L)));
  verifyNoInteractions(db);
 }
 @Test void anonymousCannotReadDiagnoses() {
  assertThrows(AccessDeniedException.class,()->api.diagnoses(null,UUID.randomUUID()));
  verifyNoInteractions(db);
 }
 @Test void rejectsInvalidDiagnosisBeforeSQL() {
  assertThrows(ResponseStatusException.class,()->api.addDiagnosis(auth("DENTIST"),UUID.randomUUID(),
   new EncounterClinicalController.DiagnosisInput("MADEUP","123","Synthetic")));
  verifyNoInteractions(db);
 }
 @Test void rejectsMissingOptimisticVersion() {
  assertThrows(ResponseStatusException.class,()->api.save(auth("DENTIST"),UUID.randomUUID(),
   new EncounterClinicalController.SoapInput("s","o","a","p",null)));
  verifyNoInteractions(db);
 }
 @Test void rejectsOverlongDraftBeforeQuerying() {
  assertThrows(ResponseStatusException.class,()->api.save(auth("DENTIST"),UUID.randomUUID(),
   new EncounterClinicalController.SoapInput("x".repeat(20001),"","","",0L)));
  verifyNoInteractions(db);
 }
 @Test void missingEncounterDoesNotLeakOtherClinicData() {
  UUID id=UUID.randomUUID();
  when(db.queryForList(contains("FROM visit WHERE clinic_id=? AND id=?"),any(),eq(id))).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->api.soap(auth("DENTIST"),id));
  verify(db).queryForList(contains("clinic_id=? AND id=?"),any(),eq(id));
 }
}
