package com.dentalapp.clinic;

import com.dentalapp.auth.AuthPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClinicManagementControllerTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ClinicManagementController controller = new ClinicManagementController(db);
    private final UUID clinic = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private UsernamePasswordAuthenticationToken login(String role) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(user, clinic, role), "", java.util.List.of());
    }
    @Test void frontDeskCannotCreateBranch() {
        assertThrows(AccessDeniedException.class, () -> controller.createBranch(login("FRONT_DESK"), new ClinicManagementController.BranchInput("Main",null,null)));
        verifyNoInteractions(db);
    }
    @Test void dentistCannotChangeClinicSettings() {
        assertThrows(AccessDeniedException.class, () -> controller.updateClinic(login("DENTIST"),new ClinicManagementController.ClinicUpdate("Clinic","Asia/Manila",null,null,null)));
        verifyNoInteractions(db);
    }
    @Test void invalidTimezoneRejectedBeforeUpdate() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> controller.updateClinic(login("OWNER"),new ClinicManagementController.ClinicUpdate("Clinic","not_a_timezone",null,null,null))).getStatusCode());
        verifyNoInteractions(db);
    }
    @Test void branchQueryUsesAuthenticatedClinicId() {
        controller.branches(login("DENTIST"));
        verify(db).queryForList("SELECT id,name,address,phone,active FROM branch WHERE clinic_id=? ORDER BY name",clinic);
    }
    @Test void platformAdminCannotUseClinicApi() {
        assertThrows(AccessDeniedException.class, () -> controller.branches(new UsernamePasswordAuthenticationToken("platform", "", java.util.List.of())));
        verifyNoInteractions(db);
    }
}
