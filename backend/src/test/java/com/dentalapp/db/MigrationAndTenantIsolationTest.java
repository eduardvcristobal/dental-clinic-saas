package com.dentalapp.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dentalapp.tenancy.TenantContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Uses an explicitly created disposable PostgreSQL database supplied by M04_MIGRATION_TEST_DB_URL. */
@EnabledIfEnvironmentVariable(named = "M04_MIGRATION_TEST_DB_URL", matches = ".+")
@SpringBootTest
class MigrationAndTenantIsolationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("M04_MIGRATION_TEST_DB_URL");
        String ownerUser = System.getenv().getOrDefault("M04_MIGRATION_TEST_OWNER_USER", "postgres");
        String ownerPassword = System.getenv("M04_MIGRATION_TEST_OWNER_PASSWORD");
        String appPassword = System.getenv("M04_MIGRATION_TEST_APP_PASSWORD");
        try (Connection owner = DriverManager.getConnection(url, ownerUser, ownerPassword);
             var statement = owner.createStatement()) {
            boolean roleExists;
            try (var roles = statement.executeQuery("SELECT rolsuper,rolbypassrls FROM pg_roles WHERE rolname='dental_app'")) {
                roleExists = roles.next();
                if (roleExists && (roles.getBoolean(1) || roles.getBoolean(2)))
                    throw new IllegalStateException("dental_app test role must be NOSUPERUSER NOBYPASSRLS");
            }
            if (!roleExists)
                statement.execute("CREATE ROLE dental_app LOGIN PASSWORD '" + appPassword.replace("'", "''") + "' NOSUPERUSER NOBYPASSRLS");
            statement.execute("GRANT USAGE ON SCHEMA public TO dental_app");
        } catch (SQLException e) { throw new IllegalStateException("Could not provision restricted migration-test role", e); }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "dental_app");
        properties.add("spring.datasource.password", () -> appPassword);
        properties.add("spring.flyway.url", () -> url);
        properties.add("spring.flyway.user", () -> ownerUser);
        properties.add("spring.flyway.password", () -> ownerPassword);
        properties.add("app.auth.jwt-secret", () -> "m04-migration-test-jwt-secret-is-at-least-32-bytes");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void migratesFreshSchemaAndEnforcesRlsAndTenantRelationshipsForRuntimeRole() throws Exception {
        assertThat(jdbc.queryForObject("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user", Boolean.class)).isFalse();
        var migrations = jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(migrations).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity
                """, Integer.class)).isGreaterThanOrEqualTo(25);

        UUID clinicA = UUID.randomUUID(), clinicB = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            s.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO dental_app");
            s.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO dental_app");
            s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO dental_app");
            s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO dental_app");
            s.execute("INSERT INTO clinic(id,slug,name) VALUES ('"+clinicA+"','m04-a','Clinic A'),('"+clinicB+"','m04-b','Clinic B')");
            s.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES (gen_random_uuid(),'"+clinicA+"','a@test.invalid','x','User A','OWNER'),('"+userB+"','"+clinicB+"','b@test.invalid','x','User B','OWNER')");
            s.execute("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES (gen_random_uuid(),'"+clinicA+"','A1','Patient','A'),(gen_random_uuid(),'"+clinicB+"','B1','Patient','B')");
        }

        try (Connection app = appConnection()) {
            app.setAutoCommit(false);
            try (var s = app.createStatement()) {
                s.execute("SELECT set_config('app.clinic_id','"+clinicA+"',false)");
                try (var rs = s.executeQuery("SELECT count(*) FROM patient")) { rs.next(); assertThat(rs.getInt(1)).isEqualTo(1); }
                s.execute("UPDATE patient SET first_name='changed' WHERE clinic_id='"+clinicB+"'");
                try (var rs = s.executeQuery("SELECT count(*) FROM patient WHERE clinic_id='"+clinicB+"'")) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
                s.execute("DELETE FROM patient WHERE clinic_id='"+clinicB+"'");
                try (var rs = s.executeQuery("SELECT clinic_id FROM patient LIMIT 1")) { rs.next(); assertThat(rs.getObject(1, UUID.class)).isEqualTo(clinicA); }
                assertThrows(SQLException.class, () -> s.execute("INSERT INTO patient(clinic_id,chart_no,first_name,last_name) VALUES ('"+clinicB+"','spoof','Spoof','Patient')"));
            }
            app.rollback();
        }
        TenantContext.set(clinicA);
        try (Connection pooled = dataSource.getConnection(); var s = pooled.createStatement(); var rs = s.executeQuery("SELECT count(*) FROM patient")) {
            rs.next(); assertThat(rs.getInt(1)).isEqualTo(1);
        } finally { TenantContext.clear(); }
        try (Connection pooled = dataSource.getConnection(); var s = pooled.createStatement(); var rs = s.executeQuery("SELECT count(*) FROM patient")) {
            rs.next(); assertThat(rs.getInt(1)).isZero();
        }
        try (Connection app = appConnection(); var s = app.createStatement(); var rs = s.executeQuery("SELECT count(*) FROM patient")) {
            rs.next(); assertThat(rs.getInt(1)).isZero();
        }
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            assertThrows(SQLException.class, () -> s.execute("INSERT INTO auth_refresh_session(clinic_id,user_id,token_hash,expires_at) VALUES ('"+clinicA+"','"+userB+"','cross-clinic-test',now()+interval '1 day')"));
        }
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(System.getenv("M04_MIGRATION_TEST_DB_URL"),
                System.getenv().getOrDefault("M04_MIGRATION_TEST_OWNER_USER", "postgres"),
                System.getenv("M04_MIGRATION_TEST_OWNER_PASSWORD"));
    }

    private Connection appConnection() throws SQLException {
        return DriverManager.getConnection(System.getenv("M04_MIGRATION_TEST_DB_URL"), "dental_app",
                System.getenv("M04_MIGRATION_TEST_APP_PASSWORD"));
    }
}
