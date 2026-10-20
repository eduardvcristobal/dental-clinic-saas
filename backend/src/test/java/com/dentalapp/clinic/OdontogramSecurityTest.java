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
import static org.mockito.ArgumentMatchers.*;

class OdontogramSecurityTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final OdontogramController controller = new OdontogramController(db);
    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(
            new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
    }
    private OdontogramController.FindingInput input(int tooth,String surface,String kind,UUID supersedes) {
        return new OdontogramController.FindingInput(tooth,surface,"CARIES","EXISTING","synthetic",kind,supersedes);
    }
    @Test void frontDeskCannotReadClinicalChart() {
        assertThrows(AccessDeniedException.class,()->controller.chart(auth("FRONT_DESK"),UUID.randomUUID()));
        verifyNoInteractions(db);
    }
    @Test void frontDeskCannotWriteClinicalChart() {
        assertThrows(AccessDeniedException.class,()->controller.record(auth("FRONT_DESK"),UUID.randomUUID(),input(11,"I","OBSERVATION",null)));
        verifyNoInteractions(db);
    }
    @Test void chartLookupScopedToPrincipalClinic() {
        UUID clinic=UUID.randomUUID(),patient=UUID.randomUUID();
        var staff=new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,"DENTIST"),null);
        when(db.queryForObject(contains("clinic_id=? AND id=?"),eq(Boolean.class),eq(clinic),eq(patient))).thenReturn(false);
        assertThrows(ResponseStatusException.class,()->controller.chart(staff,patient));
        verify(db).queryForObject(contains("clinic_id=? AND id=?"),eq(Boolean.class),eq(clinic),eq(patient));
    }
    @Test void noAnonymousAccess() {
        assertThrows(AccessDeniedException.class,()->controller.chart(null,UUID.randomUUID()));
        verifyNoInteractions(db);
    }
    @Test void refusesInvalidToothAndSurfaceBeforeWriting() {
        UUID clinic=UUID.randomUUID(),patient=UUID.randomUUID();
        var staff=new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,"DENTIST"),null);
        when(db.queryForObject(anyString(),eq(Boolean.class),eq(clinic),eq(patient))).thenReturn(true);
        assertThrows(ResponseStatusException.class,()->controller.record(staff,patient,input(49,"O","OBSERVATION",null)));
        assertThrows(ResponseStatusException.class,()->controller.record(staff,patient,input(11,"O","OBSERVATION",null)));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void correctionRequiresOriginalEventId() {
        UUID clinic=UUID.randomUUID(),patient=UUID.randomUUID();
        var staff=new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,"DENTIST"),null);
        when(db.queryForObject(anyString(),eq(Boolean.class),eq(clinic),eq(patient))).thenReturn(true);
        assertThrows(ResponseStatusException.class,()->controller.record(staff,patient,input(11,"I","CORRECTION",null)));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
}
