package com.dentalapp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final GoogleIdentityVerifier google;
    private final AuthProperties props;
    AuthController(AuthService service, GoogleIdentityVerifier google, AuthProperties props) {
        this.service=service;this.google=google;this.props=props;
    }
    record GoogleLogin(@NotBlank String clinicSlug, @NotBlank String idToken) {
        @Override public String toString() { return "GoogleLogin[clinicSlug=" + clinicSlug + ", idToken=[REDACTED]]"; }
    }
    record PlatformAdminGoogleLogin(@NotBlank String idToken) {
        @Override public String toString() { return "PlatformAdminGoogleLogin[idToken=[REDACTED]]"; }
    }
    @PostMapping("/google")
    ResponseEntity<AuthService.Session> google(@Valid @RequestBody GoogleLogin request, HttpServletRequest http) {
        checkOrigin(http);
        String email=google.verifiedEmail(request.idToken());
        var clinic=service.clinicForSlug(request.clinicSlug());
        var staff=clinic==null?null:service.staffByEmail(clinic,email);
        if(staff==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"No active clinic staff membership");
        return respond(service.create(staff));
    }
    @PostMapping("/platform-admin/google")
    ResponseEntity<AuthService.Session> platformAdminGoogle(@Valid @RequestBody PlatformAdminGoogleLogin request, HttpServletRequest http) {
        checkOrigin(http);
        String email = google.verifiedEmail(request.idToken());
        return respond(service.createPlatformAdmin(email));
    }
    @PostMapping("/refresh")
    ResponseEntity<AuthService.Session> refresh(@CookieValue(name="dental_refresh",required=false) String cookie,
                                                 HttpServletRequest http) {
        checkOrigin(http);
        return respond(service.rotate(cookie));
    }
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name="dental_refresh",required=false) String cookie,
                                HttpServletRequest http) {
        checkOrigin(http);
        if(cookie!=null) try { service.revoke(cookie); } catch (ResponseStatusException ignored) { }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("",0).toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").build();
    }
    private ResponseEntity<AuthService.Session> respond(AuthService.Issued issued) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie(issued.cookie(),14*24*3600).toString())
               .header(HttpHeaders.CACHE_CONTROL,"no-store").body(issued.session());
    }
    private ResponseCookie cookie(String value,long maxAge) {
        return ResponseCookie.from("dental_refresh",value).httpOnly(true).secure(props.cookieSecure())
                .sameSite("Strict").path("/api/auth").maxAge(maxAge).build();
    }
    // Credentialed requests are always checked even when browser CORS is not enforced (e.g. form requests).
    private void checkOrigin(HttpServletRequest request) {
        String origin=request.getHeader("Origin");
        if(origin==null || origin.isBlank()) return; // Non-browser clients must still supply valid credentials.
        if(props.allowedOrigins()==null || java.util.Arrays.stream(props.allowedOrigins().split(","))
                .map(String::trim).noneMatch(origin::equals))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Origin is not allowed");
    }
}
