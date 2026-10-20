package com.dentalapp.clinic;
import com.dentalapp.auth.AuthPrincipal;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BillingFinancialClosureSecurityTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final BillingRefundController refunds=new BillingRefundController(db);
 private final BillingReconciliationController recon=new BillingReconciliationController(db);
 private final FiscalReadinessController fiscal=new FiscalReadinessController(db);
 private UsernamePasswordAuthenticationToken as(String role){
  return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
 }
 @Test void frontDeskCannotDisburseRefund(){
  assertThrows(AccessDeniedException.class,()->refunds.refund(as("FRONT_DESK"),UUID.randomUUID(),
   UUID.randomUUID(),new BillingRefundController.RefundInput(new BigDecimal("5"),"CASH","MANUAL-123","Correction",UUID.randomUUID())));
  verifyNoInteractions(db);
 }
 @Test void clinicianCannotSeeFinancialAging(){
  assertThrows(AccessDeniedException.class,()->recon.reconcile(as("DENTIST"),UUID.randomUUID(),java.time.LocalDate.now()));
  verifyNoInteractions(db);
 }
 @Test void clinicalStaffCannotConfigureFiscalDocuments(){
  assertThrows(AccessDeniedException.class,()->fiscal.status(as("DENTIST")));
  verifyNoInteractions(db);
 }
 @Test void invalidRefundRejectedWithoutWrite(){
  assertThrows(ResponseStatusException.class,()->refunds.refund(as("ADMIN"),UUID.randomUUID(),
    UUID.randomUUID(),new BillingRefundController.RefundInput(BigDecimal.ZERO,"CASH","MANUAL-123","Correction",UUID.randomUUID())));
  verifyNoInteractions(db);
 }
 @Test void invalidFiscalProfileRejectedWithoutWrite(){
  assertThrows(ResponseStatusException.class,()->fiscal.configure(as("OWNER"),
   new FiscalReadinessController.Profile("PH","A","Missing", "X","VAT",null)));
  verifyNoInteractions(db);
 }
}
