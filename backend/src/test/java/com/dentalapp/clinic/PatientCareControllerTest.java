package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.patient.PatientCareController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatientCareControllerTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final PatientCareController api=new PatientCareController(db);
 private UsernamePasswordAuthenticationToken auth(String role) {
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
 }
 @Test void unauthenticatedCannotReadHistory() {
  assertThrows(AccessDeniedException.class,()->api.history(null,UUID.randomUUID()));
  verifyNoInteractions(db);
 }
 @Test void receptionistCannotReadOrChangeClinicalHistory() {
  var staff=auth("FRONT_DESK");
  assertThrows(AccessDeniedException.class,()->api.history(staff,UUID.randomUUID()));
  assertThrows(AccessDeniedException.class,()->api.saveHistory(staff,UUID.randomUUID(),new PatientCareController.HistoryInput("none","none","none")));
  verifyNoInteractions(db);
 }
 @Test void historyLookupsRequireAuthenticatedClinic() {
  var user=auth("DENTIST");var clinic=((AuthPrincipal)user.getPrincipal()).clinicId();
  when(db.queryForObject(contains("patient WHERE clinic_id=?"),eq(Integer.class),eq(clinic),any(UUID.class))).thenReturn(0);
  assertThrows(ResponseStatusException.class,()->api.history(user,UUID.randomUUID()));
  verify(db).queryForObject(contains("clinic_id=?"),eq(Integer.class),eq(clinic),any(UUID.class));
 }
 @Test void invalidGuardianMustNotWrite() {
  var user=auth("OWNER");var clinic=((AuthPrincipal)user.getPrincipal()).clinicId();var patient=UUID.randomUUID();
  when(db.queryForObject(contains("patient WHERE clinic_id=?"),eq(Integer.class),eq(clinic),eq(patient))).thenReturn(1);
  assertThrows(ResponseStatusException.class,()->api.addGuardian(user,patient,new PatientCareController.GuardianInput("  ","Parent",null,null,false)));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
