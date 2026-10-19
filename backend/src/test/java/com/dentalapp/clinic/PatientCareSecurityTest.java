package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.patient.PatientCareController;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** M2.4 regression: prevent clinical disclosures and archived identity review. */
class PatientCareSecurityTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final PatientCareController api=new PatientCareController(db);
    private UsernamePasswordAuthenticationToken auth(UUID clinic,String role) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,role),null);
    }
    @Test void frontDeskCannotReadOrModifyClinicalHistory() {
        var staff=auth(UUID.randomUUID(),"FRONT_DESK");
        var id=UUID.randomUUID();
        assertThrows(AccessDeniedException.class,()->api.history(staff,id));
        assertThrows(AccessDeniedException.class,()->api.saveHistory(staff,id,new PatientCareController.HistoryInput("x","y","z")));
        verifyNoInteractions(db);
    }
    @Test void unscopedAndArchivedPatientsCannotBeOpened() {
        var clinic=UUID.randomUUID();var id=UUID.randomUUID();
        var staff=auth(clinic,"DENTIST");
        when(db.queryForObject(contains("deleted_at IS NULL"),eq(Integer.class),eq(clinic),eq(id))).thenReturn(0);
        assertThrows(ResponseStatusException.class,()->api.history(staff,id));
        verify(db).queryForObject(contains("clinic_id=? AND id=? AND deleted_at IS NULL"),eq(Integer.class),eq(clinic),eq(id));
    }
    @Test void unknownOrArchivedIdentityCannotBeReviewed() {
        var clinic=UUID.randomUUID();var id=UUID.randomUUID();
        var staff=auth(clinic,"FRONT_DESK");
        when(db.queryForObject(anyString(),eq(Integer.class),eq(clinic),eq(id))).thenReturn(1);
        when(db.queryForMap(contains("deleted_at IS NULL"),eq(clinic),eq(id))).thenReturn(Map.of("id",id,"chart_no","SYN-1","first_name","Test","last_name","Patient"));
        var result=api.identityReview(staff,id);
        assertEquals(false,result.get("reviewRequired"));
        verify(db).queryForMap(contains("deleted_at IS NULL"),eq(clinic),eq(id));
    }
    @Test void consentAcknowledgeRequiresExistingUnsignedConsent() {
        var clinic=UUID.randomUUID();var patient=UUID.randomUUID();var consent=UUID.randomUUID();
        var staff=auth(clinic,"ADMIN");
        when(db.queryForObject(anyString(),eq(Integer.class),eq(clinic),eq(patient))).thenReturn(1);
        when(db.update(contains("consent_status='UNSIGNED'"),any(),eq(consent),eq(patient),eq(clinic))).thenReturn(0);
        assertThrows(ResponseStatusException.class,()->api.acknowledge(staff,patient,consent));
        verify(db,never()).update(startsWith("INSERT INTO audit_log"),any(),any(),any());
    }
}
