package com.dentalapp.clinic;
import com.dentalapp.auth.AuthPrincipal;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TreatmentPlanSecurityTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final TreatmentPlanController api=new TreatmentPlanController(db);
 private UsernamePasswordAuthenticationToken as(String role,UUID clinic){
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,role),null);
 }
 @Test void frontDeskDenied(){var auth=as("FRONT_DESK",UUID.randomUUID());
  assertThrows(AccessDeniedException.class,()->api.catalog(auth));
  assertThrows(AccessDeniedException.class,()->api.create(auth,new TreatmentPlanController.PlanInput(UUID.randomUUID(),UUID.randomUUID(),"Test")));
  verifyNoInteractions(db);
 }
 @Test void anonymousDenied(){assertThrows(AccessDeniedException.class,()->api.detail(null,UUID.randomUUID()));verifyNoInteractions(db);}
 @Test void planLookupScopedToClinic(){
  UUID c=UUID.randomUUID(),id=UUID.randomUUID();
  when(db.queryForList(anyString(),eq(c),eq(id))).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->api.detail(as("DENTIST",c),id));
  verify(db).queryForList(contains("clinic_id=? AND id=?"),eq(c),eq(id));
 }
 @Test void rejectsNegativePriceBeforeDatabaseWrite(){
  UUID c=UUID.randomUUID(),id=UUID.randomUUID(),patient=UUID.randomUUID();
  when(db.queryForList(anyString(),eq(c),eq(id))).thenReturn(List.of(Map.of("status","DRAFT","patient_id",patient)));
  var input=new TreatmentPlanController.ItemInput(UUID.randomUUID(),11,null,1,1,new BigDecimal("-1.00"),BigDecimal.ZERO);
  assertThrows(ResponseStatusException.class,()->api.addItem(as("DENTIST",c),id,input));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void rejectsMissingConsentVersion(){
  assertThrows(ResponseStatusException.class,()->api.transition(as("DENTIST",UUID.randomUUID()),
   UUID.randomUUID(),new TreatmentPlanController.TransitionInput("ACCEPTED",null,null)));
  verifyNoInteractions(db);
 }
}
