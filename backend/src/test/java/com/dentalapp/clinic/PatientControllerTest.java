package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.patient.PatientController;
import com.dentalapp.service.NumberingService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatientControllerTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final NumberingService numbers=mock(NumberingService.class);
    private final PatientController api=new PatientController(db,numbers);
    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
    }
    @Test void unauthenticatedRequestsAreDenied() {
        assertThrows(AccessDeniedException.class,()->api.search(null,"",0,25));
    }
    @Test void boundedSearchRejectsInvalidArguments() {
        var a=auth("FRONT_DESK");
        assertThrows(ResponseStatusException.class,()->api.search(a,"",0,101));
        assertThrows(ResponseStatusException.class,()->api.search(a,"",-1,25));
        verifyNoInteractions(db);
    }
    @Test void createRejectsInvalidDemographicsBeforeWriting() {
        var invalid=new PatientController.PatientInput(" ","Doe",LocalDate.now(),null,null,null,null,null,null,false,null);
        assertThrows(ResponseStatusException.class,()->api.create(auth("FRONT_DESK"),invalid));
        verifyNoInteractions(db,numbers);
    }
    @Test void updatesRequireAnExpectedVersionAndRejectStaleWrites() {
        var user=auth("OWNER");var principal=(AuthPrincipal)user.getPrincipal();var id=UUID.randomUUID();
        var withoutVersion=new PatientController.PatientInput("Anne","Doe",null,null,null,null,null,null,null,false,null);
        var missing=assertThrows(ResponseStatusException.class,()->api.update(user,id,withoutVersion));
        assertEquals(HttpStatus.BAD_REQUEST,missing.getStatusCode());

        var stale=new PatientController.PatientInput("Anne","Doe",null,null,null,null,null,null,null,false,2L);
        when(db.update(contains("AND version=?"),any(Object[].class))).thenReturn(0);
        when(db.queryForObject(contains("SELECT count(*) FROM patient"),eq(Integer.class),eq(id),eq(principal.clinicId()))).thenReturn(1);
        var conflict=assertThrows(ResponseStatusException.class,()->api.update(user,id,stale));
        assertEquals(HttpStatus.CONFLICT,conflict.getStatusCode());
        verify(db).update(contains("AND version=?"),any(Object[].class));
        verify(db,never()).update(startsWith("INSERT INTO audit_log"),any(Object[].class));
    }
    @Test void archiveIsNotAvailableToReceptionOrDentists() {
        for (String role:List.of("FRONT_DESK","DENTIST"))
            assertThrows(AccessDeniedException.class,()->api.archive(auth(role),UUID.randomUUID()));
        verifyNoInteractions(db);
    }
    @Test void searchUsesTenantParameter() {
        var a=auth("DENTIST");
        api.search(a,"Anne",0,10);
        verify(db).queryForList(contains("WHERE clinic_id=? AND deleted_at IS NULL"),eq(((AuthPrincipal)a.getPrincipal()).clinicId()),any(),any(),any(),any(),any(),eq(10),eq(0));
    }
}
