package com.dentalapp.auth;

import com.dentalapp.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(10);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(14);
    private final JdbcTemplate jdbc;
    private final JwtEncoder encoder;
    private final TransactionTemplate transactions;
    private final AuthProperties props;
    private final SecureRandom random = new SecureRandom();

    AuthService(JdbcTemplate jdbc, JwtEncoder encoder, org.springframework.transaction.PlatformTransactionManager tx, AuthProperties props) {
        this.jdbc = jdbc; this.encoder = encoder; this.transactions = new TransactionTemplate(tx); this.props = props;
    }
    public record Staff(UUID id, UUID clinicId, String email, String fullName, String role,
                        String clinicSlug, String clinicName) {}
    public record Session(String accessToken, Map<String, Object> user, Map<String, Object> clinic) {
        @Override public String toString() { return "Session[accessToken=[REDACTED], user=[REDACTED], clinic=[REDACTED]]"; }
    }
    public record Issued(Session session, String cookie) {
        @Override public String toString() { return "Issued[session=[REDACTED], cookie=[REDACTED]]"; }
    }

    public UUID clinicForSlug(String slug) {
        if (slug == null || !slug.matches("[a-z0-9][a-z0-9-]{0,62}")) throw unauthorized();
        return jdbc.query("SELECT id FROM clinic WHERE slug=? AND active=true", rs ->
            rs.next() ? (UUID) rs.getObject(1) : null, slug);
    }
    public Staff staffByEmail(UUID clinicId, String email) {
        return TenantContextResult.call(clinicId, () -> jdbc.query("""
            SELECT u.id,u.clinic_id,u.email,u.full_name,u.role,c.slug,c.name
            FROM app_user u JOIN clinic c ON c.id=u.clinic_id
            WHERE u.email=? AND u.active=true AND c.active=true
            """, rs -> rs.next() ? staff(rs) : null, email));
    }
    private Staff staff(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Staff((UUID)rs.getObject(1),(UUID)rs.getObject(2),rs.getString(3),
                rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7));
    }
    public Issued create(Staff staff) {
        return TenantContextResult.call(staff.clinicId(), () -> transactions.execute(status -> issue(staff)));
    }
    public Issued createPlatformAdmin(String email) {
        if (!props.isPlatformAdmin(email)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access is not enabled for this account");
        return transactions.execute(status -> issuePlatformAdmin(email));
    }
    private Issued issue(Staff staff) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = staff.clinicId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO auth_refresh_session(clinic_id,user_id,token_hash,expires_at) VALUES(?,?,?,?)",
                staff.clinicId(), staff.id(), hash(token), java.sql.Timestamp.from(Instant.now().plus(REFRESH_LIFETIME)));
        Instant now = Instant.now();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("dental-clinic-saas").subject(staff.id().toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_LIFETIME))
                .claim("clinic_id", staff.clinicId().toString()).claim("role", staff.role()).build())).getTokenValue();
        return new Issued(new Session(access,
                Map.of("id",staff.id(),"email",staff.email(),"fullName",staff.fullName(),"role",staff.role()),
                Map.of("id",staff.clinicId(),"slug",staff.clinicSlug(),"name",staff.clinicName())),token);
    }
    public Issued rotate(String cookie) {
        if (isPlatformCookie(cookie)) return rotatePlatformAdmin(cookie);
        UUID clinic = cookieClinic(cookie);
        Issued issued = TenantContextResult.call(clinic, () -> transactions.execute(status -> {
            var matches = jdbc.query("""
                SELECT u.id,u.clinic_id,u.email,u.full_name,u.role,c.slug,c.name
                FROM auth_refresh_session s JOIN app_user u ON u.id=s.user_id AND u.clinic_id=s.clinic_id
                JOIN clinic c ON c.id=u.clinic_id
                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>now()
                  AND u.active=true AND c.active=true FOR UPDATE OF s
                """, (rs, row) -> staff(rs), hash(cookie));
            if (matches.size()!=1) {
                // Reuse of a revoked token revokes every live session for that user in this clinic.
                jdbc.update("""
                    UPDATE auth_refresh_session SET revoked_at=now()
                    WHERE clinic_id=? AND user_id=(SELECT user_id FROM auth_refresh_session WHERE token_hash=?)
                      AND revoked_at IS NULL
                    """, clinic, hash(cookie));
                return null;
            }
            jdbc.update("UPDATE auth_refresh_session SET revoked_at=now() WHERE token_hash=? AND revoked_at IS NULL", hash(cookie));
            return issue(matches.get(0));
        }));
        if (issued == null) throw unauthorized();
        return issued;
    }
    public void revoke(String cookie) {
        if (isPlatformCookie(cookie)) {
            String hash = hash(cookie);
            transactions.execute(status -> {
                jdbc.update("UPDATE platform_admin_refresh_session SET revoked_at=now() WHERE token_hash=? AND revoked_at IS NULL", hash);
                return null;
            });
            return;
        }
        UUID clinic = cookieClinic(cookie);
        TenantContextResult.call(clinic, () -> transactions.execute(status -> {
            jdbc.update("UPDATE auth_refresh_session SET revoked_at=now() WHERE token_hash=? AND revoked_at IS NULL", hash(cookie));
            return null;
        }));
    }
    static UUID cookieClinic(String cookie) {
        if (cookie == null || !cookie.matches("[0-9a-fA-F-]{36}\\.[A-Za-z0-9_-]{43}")) throw unauthorized();
        try { return UUID.fromString(cookie.substring(0,36)); }
        catch (IllegalArgumentException e) { throw unauthorized(); }
    }
    private Issued issuePlatformAdmin(String email) {
        UUID adminId = UUID.randomUUID();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = "pa." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO platform_admin_refresh_session(admin_id,email,token_hash,expires_at) VALUES(?,?,?,?)",
                adminId, email.trim().toLowerCase(java.util.Locale.ROOT), hash(token), java.sql.Timestamp.from(Instant.now().plus(REFRESH_LIFETIME)));
        Instant now = Instant.now();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("dental-clinic-saas").subject(adminId.toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_LIFETIME))
                .claim("email", email.trim().toLowerCase(java.util.Locale.ROOT))
                .claim("role", "PLATFORM_ADMIN").claim("platform_admin", true).build())).getTokenValue();
        return new Issued(new Session(access,
                Map.of("id", adminId, "email", email.trim().toLowerCase(java.util.Locale.ROOT),
                        "fullName", email.trim(), "role", "PLATFORM_ADMIN", "platformAdmin", true), null), token);
    }
    private Issued rotatePlatformAdmin(String cookie) {
        if (cookie == null || !cookie.matches("pa\\.[A-Za-z0-9_-]{43}")) throw unauthorized();
        Issued issued = transactions.execute(status -> {
            String tokenHash = hash(cookie);
            var sessions = jdbc.query("""
                SELECT admin_id,email FROM platform_admin_refresh_session
                WHERE token_hash=? AND revoked_at IS NULL AND expires_at>now() FOR UPDATE
                """, (rs, row) -> new PlatformSession((UUID) rs.getObject(1), rs.getString(2)), tokenHash);
            if (sessions.size() != 1) {
                jdbc.update("""
                    UPDATE platform_admin_refresh_session SET revoked_at=now()
                    WHERE email=(SELECT email FROM platform_admin_refresh_session WHERE token_hash=?) AND revoked_at IS NULL
                    """, tokenHash);
                return null;
            }
            PlatformSession session = sessions.get(0);
            jdbc.update("UPDATE platform_admin_refresh_session SET revoked_at=now() WHERE token_hash=? AND revoked_at IS NULL", tokenHash);
            if (!props.isPlatformAdmin(session.email())) {
                jdbc.update("UPDATE platform_admin_refresh_session SET revoked_at=now() WHERE email=? AND revoked_at IS NULL", session.email());
                return null;
            }
            return issuePlatformAdmin(session.adminId(), session.email());
        });
        if (issued == null) throw unauthorized();
        return issued;
    }
    private Issued issuePlatformAdmin(UUID adminId, String email) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = "pa." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO platform_admin_refresh_session(admin_id,email,token_hash,expires_at) VALUES(?,?,?,?)",
                adminId, email, hash(token), java.sql.Timestamp.from(Instant.now().plus(REFRESH_LIFETIME)));
        Instant now = Instant.now();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("dental-clinic-saas").subject(adminId.toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_LIFETIME)).claim("email", email)
                .claim("role", "PLATFORM_ADMIN").claim("platform_admin", true).build())).getTokenValue();
        return new Issued(new Session(access,
                Map.of("id", adminId, "email", email, "fullName", email, "role", "PLATFORM_ADMIN", "platformAdmin", true), null), token);
    }
    private static boolean isPlatformCookie(String cookie) { return cookie != null && cookie.startsWith("pa."); }
    private record PlatformSession(UUID adminId, String email) {}
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session");
    }
    private static final class TenantContextResult {
        static <T> T call(UUID tenant, java.util.function.Supplier<T> operation) {
            UUID old = TenantContext.get();
            boolean oldPlatformAdmin = TenantContext.isPlatformAdmin();
            TenantContext.set(tenant);
            try { return operation.get(); }
            finally {
                if (oldPlatformAdmin) TenantContext.setPlatformAdmin();
                else if (old==null) TenantContext.clear();
                else TenantContext.set(old);
            }
        }
    }
}
