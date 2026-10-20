package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Scheduling authorization and basic validation unit regressions, with synthetic identities. */
class SchedulingSecurityTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final SchedulingController api=new SchedulingController(db);
    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),role),null);
    }
    @Test void frontDeskCannotConfigureAppointmentTypes() {
        assertThrows(AccessDeniedException.class,()->api.type(auth("FRONT_DESK"),new SchedulingController.TypeInput("Cleaning",30,0,0,null)));
        verifyNoInteractions(db);
    }
    @Test void frontDeskCannotConfigureSchedulingPolicy() {
        assertThrows(AccessDeniedException.class,()->api.updatePolicy(auth("FRONT_DESK"),new SchedulingController.PolicyInput(15,0,180)));
        verifyNoInteractions(db);
    }
    @Test void dentistCannotCreateChairMaintenanceBlocks() {
        var block=new SchedulingController.BlockInput(UUID.randomUUID(),Instant.now(),Instant.now().plusSeconds(3600),"Maintenance");
        assertThrows(AccessDeniedException.class,()->api.chairBlock(auth("DENTIST"),block));
        verifyNoInteractions(db);
    }
    @Test void timeOffRejectsInvalidIntervalBeforeQueryingResource() {
        Instant start=Instant.now();
        assertThrows(ResponseStatusException.class,()->api.timeOff(auth("ADMIN"),new SchedulingController.BlockInput(UUID.randomUUID(),start,start,"Bad interval")));
        verifyNoInteractions(db);
    }
    @Test void rejectsInvalidWeeklyAvailabilityWithoutWrites() {
        UUID clinic=UUID.randomUUID(),dentist=UUID.randomUUID(),branch=UUID.randomUUID();
        var principal=new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,"OWNER"),null);
        when(db.queryForObject(anyString(),eq(Boolean.class),eq(clinic),eq(dentist))).thenReturn(true);
        when(db.queryForObject(anyString(),eq(Boolean.class),eq(clinic),eq(branch))).thenReturn(true);
        assertThrows(ResponseStatusException.class,()->api.setAvailability(principal,new SchedulingController.AvailabilityInput(dentist,branch,8,LocalTime.NOON,LocalTime.NOON.plusHours(1))));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void readCalendarAlwaysFiltersByAuthenticatedClinic() {
        UUID clinic=UUID.randomUUID(),branch=UUID.randomUUID();
        var principal=new UsernamePasswordAuthenticationToken(new AuthPrincipal(UUID.randomUUID(),clinic,"FRONT_DESK"),null);
        Instant from=Instant.parse("2026-11-01T00:00:00Z"),to=from.plusSeconds(3600);
        when(db.queryForList(anyString(),eq(clinic),eq(branch),eq(java.sql.Timestamp.from(to)),eq(java.sql.Timestamp.from(from)))).thenReturn(List.of(Map.of("id",UUID.randomUUID())));
        assertEquals(1,api.calendar(principal,branch,from,to).size());
        verify(db).queryForList(contains("a.clinic_id=? AND a.branch_id=?"),eq(clinic),eq(branch),eq(java.sql.Timestamp.from(to)),eq(java.sql.Timestamp.from(from)));
    }
}
