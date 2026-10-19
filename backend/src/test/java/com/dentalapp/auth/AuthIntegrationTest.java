package com.dentalapp.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalapp.tenancy.TenantContext;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.Connection;

/** Runs against an explicitly supplied disposable PostgreSQL database (M04_AUTH_TEST_DB_URL). */
@EnabledIfEnvironmentVariable(named = "M04_AUTH_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {
    private static final String CLIENT_ID = "m04-test-client";
    private static final RSAKey SIGNING_KEY;
    private static final HttpServer JWKS_SERVER;

    static {
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID("m04-test-key").generate();
            JWKS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS_SERVER.createContext("/keys", exchange -> {
                byte[] body = ("{\"keys\":[" + SIGNING_KEY.toPublicJWK().toJSONString() + "]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            JWKS_SERVER.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("M04_AUTH_TEST_DB_URL");
        String owner = System.getenv().getOrDefault("M04_AUTH_TEST_OWNER_USER", "postgres");
        String ownerPassword = System.getenv("M04_AUTH_TEST_OWNER_PASSWORD");
        String appPassword = System.getenv("M04_AUTH_TEST_APP_PASSWORD");
        try (var connection = DriverManager.getConnection(url, owner, ownerPassword);
             var statement = connection.createStatement()) {
            try (var roles = statement.executeQuery("SELECT rolsuper,rolbypassrls FROM pg_roles WHERE rolname='dental_app'")) {
                if (!roles.next()) {
                    statement.execute("CREATE ROLE dental_app LOGIN PASSWORD '" + appPassword.replace("'", "''") + "' NOSUPERUSER NOBYPASSRLS");
                } else if (roles.getBoolean(1) || roles.getBoolean(2)) {
                    throw new IllegalStateException("dental_app test role must be NOSUPERUSER NOBYPASSRLS");
                }
            }
        } catch (Exception e) { throw new IllegalStateException("Could not provision restricted auth test role", e); }
        properties.add("spring.datasource.url", () -> System.getenv("M04_AUTH_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("M04_AUTH_TEST_APP_USER", "dental_app"));
        properties.add("spring.datasource.password", () -> System.getenv("M04_AUTH_TEST_APP_PASSWORD"));
        properties.add("spring.flyway.url", () -> System.getenv("M04_AUTH_TEST_DB_URL"));
        properties.add("spring.flyway.user", () -> System.getenv().getOrDefault("M04_AUTH_TEST_OWNER_USER", "postgres"));
        properties.add("spring.flyway.password", () -> System.getenv("M04_AUTH_TEST_OWNER_PASSWORD"));
        properties.add("app.auth.google-client-id", () -> CLIENT_ID);
        properties.add("app.auth.google-jwk-set-uri", () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/keys");
        properties.add("app.auth.jwt-secret", () -> "m04-integration-test-jwt-secret-32-bytes-minimum");
        properties.add("app.auth.cookie-secure", () -> "false");
        properties.add("app.auth.allowed-origins", () -> "http://localhost:5173");
        properties.add("app.auth.login-requests-per-minute", () -> "1000");
        properties.add("app.auth.refresh-requests-per-minute", () -> "1000");
        properties.add("app.auth.platform-admin-emails", () -> "platform-admin@test.invalid");
        // Deliberately enabled only inside this disposable integration-test ApplicationContext.
        properties.add("app.onboarding.self-service-enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DataSource dataSource;
    private final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int port;
    UUID clinicId;
    UUID otherClinicId;
    UUID activeUserId;
    UUID otherUserId;
    UUID otherPatientId;
    UUID branchId;
    UUID otherBranchId;
    UUID dentistUserId;
    UUID frontDeskUserId;
    String otherSlug;
    String slug;

    @AfterAll static void stopJwksServer() { JWKS_SERVER.stop(0); }

    @BeforeEach void createStaffFixture() {
        clinicId = UUID.randomUUID();
        slug = "m04-" + clinicId.toString().substring(0, 8);
        otherClinicId = UUID.randomUUID();
        otherSlug = "m04-" + otherClinicId.toString().substring(0, 8);
        jdbc.update("INSERT INTO clinic(id,slug,name) VALUES (?,?,?),(?,?,?)", clinicId, slug, "M04 Auth Clinic", otherClinicId, otherSlug, "M04 Other Clinic");
        activeUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        otherPatientId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        otherBranchId = UUID.randomUUID();
        dentistUserId = UUID.randomUUID();
        frontDeskUserId = UUID.randomUUID();
        TenantContext.runAs(clinicId, () -> {
            jdbc.update("INSERT INTO branch(id,clinic_id,name) VALUES (?,?,?)", branchId, clinicId, "Synthetic Main Branch A");
            jdbc.update("""
            INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active)
            VALUES (?,?,?,?,?,'OWNER',true)
            """, activeUserId, clinicId, email(), "unused", "Active Staff");
            jdbc.update("""
            INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active)
            VALUES (?,?,?,?,?,'FRONT_DESK',false)
            """, UUID.randomUUID(), clinicId, "inactive-" + slug + "@test.invalid", "unused", "Inactive Staff");
            jdbc.update("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES (?,?,?,?,?,'DENTIST',true)",
                    dentistUserId, clinicId, "dentist-" + slug + "@test.invalid", "unused", "Synthetic Dentist");
            jdbc.update("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES (?,?,?,?,?,'FRONT_DESK',true)",
                    frontDeskUserId, clinicId, "frontdesk-" + slug + "@test.invalid", "unused", "Synthetic Front Desk");
            jdbc.update("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), clinicId, "A-" + slug, "Patient", "A");
        });
        TenantContext.runAs(otherClinicId, () -> {
            jdbc.update("INSERT INTO branch(id,clinic_id,name) VALUES (?,?,?)", otherBranchId, otherClinicId, "Synthetic Main Branch B");
            jdbc.update("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES (?,?,?,?,?,'OWNER',true)",
                    otherUserId, otherClinicId, "other-" + otherSlug + "@test.invalid", "unused", "Other Staff");
            jdbc.update("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES (?,?,?,?,?)",
                    otherPatientId, otherClinicId, "B-" + otherSlug, "Patient", "B");
        });
    }

    private String email() { return "active-" + slug + "@test.invalid"; }
    private String accessToken(String mail) throws Exception {
        return objectMapper.readTree(login(slug, validToken(mail), "http://localhost:5173").body()).get("accessToken").asText();
    }
    private String token(String issuer, String audience, String email, boolean verified, Instant expires, RSAKey key) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("google-subject-" + UUID.randomUUID())
                .audience(audience).issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(expires)).claim("email", email).claim("email_verified", verified).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    private String validToken(String mail) throws Exception {
        return token("https://accounts.google.com", CLIENT_ID, mail, true, Instant.now().plusSeconds(300), SIGNING_KEY);
    }
    private HttpResponse<String> request(String method, String path, String body, String cookie, String origin, String bearer) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (origin != null) builder.header("Origin", origin);
        if (cookie != null) builder.header("Cookie", cookie);
        if (bearer != null) builder.header("Authorization", bearer);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try { return http.send(builder.build(), HttpResponse.BodyHandlers.ofString()); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private HttpResponse<String> login(String clinic, String identityToken, String origin) throws Exception {
        return request("POST", "/api/auth/google", objectMapper.writeValueAsString(java.util.Map.of("clinicSlug", clinic, "idToken", identityToken)), null, origin, null);
    }
    private HttpResponse<String> refresh(String cookie) {
        return request("POST", "/api/auth/refresh", null, "dental_refresh=" + cookie, "http://localhost:5173", null);
    }
    private String cookieValue(HttpResponse<?> response) {
        String setCookie = response.headers().firstValue("Set-Cookie").orElse(null);
        assertThat(setCookie).isNotNull();
        String cookie = setCookie.split(";", 2)[0];
        return cookie.substring(cookie.indexOf('=') + 1);
    }

    @Test void invitationCreationValidatesPayloadDuplicatesAndTenantScope() throws Exception {
        var ownerLogin = login(slug, validToken(email()), "http://localhost:5173");
        assertThat(ownerLogin.statusCode()).isEqualTo(200);
        String ownerAccess = objectMapper.readTree(ownerLogin.body()).get("accessToken").asText();
        String invitee = "invitee-" + slug + "@test.invalid";
        String validBody = objectMapper.writeValueAsString(java.util.Map.of(
                "email", invitee, "role", "FRONT_DESK", "branchId", branchId));

        var created = request("POST", "/api/onboarding/invitations", validBody,
                null, "http://localhost:5173", "Bearer " + ownerAccess);
        assertThat(created.statusCode()).isEqualTo(201);
        var createdJson = objectMapper.readTree(created.body());
        UUID invitationId = UUID.fromString(createdJson.get("id").asText());
        assertThat(createdJson.get("token").asText()).isNotBlank();
        TenantContext.runAs(clinicId, () -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_invitation WHERE id=? AND clinic_id=? AND email=? AND role='FRONT_DESK' AND branch_id=?", Integer.class,
                    invitationId, clinicId, invitee, branchId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM clinic_admin_event WHERE clinic_id=? AND subject_id=?", Integer.class, clinicId, invitationId)).isEqualTo(1);
        });

        var duplicate = request("POST", "/api/onboarding/invitations", validBody,
                null, "http://localhost:5173", "Bearer " + ownerAccess);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(duplicate.body()).get("detail").asText()).contains("active invitation");

        var invalidRole = request("POST", "/api/onboarding/invitations", objectMapper.writeValueAsString(java.util.Map.of(
                "email", "bad-role-" + slug + "@test.invalid", "role", "OWNER", "branchId", branchId)),
                null, "http://localhost:5173", "Bearer " + ownerAccess);
        assertThat(invalidRole.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(invalidRole.body()).get("detail").asText()).contains("Choose Administrator");

        var emptyRole = request("POST", "/api/onboarding/invitations", objectMapper.writeValueAsString(java.util.Map.of(
                "email", "empty-role-" + slug + "@test.invalid", "role", "", "branchId", branchId)),
                null, "http://localhost:5173", "Bearer " + ownerAccess);
        assertThat(emptyRole.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(emptyRole.body()).get("detail").asText()).contains("Choose a staff role");

        var crossClinicBranch = request("POST", "/api/onboarding/invitations", objectMapper.writeValueAsString(java.util.Map.of(
                "email", "cross-clinic-" + slug + "@test.invalid", "role", "DENTIST", "branchId", otherBranchId)),
                null, "http://localhost:5173", "Bearer " + ownerAccess);
        assertThat(crossClinicBranch.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(crossClinicBranch.body()).get("detail").asText()).contains("active branch in your clinic");
        TenantContext.runAs(clinicId, () -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM staff_invitation WHERE clinic_id=?", Integer.class, clinicId)).isEqualTo(1));
        TenantContext.runAs(otherClinicId, () -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM staff_invitation WHERE clinic_id=?", Integer.class, otherClinicId)).isZero());

        assertThat(request("POST", "/api/onboarding/invitations", validBody,
                null, "http://localhost:5173", null).statusCode()).isEqualTo(401);
        var dentistLogin = login(slug, validToken("dentist-" + slug + "@test.invalid"), "http://localhost:5173");
        String dentistAccess = objectMapper.readTree(dentistLogin.body()).get("accessToken").asText();
        assertThat(request("POST", "/api/onboarding/invitations", validBody,
                null, "http://localhost:5173", "Bearer " + dentistAccess).statusCode()).isEqualTo(403);
    }

    @Test void googleIdentityMatrixAndClinicMembershipAreEnforced() throws Exception {
        assertThat(login(slug, validToken(email()), "http://localhost:5173").statusCode()).isEqualTo(200);
        assertThat(login(slug, "not.a.jwt", "http://localhost:5173").statusCode()).isEqualTo(401);
        assertThat(login(slug, token("https://wrong.invalid", CLIENT_ID, email(), true, Instant.now().plusSeconds(300), SIGNING_KEY), "http://localhost:5173").statusCode()).isEqualTo(401);
        assertThat(login(slug, token("https://accounts.google.com", "wrong-audience", email(), true, Instant.now().plusSeconds(300), SIGNING_KEY), "http://localhost:5173").statusCode()).isEqualTo(401);
        assertThat(login(slug, token("https://accounts.google.com", CLIENT_ID, email(), true, Instant.now().minusSeconds(1), SIGNING_KEY), "http://localhost:5173").statusCode()).isEqualTo(401);
        assertThat(login(slug, token("https://accounts.google.com", CLIENT_ID, email(), false, Instant.now().plusSeconds(300), SIGNING_KEY), "http://localhost:5173").statusCode()).isEqualTo(401);
        RSAKey other = new RSAKeyGenerator(2048).keyID("wrong-key").generate();
        assertThat(login(slug, token("https://accounts.google.com", CLIENT_ID, email(), true, Instant.now().plusSeconds(300), other), "http://localhost:5173").statusCode()).isEqualTo(401);
        assertThat(login(slug, validToken("unknown@test.invalid"), "http://localhost:5173").statusCode()).isEqualTo(403);
        assertThat(login(slug, validToken("inactive-" + slug + "@test.invalid"), "http://localhost:5173").statusCode()).isEqualTo(403);
        assertThat(login("unknown-clinic", validToken(email()), "http://localhost:5173").statusCode()).isEqualTo(403);
        assertThat(login(otherSlug, validToken(email()), "http://localhost:5173").statusCode()).isEqualTo(403);
        assertThat(login(slug, validToken(email()), "https://evil.invalid").statusCode()).isEqualTo(403);
    }

    @Test void refreshRotationReplayRevocationCookiesAndLogoutWork() throws Exception {
        var loggedIn = login(slug, validToken(email()), "http://localhost:5173");
        assertThat(loggedIn.statusCode()).isEqualTo(200);
        String firstCookie = cookieValue(loggedIn);
        String setCookie = loggedIn.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
        // Split across statements: gitleaks' generic-api-key rule misreads "auth\", \"Max-Age=..." on one line.
        assertThat(setCookie).contains("Max-Age=1209600");
        assertThat(setCookie).doesNotContain("Secure"); // Test profile explicitly disables HTTPS-only cookies.

        var rotated = refresh(firstCookie);
        assertThat(rotated.statusCode()).isEqualTo(200);
        String secondCookie = cookieValue(rotated);
        assertThat(secondCookie).isNotEqualTo(firstCookie);
        assertThat(refresh(firstCookie).statusCode()).isEqualTo(401);
        assertThat(refresh(secondCookie).statusCode()).isEqualTo(401); // replay revokes the user's live sessions

        var anotherLogin = login(slug, validToken(email()), "http://localhost:5173");
        String logoutCookie = cookieValue(anotherLogin);
        var logout = request("POST", "/api/auth/logout", null, "dental_refresh=" + logoutCookie, "http://localhost:5173", null);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().firstValue("Set-Cookie").orElseThrow()).contains("HttpOnly", "SameSite=Strict", "Max-Age=0");
        assertThat(refresh(logoutCookie).statusCode()).isEqualTo(401);
    }

    @Test void concurrentRefreshAllowsOneRotationThenRevokesOnReplay() throws Exception {
        String cookie = cookieValue(login(slug, validToken(email()), "http://localhost:5173"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = pool.submit(() -> refresh(cookie));
            Future<HttpResponse<String>> second = pool.submit(() -> refresh(cookie));
            int a = first.get(20, TimeUnit.SECONDS).statusCode();
            int b = second.get(20, TimeUnit.SECONDS).statusCode();
            assertThat(java.util.List.of(a,b)).containsExactlyInAnyOrder(200,401);
            String rotatedCookie = a == 200 ? cookieValue(first.get()) : cookieValue(second.get());
            assertThat(refresh(rotatedCookie).statusCode()).isEqualTo(401);
        } finally { pool.shutdownNow(); }
    }

    @Test void accessJwtIsAcceptedOnlyWhenSignedAndUnexpiredAndProtectedRoutesRequireIt() throws Exception {
        var login = login(slug, validToken(email()), "http://localhost:5173");
        String access = objectMapper.readTree(login.body()).get("accessToken").asText();
        int authenticatedStatus = request("GET", "/api/not-found", null, null, null, "Bearer " + access).statusCode();
        assertThat(authenticatedStatus).isEqualTo(404);
        assertThat(request("GET", "/api/not-found", null, null, null, null).statusCode()).isEqualTo(401);
        String[] parts = access.split("\\.");
        char firstSignature = parts[2].charAt(0);
        parts[2] = (firstSignature == 'a' ? "b" : "a") + parts[2].substring(1);
        assertThat(request("GET", "/api/not-found", null, null, null, "Bearer " + String.join(".", parts)).statusCode()).isEqualTo(401);
        String expired = expiredAccessToken();
        assertThat(request("GET", "/api/not-found", null, null, null, "Bearer " + expired).statusCode()).isEqualTo(401);
    }

    @Test void pooledConnectionTenantSettingIsClearedBeforeReuse() throws Exception {
        TenantContext.set(clinicId);
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM app_user")) {
            result.next(); assertThat(result.getInt(1)).isEqualTo(4);
        } finally { TenantContext.clear(); }
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM app_user")) {
            result.next(); assertThat(result.getInt(1)).isZero();
        }
    }

    @Test void springMvcBodyAndRequestParameterDiagnosticsAreNotDebugEnabled() {
        assertThat(org.slf4j.LoggerFactory.getLogger("org.springframework.web.servlet.DispatcherServlet").isDebugEnabled()).isFalse();
        assertThat(org.slf4j.LoggerFactory.getLogger("org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor").isDebugEnabled()).isFalse();
        assertThat(org.slf4j.LoggerFactory.getLogger("org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor").isDebugEnabled()).isFalse();
    }

    @Test void patientRegistryIdentityCareAndArchiveWorkOverAuthenticatedHttp() throws Exception {
        String owner = accessToken(email());
        String dentist = accessToken("dentist-" + slug + "@test.invalid");
        String frontDesk = accessToken("frontdesk-" + slug + "@test.invalid");
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "firstName", "Synthetic", "lastName", "Patient", "birthDate", "2015-04-03",
                "sex", "UNDISCLOSED", "phone", "555-0101", "email", "patient@test.invalid",
                "smsOptIn", false));
        var created = request("POST", "/api/patients", body, null, "http://localhost:5173", "Bearer " + owner);
        assertThat(created.statusCode()).isEqualTo(201);
        var patient = objectMapper.readTree(created.body());
        UUID patientId = UUID.fromString(patient.get("id").asText());
        assertThat(patient.get("chart_no").asText()).isEqualTo("P-000001");
        assertThat(request("GET", "/api/patients?q=Synthetic%20Patient&limit=10", null, null, null, "Bearer " + owner).statusCode()).isEqualTo(200);
        var candidates = request("GET", "/api/patients/duplicate-candidates?firstName=Synthetic&lastName=Patient&birthDate=2015-04-03", null, null, null, "Bearer " + owner);
        assertThat(objectMapper.readTree(candidates.body()).size()).isEqualTo(1);

        var initial = objectMapper.readTree(request("GET", "/api/patients/" + patientId, null, null, null, "Bearer " + owner).body());
        long version = initial.get("version").asLong();
        var update = objectMapper.readTree(body);
        ((com.fasterxml.jackson.databind.node.ObjectNode) update).put("firstName", "Edited").put("version", version);
        var firstEdit = objectMapper.writeValueAsString(update);
        var editResult = request("PUT", "/api/patients/" + patientId, firstEdit, null, "http://localhost:5173", "Bearer " + owner);
        assertThat(editResult.statusCode()).isEqualTo(200);
        var stale = request("PUT", "/api/patients/" + patientId, firstEdit, null, "http://localhost:5173", "Bearer " + owner);
        assertThat(stale.statusCode()).isEqualTo(409);
        ((com.fasterxml.jackson.databind.node.ObjectNode) update).put("version", 1).put("lastName", "Concurrent A");
        String concurrentA = objectMapper.writeValueAsString(update);
        ((com.fasterxml.jackson.databind.node.ObjectNode) update).put("lastName", "Concurrent B");
        String concurrentB = objectMapper.writeValueAsString(update);
        var editPool = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> editA = editPool.submit(() -> request("PUT", "/api/patients/" + patientId, concurrentA, null, "http://localhost:5173", "Bearer " + owner));
            Future<HttpResponse<String>> editB = editPool.submit(() -> request("PUT", "/api/patients/" + patientId, concurrentB, null, "http://localhost:5173", "Bearer " + owner));
            var statuses = java.util.List.of(editA.get(20, TimeUnit.SECONDS).statusCode(), editB.get(20, TimeUnit.SECONDS).statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally { editPool.shutdownNow(); }
        assertThat(request("GET", "/api/patients/" + otherPatientId, null, null, null, "Bearer " + owner).statusCode()).isEqualTo(404);

        var historyWrite = request("PUT", "/api/patients/" + patientId + "/medical-history",
                "{\"allergies\":\"synthetic pollen\",\"conditions\":\"test condition\",\"medications\":\"test medication\"}", null, "http://localhost:5173", "Bearer " + dentist);
        assertThat(historyWrite.statusCode()).isEqualTo(200);
        assertThat(request("GET", "/api/patients/" + patientId + "/medical-history", null, null, null, "Bearer " + frontDesk).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/patients/" + otherPatientId + "/guardians", null, null, null, "Bearer " + owner).statusCode()).isEqualTo(404);

        var guardian = request("POST", "/api/patients/" + patientId + "/guardians",
                "{\"fullName\":\"Synthetic guardian\",\"relationship\":\"Parent\",\"primary\":true}", null, "http://localhost:5173", "Bearer " + owner);
        assertThat(guardian.statusCode()).isEqualTo(201);
        UUID guardianId = UUID.fromString(objectMapper.readTree(guardian.body()).get("id").asText());
        var consent = request("POST", "/api/patients/" + patientId + "/consents", "{\"formType\":\"test form\"}", null, "http://localhost:5173", "Bearer " + owner);
        assertThat(consent.statusCode()).isEqualTo(201);
        UUID consentId = UUID.fromString(objectMapper.readTree(consent.body()).get("id").asText());
        assertThat(request("POST", "/api/patients/" + patientId + "/consents/" + consentId + "/acknowledge", null, null, "http://localhost:5173", "Bearer " + owner).statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/patients/" + patientId + "/consents/" + consentId + "/acknowledge", null, null, "http://localhost:5173", "Bearer " + owner).statusCode()).isEqualTo(409);
        assertThat(TenantContext.callAs(clinicId, () -> jdbc.queryForObject("SELECT signed_at IS NULL AND signed_by IS NULL FROM consent_form WHERE id=? AND clinic_id=?", Boolean.class, consentId, clinicId))).isTrue();
        assertThat(request("DELETE", "/api/patients/" + patientId + "/guardians/" + guardianId, null, null, "http://localhost:5173", "Bearer " + owner).statusCode()).isEqualTo(204);
        assertThat(TenantContext.callAs(clinicId, () -> jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE clinic_id=? AND user_id=? AND entity_id=?", Integer.class, clinicId, activeUserId, patientId))).isGreaterThanOrEqualTo(3);

        assertThat(request("POST", "/api/patients/" + patientId + "/archive", null, null, "http://localhost:5173", "Bearer " + dentist).statusCode()).isEqualTo(403);
        assertThat(request("POST", "/api/patients/" + patientId + "/archive", null, null, "http://localhost:5173", "Bearer " + owner).statusCode()).isEqualTo(204);
        assertThat(request("GET", "/api/patients/" + patientId, null, null, null, "Bearer " + owner).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/patients/" + patientId + "/guardians", null, null, null, "Bearer " + owner).statusCode()).isEqualTo(404);
    }

    @Test void clinicOwnerInvitationAcceptanceBranchDentistAndAuditLifecycleWorkOverHttp() throws Exception {
        String ownerEmail = "onboard-owner-" + UUID.randomUUID() + "@test.invalid";
        String clinicSlug = "onboard-" + UUID.randomUUID().toString().substring(0, 8);
        var create = request("POST", "/api/onboarding/clinics", objectMapper.writeValueAsString(java.util.Map.of(
                "googleIdToken", validToken(ownerEmail), "slug", clinicSlug, "name", "Synthetic Onboarding Clinic",
                "timezone", "Asia/Manila", "branchName", "Synthetic Main Branch", "ownerName", "Synthetic Owner")),
                null, "http://localhost:5173", null);
        assertThat(create.statusCode()).isEqualTo(201);
        var created = objectMapper.readTree(create.body());
        UUID createdClinic = UUID.fromString(created.get("clinicId").asText());
        UUID branchId = UUID.fromString(created.get("branchId").asText());
        UUID ownerId = UUID.fromString(created.get("ownerId").asText());
        TenantContext.runAs(createdClinic, () -> {
            assertThat(jdbc.queryForObject("SELECT timezone FROM clinic WHERE id=?", String.class, createdClinic)).isEqualTo("Asia/Manila");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM branch WHERE id=? AND clinic_id=?", Integer.class, branchId, createdClinic)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_branch WHERE user_id=? AND clinic_id=? AND branch_id=?", Integer.class, ownerId, createdClinic, branchId)).isEqualTo(1);
        });

        var ownerLogin = login(clinicSlug, validToken(ownerEmail), "http://localhost:5173");
        assertThat(ownerLogin.statusCode()).isEqualTo(200);
        String ownerAccess = objectMapper.readTree(ownerLogin.body()).get("accessToken").asText();
        String staffEmail = "onboard-dentist-" + UUID.randomUUID() + "@test.invalid";
        var invited = request("POST", "/api/onboarding/invitations", objectMapper.writeValueAsString(
                java.util.Map.of("email", staffEmail, "role", "DENTIST", "branchId", branchId)),
                null, "http://localhost:5173", "Bearer " + ownerAccess);
        assertThat(invited.statusCode()).isEqualTo(201);
        var invitation = objectMapper.readTree(invited.body());
        String inviteToken = invitation.get("token").asText();
        UUID inviteId = UUID.fromString(invitation.get("id").asText());

        var wrongEmail = request("POST", "/api/onboarding/accept", objectMapper.writeValueAsString(java.util.Map.of(
                "googleIdToken", validToken("wrong-recipient-" + UUID.randomUUID() + "@test.invalid"),
                "token", inviteToken, "fullName", "Synthetic Wrong Recipient")), null, "http://localhost:5173", null);
        assertThat(wrongEmail.statusCode()).isEqualTo(403);
        var accepted = request("POST", "/api/onboarding/accept", objectMapper.writeValueAsString(java.util.Map.of(
                "googleIdToken", validToken(staffEmail), "token", inviteToken, "fullName", "Synthetic Dentist")),
                null, "http://localhost:5173", null);
        assertThat(accepted.statusCode()).isEqualTo(200);
        UUID staffId = UUID.fromString(objectMapper.readTree(accepted.body()).get("userId").asText());
        assertThat(request("POST", "/api/onboarding/accept", objectMapper.writeValueAsString(java.util.Map.of(
                "googleIdToken", validToken(staffEmail), "token", inviteToken, "fullName", "Synthetic Dentist")),
                null, "http://localhost:5173", null).statusCode()).isEqualTo(403);

        var staffLogin = login(clinicSlug, validToken(staffEmail), "http://localhost:5173");
        assertThat(staffLogin.statusCode()).isEqualTo(200);
        TenantContext.runAs(createdClinic, () -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id=? AND active=true AND role='DENTIST'", Integer.class, staffId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_branch WHERE user_id=? AND clinic_id=? AND branch_id=?", Integer.class, staffId, createdClinic, branchId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_invitation WHERE id=? AND accepted_at IS NOT NULL", Integer.class, inviteId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM clinic_admin_event WHERE clinic_id=?", Integer.class, createdClinic)).isEqualTo(3);
        });

        var dentist = request("POST", "/api/onboarding/dentists", objectMapper.writeValueAsString(java.util.Map.of(
                "userId", staffId, "fullName", "Synthetic Dentist", "licenseNo", "TEST-LICENSE-001", "specialty", "General Dentistry")),
                null, "http://localhost:5173", "Bearer " + ownerAccess);
        assertThat(dentist.statusCode()).isEqualTo(201);
        TenantContext.runAs(createdClinic, () -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dentist WHERE user_id=? AND clinic_id=?", Integer.class, staffId, createdClinic)).isEqualTo(1));
    }

    @Test void clinicRegistrationRejectsInvalidDuplicateAndUnverifiedAttemptsSafely() throws Exception {
        String clinicSlug = "registration-" + UUID.randomUUID().toString().substring(0, 8);
        var validBody = java.util.Map.of(
                "googleIdToken", validToken("registration-owner-" + UUID.randomUUID() + "@test.invalid"),
                "slug", clinicSlug, "name", "Registration Test Clinic", "timezone", "Asia/Manila",
                "branchName", "Main Branch", "ownerName", "Test Owner");
        var created = request("POST", "/api/onboarding/clinics", objectMapper.writeValueAsString(validBody),
                null, "http://localhost:5173", null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID clinicId = UUID.fromString(objectMapper.readTree(created.body()).get("clinicId").asText());

        var duplicate = new java.util.HashMap<>(validBody);
        duplicate.put("googleIdToken", validToken("second-owner-" + UUID.randomUUID() + "@test.invalid"));
        var duplicateResponse = request("POST", "/api/onboarding/clinics", objectMapper.writeValueAsString(duplicate),
                null, "http://localhost:5173", null);
        assertThat(duplicateResponse.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(duplicateResponse.body()).get("detail").asText()).contains("clinic code is already in use");

        var invalidSlug = new java.util.HashMap<>(validBody);
        invalidSlug.put("slug", "BAD_SLUG");
        var invalidSlugResponse = request("POST", "/api/onboarding/clinics", objectMapper.writeValueAsString(invalidSlug),
                null, "http://localhost:5173", null);
        assertThat(invalidSlugResponse.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(invalidSlugResponse.body()).get("detail").asText()).contains("Invalid clinic slug");

        var invalidZone = new java.util.HashMap<>(validBody);
        invalidZone.put("slug", "timezone-" + UUID.randomUUID().toString().substring(0, 8));
        invalidZone.put("timezone", "Mars/Olympus");
        var invalidZoneResponse = request("POST", "/api/onboarding/clinics", objectMapper.writeValueAsString(invalidZone),
                null, "http://localhost:5173", null);
        assertThat(invalidZoneResponse.statusCode()).isEqualTo(400);

        var unverified = new java.util.HashMap<>(validBody);
        unverified.put("slug", "unverified-" + UUID.randomUUID().toString().substring(0, 8));
        unverified.put("googleIdToken", "not-a-google-token");
        var unverifiedResponse = request("POST", "/api/onboarding/clinics", objectMapper.writeValueAsString(unverified),
                null, "http://localhost:5173", null);
        assertThat(unverifiedResponse.statusCode()).isEqualTo(401);

        var unauthorizedOrigin = request("POST", "/api/onboarding/clinics", objectMapper.writeValueAsString(validBody),
                null, "https://attacker.invalid", null);
        assertThat(unauthorizedOrigin.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinic WHERE slug=?", Integer.class, clinicSlug)).isEqualTo(1);
        TenantContext.runAs(clinicId, () -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM branch WHERE clinic_id=?", Integer.class, clinicId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE clinic_id=? AND role='OWNER'", Integer.class, clinicId)).isEqualTo(1);
        });
    }

    @Test void revokedAndExpiredInvitationsCannotBeAccepted() throws Exception {
        String ownerAccess = objectMapper.readTree(login(slug, validToken(email()), "http://localhost:5173").body()).get("accessToken").asText();
        UUID branch = TenantContext.callAs(clinicId, () -> jdbc.queryForObject("SELECT id FROM branch WHERE clinic_id=? LIMIT 1", UUID.class, clinicId));
        String revokedEmail = "revoked-" + UUID.randomUUID() + "@test.invalid";
        var revokedInvite = request("POST", "/api/onboarding/invitations", objectMapper.writeValueAsString(java.util.Map.of(
                "email", revokedEmail, "role", "FRONT_DESK", "branchId", branch)), null, "http://localhost:5173", "Bearer " + ownerAccess);
        var revoked = objectMapper.readTree(revokedInvite.body());
        UUID revokedId = UUID.fromString(revoked.get("id").asText());
        assertThat(request("POST", "/api/onboarding/invitations/" + revokedId + "/revoke", null,
                null, "http://localhost:5173", "Bearer " + ownerAccess).statusCode()).isEqualTo(204);
        assertThat(request("POST", "/api/onboarding/accept", objectMapper.writeValueAsString(java.util.Map.of(
                "googleIdToken", validToken(revokedEmail), "token", revoked.get("token").asText(), "fullName", "Synthetic Staff")),
                null, "http://localhost:5173", null).statusCode()).isEqualTo(403);

        String expiredEmail = "expired-" + UUID.randomUUID() + "@test.invalid";
        var expiredInvite = request("POST", "/api/onboarding/invitations", objectMapper.writeValueAsString(java.util.Map.of(
                "email", expiredEmail, "role", "FRONT_DESK", "branchId", branch)), null, "http://localhost:5173", "Bearer " + ownerAccess);
        var expired = objectMapper.readTree(expiredInvite.body());
        UUID expiredId = UUID.fromString(expired.get("id").asText());
        TenantContext.runAs(clinicId, () -> jdbc.update("UPDATE staff_invitation SET expires_at=now()-interval '1 minute' WHERE id=?", expiredId));
        assertThat(request("POST", "/api/onboarding/accept", objectMapper.writeValueAsString(java.util.Map.of(
                "googleIdToken", validToken(expiredEmail), "token", expired.get("token").asText(), "fullName", "Synthetic Staff")),
                null, "http://localhost:5173", null).statusCode()).isEqualTo(403);
    }

    @Test void simultaneousHttpInvitationClaimsCreateExactlyOneStaffAccount() throws Exception {
        String staffEmail = "parallel-" + UUID.randomUUID() + "@test.invalid";
        String ownerAccess = objectMapper.readTree(login(slug, validToken(email()), "http://localhost:5173").body()).get("accessToken").asText();
        UUID branch = TenantContext.callAs(clinicId, () -> jdbc.queryForObject("SELECT id FROM branch WHERE clinic_id=? LIMIT 1", UUID.class, clinicId));
        var issued = request("POST", "/api/onboarding/invitations", objectMapper.writeValueAsString(java.util.Map.of(
                "email", staffEmail, "role", "DENTIST", "branchId", branch)), null, "http://localhost:5173", "Bearer " + ownerAccess);
        String token = objectMapper.readTree(issued.body()).get("token").asText();
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "googleIdToken", validToken(staffEmail), "token", token, "fullName", "Synthetic Parallel Dentist"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = pool.submit(() -> request("POST", "/api/onboarding/accept", body, null, "http://localhost:5173", null));
            Future<HttpResponse<String>> second = pool.submit(() -> request("POST", "/api/onboarding/accept", body, null, "http://localhost:5173", null));
            var statuses = java.util.List.of(first.get(20, TimeUnit.SECONDS).statusCode(), second.get(20, TimeUnit.SECONDS).statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(200, 403);
            TenantContext.runAs(clinicId, () -> {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(email)=?", Integer.class, staffEmail)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_invitation WHERE token_hash IS NOT NULL AND email=? AND accepted_at IS NOT NULL", Integer.class, staffEmail)).isEqualTo(1);
            });
        } finally { pool.shutdownNow(); }
    }

    @Test void authenticatedPlatformAdminCanReadDirectoryButCannotAccessClinicRecords() throws Exception {
        var allowed = request("POST", "/api/auth/platform-admin/google", objectMapper.writeValueAsString(
                java.util.Map.of("idToken", validToken("platform-admin@test.invalid"))), null, "http://localhost:5173", null);
        assertThat(allowed.statusCode()).isEqualTo(200);
        String access = objectMapper.readTree(allowed.body()).get("accessToken").asText();
        var directory = request("GET", "/api/platform-admin/clinics", null, null, null, "Bearer " + access);
        assertThat(directory.statusCode()).isEqualTo(200);
        assertThat(directory.body()).contains(slug, otherSlug, "DIRECTORY_ONLY").doesNotContain("Patient A", "Patient B", "A-" + slug, "B-" + otherSlug);
        assertThat(request("GET", "/api/clinic-management/clinic", null, null, null, "Bearer " + access).statusCode()).isEqualTo(403);

        var denied = request("POST", "/api/auth/platform-admin/google", objectMapper.writeValueAsString(
                java.util.Map.of("idToken", validToken("not-allowlisted-" + UUID.randomUUID() + "@test.invalid"))), null, "http://localhost:5173", null);
        assertThat(denied.statusCode()).isEqualTo(403);
        String[] parts = access.split("\\.");
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                objectMapper.writeValueAsBytes(java.util.Map.of("sub", UUID.randomUUID().toString(), "role", "OWNER", "clinic_id", otherClinicId.toString(), "iss", "dental-clinic-saas", "iat", Instant.now().getEpochSecond(), "exp", Instant.now().plusSeconds(300).getEpochSecond())));
        assertThat(request("GET", "/api/platform-admin/clinics", null, null, null, "Bearer " + String.join(".", parts)).statusCode()).isEqualTo(401);
    }

    @Test void applicationRoleCannotReadWriteDeleteOrLinkOtherClinicRecords() {
        TenantContext.set(clinicId);
        try {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM patient", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM patient WHERE id=?", Integer.class, otherPatientId)).isZero();
            assertThat(jdbc.update("UPDATE patient SET first_name='spoofed' WHERE id=?", otherPatientId)).isZero();
            assertThat(jdbc.update("DELETE FROM patient WHERE id=?", otherPatientId)).isZero();
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> jdbc.update("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES (?,?,?,?,?)",
                            UUID.randomUUID(), otherClinicId, "spoof-" + UUID.randomUUID(), "Spoof", "Patient"));
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> jdbc.update("INSERT INTO auth_refresh_session(clinic_id,user_id,token_hash,expires_at) VALUES (?,?,?,now()+interval '1 day')",
                            clinicId, otherUserId, "cross-clinic-" + UUID.randomUUID()));
        } finally { TenantContext.clear(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient", Integer.class)).isZero();
    }

    private String expiredAccessToken() {
        byte[] secret = "m04-integration-test-jwt-secret-32-bytes-minimum".getBytes(StandardCharsets.UTF_8);
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("dental-clinic-saas").subject(activeUserId.toString())
                .issuedAt(now.minusSeconds(300)).expiresAt(now.minusSeconds(120))
                .claim("clinic_id", clinicId.toString()).claim("role", "OWNER").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
