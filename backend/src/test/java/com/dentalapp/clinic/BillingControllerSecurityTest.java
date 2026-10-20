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
class BillingControllerSecurityTest {
 final JdbcTemplate db=mock(JdbcTemplate.class);
 final BillingController api=new BillingController(db);
 UsernamePasswordAuthenticationToken as(String role,UUID clinic){
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,role),null);
 }
 @Test void dentistCannotCollect(){var a=as("DENTIST",UUID.randomUUID());
  assertThrows(AccessDeniedException.class,()->api.patientInvoices(a,UUID.randomUUID()));
  verifyNoInteractions(db);
 }
 @Test void tenantBoundInvoiceRead(){UUID c=UUID.randomUUID(),id=UUID.randomUUID();
  when(db.queryForList(anyString(),eq(c),eq(id))).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->api.detail(as("FRONT_DESK",c),id));
  verify(db).queryForList(contains("clinic_id=? AND id=?"),eq(c),eq(id));
 }
 @Test void negativeLineRejected(){UUID c=UUID.randomUUID(),id=UUID.randomUUID();
  when(db.queryForList(anyString(),eq(c),eq(id))).thenReturn(List.of(Map.of("status","DRAFT","patient_id",UUID.randomUUID(),"plan_id",UUID.randomUUID())));
  var in=new BillingController.LineInput(null,null,"Synthetic",1,new BigDecimal("-1"),BigDecimal.ZERO);
  assertThrows(ResponseStatusException.class,()->api.addLine(as("FRONT_DESK",c),id,in));
 }
}
