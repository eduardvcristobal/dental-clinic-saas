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
        assertThat(migrations).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
                "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity
                """, Integer.class)).isGreaterThanOrEqualTo(30);
        // M10.3: cover every new tenant table, not a fixed historical count.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns cols
            JOIN pg_namespace n ON n.nspname=cols.table_schema
            JOIN pg_class c ON c.relnamespace=n.oid AND c.relname=cols.table_name
            WHERE cols.table_schema='public' AND cols.column_name='clinic_id'
              AND c.relkind='r' AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity)
            """,Integer.class)).as("Every clinic_id table must enforce FORCE RLS").isZero();
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_roles WHERE rolname='dental_app'
              AND (rolsuper OR rolbypassrls)
            """,Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
            WHERE NOT t.tgisinternal AND t.tgname='m10_audit_immutable' AND c.relname='audit_log'
            """,Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_indexes WHERE schemaname='public'
             AND indexname IN ('idx_m10_invoice_issued','idx_m10_payment_paid',
              'idx_m10_refund_at','idx_m10_appointment_start','idx_m10_patient_registered',
              'idx_m10_plan_item_completed','idx_m10_visit_first_signed','idx_m10_audit_recent')
            """,Integer.class)).isEqualTo(8);
        // M8.10.22: verify notification callbacks and actions remain under FORCE RLS.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_class
            WHERE relname IN ('notification_delivery','notification_consent',
                              'notification_delivery_event','notification_patient_action')
              AND relrowsecurity AND relforcerowsecurity
            """, Integer.class)).isEqualTo(4);
        // Both appointment change and patient consent withdrawal must invalidate claimed work.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_trigger
            WHERE NOT tgisinternal AND tgname IN
              ('notification_consent_withdrawal_sync')
            """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_proc
            WHERE proname IN ('suppress_stale_appointment_reminders',
                             'suppress_withdrawn_notification_consent')
            """, Integer.class)).isEqualTo(2);
        // Verify the actual appointment trigger wiring, not merely that its function exists.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_trigger t
            JOIN pg_class c ON c.oid=t.tgrelid
            WHERE NOT t.tgisinternal AND t.tgname='appointment_reminder_sync'
              AND c.relname='appointment'
            """,Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_trigger t
            JOIN pg_class c ON c.oid=t.tgrelid
            WHERE NOT t.tgisinternal AND t.tgname='notification_consent_withdrawal_sync'
              AND c.relname='notification_consent'
            """,Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT relrowsecurity AND relforcerowsecurity
            FROM pg_class WHERE oid='notification_delivery_attempt'::regclass
            """,Boolean.class)).isTrue();
        // M3 tables must carry forced tenant RLS and restricted-role grants.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity
                  AND c.relname IN ('scheduling_policy','dentist_availability','dentist_time_off','chair_block',
                                    'appointment_event','appointment_waitlist','appointment_notification_outbox')
                """, Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.role_table_grants
                WHERE grantee='dental_app' AND table_name IN ('scheduling_policy','dentist_availability','dentist_time_off',
                                                              'chair_block','appointment_event','appointment_waitlist',
                                                              'appointment_notification_outbox')
                  AND privilege_type='INSERT'
                """, Integer.class)).isEqualTo(7);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity
                  AND c.relname IN ('odontogram_chart','odontogram_finding_event')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_policies
                WHERE schemaname=current_schema()
                  AND tablename IN ('odontogram_chart','odontogram_finding_event') AND policyname='tenant_isolation'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname=current_schema() AND indexname='uq_odontogram_single_supersession'
                """, Integer.class)).isEqualTo(1);
        // M5.2 clinical documentation tables must have FORCE RLS and INSERT-only history grants.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity
              AND c.relname IN ('encounter_soap_draft','encounter_soap_revision','encounter_diagnosis')
            """,Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_policies WHERE schemaname=current_schema()
             AND tablename IN ('encounter_soap_draft','encounter_soap_revision','encounter_diagnosis')
             AND policyname='tenant_isolation'
            """,Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.role_table_grants
            WHERE grantee='dental_app' AND privilege_type='INSERT'
              AND table_name IN ('encounter_soap_draft','encounter_soap_revision','encounter_diagnosis')
            """,Integer.class)).isEqualTo(3);
        // M5.3–M5.4: clinical signed documents and prescriptions are tenant isolated.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity
              AND c.relname IN ('encounter_signed_note','encounter_note_amendment',
                                'encounter_prescription_draft','encounter_document_reference')
            """,Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_policies WHERE schemaname=current_schema()
              AND tablename IN ('encounter_signed_note','encounter_note_amendment',
                                'encounter_prescription_draft','encounter_document_reference')
              AND policyname='tenant_isolation'
            """,Integer.class)).isEqualTo(4);
        // M6 clinical plan history is forced-RLS and has tenant-owned immutable records.
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_class WHERE relname='treatment_plan_event' AND relrowsecurity AND relforcerowsecurity
            """,Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_policies WHERE schemaname=current_schema()
              AND tablename='treatment_plan_event' AND policyname='tenant_isolation'
            """,Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_trigger WHERE tgname IN ('treatment_plan_event_immutable',
              'treatment_plan_item_no_delete','treatment_plan_item_price_lock')
              AND NOT tgisinternal
            """,Integer.class)).isEqualTo(3);
        // A platform-wide session must not bypass tenant RLS for clinical records.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_policies WHERE schemaname=current_schema() AND policyname='platform_admin_access'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_policies WHERE schemaname=current_schema() AND tablename='staff_invitation' AND policyname='tenant_isolation'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_policies WHERE schemaname=current_schema() AND tablename='clinic_admin_event' AND policyname='tenant_isolation'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='patient_guardian_contact'::regclass", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_policies WHERE schemaname=current_schema() AND tablename IN ('patient_medical_history','patient_guardian_contact','consent_form') AND policyname='tenant_isolation'", Integer.class)).isEqualTo(3);

        UUID clinicA = UUID.randomUUID(), clinicB = UUID.randomUUID();
        UUID userA = UUID.randomUUID(), userB = UUID.randomUUID();
        UUID patientA = UUID.randomUUID(), patientB = UUID.randomUUID();
        UUID guardianA = UUID.randomUUID(), guardianB = UUID.randomUUID();
        UUID consentA = UUID.randomUUID(), consentB = UUID.randomUUID();
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            s.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO dental_app");
            s.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO dental_app");
            s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO dental_app");
            s.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO dental_app");
            s.execute("INSERT INTO clinic(id,slug,name) VALUES ('"+clinicA+"','m04-a','Clinic A'),('"+clinicB+"','m04-b','Clinic B')");
            s.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES ('"+userA+"','"+clinicA+"','a@test.invalid','x','User A','OWNER'),('"+userB+"','"+clinicB+"','b@test.invalid','x','User B','OWNER')");
            s.execute("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES ('"+patientA+"','"+clinicA+"','A1','Patient','A'),('"+patientB+"','"+clinicB+"','B1','Patient','B')");
            s.execute("INSERT INTO patient_medical_history(clinic_id,patient_id,allergies,updated_by) VALUES ('"+clinicA+"','"+patientA+"','Synthetic allergy A','"+userA+"'),('"+clinicB+"','"+patientB+"','Synthetic allergy B','"+userB+"')");
            s.execute("INSERT INTO patient_guardian_contact(id,clinic_id,patient_id,full_name,relationship,is_primary) VALUES ('"+guardianA+"','"+clinicA+"','"+patientA+"','Guardian A','Parent',true),('"+guardianB+"','"+clinicB+"','"+patientB+"','Guardian B','Parent',true)");
            s.execute("INSERT INTO consent_form(id,clinic_id,patient_id,form_type) VALUES ('"+consentA+"','"+clinicA+"','"+patientA+"','Synthetic form A'),('"+consentB+"','"+clinicB+"','"+patientB+"','Synthetic form B')");
        }

        // M8.11.6: two independent database sessions atomically increment the same tenant bucket.
        // The conflict update must not lose counts when replicas contend.
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try (Connection first=appConnection(); Connection second=appConnection()) {
            try (var st=first.createStatement()) {st.execute("SELECT set_config('app.clinic_id','"+clinicA+"',false)");}
            try (var st=second.createStatement()) {st.execute("SELECT set_config('app.clinic_id','"+clinicA+"',false)");}
            java.util.concurrent.Callable<Void> incrementsA=()->{
                for(int k=0;k<20;k++)try(var st=first.createStatement()) {
                    st.executeUpdate("INSERT INTO notification_redemption_limit(clinic_id,bucket_start,attempts) VALUES ('"+clinicA+"','2000-01-01T00:00:00Z',1) ON CONFLICT(clinic_id,bucket_start) DO UPDATE SET attempts=notification_redemption_limit.attempts+1");
                }
                return null;
            };
            java.util.concurrent.Callable<Void> incrementsB=()->{
                for(int k=0;k<20;k++)try(var st=second.createStatement()) {
                    st.executeUpdate("INSERT INTO notification_redemption_limit(clinic_id,bucket_start,attempts) VALUES ('"+clinicA+"','2000-01-01T00:00:00Z',1) ON CONFLICT(clinic_id,bucket_start) DO UPDATE SET attempts=notification_redemption_limit.attempts+1");
                }
                return null;
            };
            var one=pool.submit(incrementsA);
            var two=pool.submit(incrementsB);
            one.get();two.get();
            try(var st=first.createStatement();var rs=st.executeQuery("SELECT attempts FROM notification_redemption_limit WHERE clinic_id='"+clinicA+"' AND bucket_start='2000-01-01T00:00:00Z'")) {
                rs.next();assertThat(rs.getInt(1)).isEqualTo(40);
            }
        } finally {pool.shutdownNow();}
        try (Connection app = appConnection()) {
            app.setAutoCommit(false);
            try (var s = app.createStatement()) {
                s.execute("SELECT set_config('app.clinic_id','"+clinicA+"',false)");
                // Shared throttling must be atomic and invisible to other tenants.
                s.execute("INSERT INTO notification_redemption_limit(clinic_id,bucket_start,attempts) VALUES ('"+clinicA+"',date_trunc('minute',now()),1)");
                s.execute("INSERT INTO notification_redemption_limit(clinic_id,bucket_start,attempts) VALUES ('"+clinicA+"',date_trunc('minute',now()),1) ON CONFLICT(clinic_id,bucket_start) DO UPDATE SET attempts=notification_redemption_limit.attempts+1");
                try (var rs=s.executeQuery("SELECT attempts FROM notification_redemption_limit WHERE clinic_id='"+clinicA+"' AND bucket_start=date_trunc('minute',now())")) {
                    rs.next(); assertThat(rs.getInt(1)).isEqualTo(2);
                }
                assertSqlRejected(app,s,"INSERT INTO notification_redemption_limit(clinic_id,bucket_start,attempts) VALUES ('"+clinicB+"',date_trunc('minute',now()),1)");
                try (var rs=s.executeQuery("SELECT count(*) FROM notification_redemption_limit WHERE clinic_id='"+clinicB+"'")) {
                    rs.next(); assertThat(rs.getInt(1)).isZero();
                }
                try (var rs = s.executeQuery("SELECT count(*) FROM patient")) { rs.next(); assertThat(rs.getInt(1)).isEqualTo(1); }
                s.execute("UPDATE patient SET first_name='changed' WHERE clinic_id='"+clinicB+"'");
                try (var rs = s.executeQuery("SELECT count(*) FROM patient WHERE clinic_id='"+clinicB+"'")) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
                s.execute("DELETE FROM patient WHERE clinic_id='"+clinicB+"'");
                try (var rs = s.executeQuery("SELECT clinic_id FROM patient LIMIT 1")) { rs.next(); assertThat(rs.getObject(1, UUID.class)).isEqualTo(clinicA); }
                assertSqlRejected(app, s, "INSERT INTO patient(clinic_id,chart_no,first_name,last_name) VALUES ('"+clinicB+"','spoof','Spoof','Patient')");
                try (var rs=s.executeQuery("SELECT count(*) FROM patient_medical_history WHERE patient_id='"+patientB+"'")) {rs.next();assertThat(rs.getInt(1)).isZero();}
                try (var rs=s.executeQuery("SELECT count(*) FROM patient_guardian_contact WHERE id='"+guardianB+"'")) {rs.next();assertThat(rs.getInt(1)).isZero();}
                try (var rs=s.executeQuery("SELECT count(*) FROM consent_form WHERE id='"+consentB+"'")) {rs.next();assertThat(rs.getInt(1)).isZero();}
                assertThat(s.executeUpdate("UPDATE patient_medical_history SET allergies='spoof' WHERE clinic_id='"+clinicB+"'")).isZero();
                assertThat(s.executeUpdate("DELETE FROM patient_guardian_contact WHERE clinic_id='"+clinicB+"'")).isZero();
                assertThat(s.executeUpdate("UPDATE consent_form SET form_type='spoof' WHERE clinic_id='"+clinicB+"'")).isZero();
                assertSqlRejected(app, s, "INSERT INTO patient_guardian_contact(clinic_id,patient_id,full_name,relationship) VALUES ('"+clinicA+"','"+patientB+"','Cross Clinic','Parent')");
                assertSqlRejected(app, s, "INSERT INTO patient_medical_history(clinic_id,patient_id,updated_by) VALUES ('"+clinicA+"','"+patientB+"','"+userA+"')");
                assertSqlRejected(app, s, "INSERT INTO consent_form(clinic_id,patient_id,form_type) VALUES ('"+clinicA+"','"+patientB+"','Cross Clinic')");
            }
            app.rollback();
        }
        // Invitation isolation and one-time claim must be enforced by the database, not just the UI.
        UUID branchA=UUID.randomUUID(), branchB=UUID.randomUUID(), inviterA=UUID.randomUUID();
        UUID invitationA=UUID.randomUUID(), invitationB=UUID.randomUUID();
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            s.execute("INSERT INTO branch(id,clinic_id,name) VALUES ('"+branchA+"','"+clinicA+"','Main A'),('"+branchB+"','"+clinicB+"','Main B')");
            s.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES ('"+inviterA+"','"+clinicA+"','invite-a@test.invalid','x','Inviter A','OWNER')");
            s.execute("INSERT INTO staff_invitation(id,clinic_id,email,role,branch_id,token_hash,invited_by,expires_at) VALUES " +
                "('"+invitationA+"','"+clinicA+"','candidate-a@test.invalid','DENTIST','"+branchA+"','test-hash-a','"+inviterA+"',now()+interval '1 day')");
            assertThrows(SQLException.class, () -> s.execute("INSERT INTO staff_invitation(id,clinic_id,email,role,branch_id,token_hash,invited_by,expires_at) VALUES " +
                "('"+invitationB+"','"+clinicA+"','cross@test.invalid','DENTIST','"+branchB+"','test-hash-b','"+inviterA+"',now()+interval '1 day')"));
        }
        try (Connection app = appConnection(); var s = app.createStatement()) {
            s.execute("SELECT set_config('app.clinic_id','"+clinicA+"',false)");
            try (var rs=s.executeQuery("SELECT count(*) FROM staff_invitation")) {rs.next(); assertThat(rs.getInt(1)).isEqualTo(1);}
            assertThat(s.executeUpdate("UPDATE staff_invitation SET revoked_at=now() WHERE id='"+invitationB+"'")).isZero();
            // Single-use claim is expressed as an atomic conditional UPDATE.
            assertThat(s.executeUpdate("UPDATE staff_invitation SET accepted_at=now() WHERE id='"+invitationA+"' AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at>now()")) .isEqualTo(1);
            assertThat(s.executeUpdate("UPDATE staff_invitation SET accepted_at=now() WHERE id='"+invitationA+"' AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at>now()")) .isZero();
            assertThat(s.executeUpdate("UPDATE staff_invitation SET revoked_at=now() WHERE id='"+invitationA+"' AND accepted_at IS NULL")) .isZero();
        }
        try (Connection app = appConnection(); var s = app.createStatement()) {
            s.execute("SELECT set_config('app.clinic_id','"+clinicB+"',false)");
            try (var rs=s.executeQuery("SELECT count(*) FROM staff_invitation")) {rs.next(); assertThat(rs.getInt(1)).isZero();}
            assertThat(s.executeUpdate("UPDATE staff_invitation SET revoked_at=now() WHERE id='"+invitationA+"'")) .isZero();
        }
        // M4: clinic-scoped odontogram, immutable events and single-supersession rule.
        UUID chartA=UUID.randomUUID(), chartB=UUID.randomUUID(), findingA=UUID.randomUUID();
        try (Connection owner = ownerConnection(); var st = owner.createStatement()) {
            st.execute("INSERT INTO odontogram_chart(id,clinic_id,patient_id,created_by) VALUES " +
                "('"+chartA+"','"+clinicA+"','"+patientA+"','"+userA+"'),('"+chartB+"','"+clinicB+"','"+patientB+"','"+userB+"')");
            st.execute("INSERT INTO odontogram_finding_event(id,clinic_id,chart_id,patient_id,tooth_number,surface,condition_code,event_kind,recorded_by) VALUES " +
                "('"+findingA+"','"+clinicA+"','"+chartA+"','"+patientA+"',11,'I','CARIES','OBSERVATION','"+userA+"')");
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO odontogram_finding_event(clinic_id,chart_id,patient_id,tooth_number,condition_code,event_kind,recorded_by) VALUES " +
                "('"+clinicA+"','"+chartA+"','"+patientB+"',11,'CARIES','OBSERVATION','"+userA+"')"));
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO odontogram_finding_event(clinic_id,chart_id,patient_id,tooth_number,surface,condition_code,event_kind,recorded_by) VALUES " +
                "('"+clinicA+"','"+chartA+"','"+patientA+"',19,'O','CARIES','OBSERVATION','"+userA+"')"));
        }
        try (Connection app = appConnection()) {
            app.setAutoCommit(false);
            try (var st = app.createStatement()) {
                st.execute("SELECT set_config('app.clinic_id','"+clinicA+"',false)");
                try (var rs=st.executeQuery("SELECT count(*) FROM odontogram_chart")) {
                    rs.next(); assertThat(rs.getInt(1)).isEqualTo(1);
                }
                try (var rs=st.executeQuery("SELECT count(*) FROM odontogram_finding_event")) {
                    rs.next(); assertThat(rs.getInt(1)).isEqualTo(1);
                }
                assertSqlRejected(app,st,"INSERT INTO odontogram_chart(clinic_id,patient_id) VALUES ('"+clinicB+"','"+patientB+"')");
                assertSqlRejected(app,st,"INSERT INTO odontogram_chart(clinic_id,patient_id) VALUES ('"+clinicA+"','"+patientB+"')");
                assertSqlRejected(app,st,"DELETE FROM odontogram_finding_event WHERE id='"+findingA+"'");
                assertSqlRejected(app,st,"UPDATE odontogram_finding_event SET notes='forged' WHERE id='"+findingA+"'");
                UUID correction=UUID.randomUUID();
                st.execute("INSERT INTO odontogram_finding_event(id,clinic_id,chart_id,patient_id,tooth_number,surface,condition_code,event_kind,recorded_by,supersedes_event_id) VALUES " +
                    "('"+correction+"','"+clinicA+"','"+chartA+"','"+patientA+"',11,'I','RESTORATION','CORRECTION','"+userA+"','"+findingA+"')");
                assertSqlRejected(app,st,"INSERT INTO odontogram_finding_event(clinic_id,chart_id,patient_id,tooth_number,surface,condition_code,event_kind,recorded_by,supersedes_event_id) VALUES " +
                    "('"+clinicA+"','"+chartA+"','"+patientA+"',12,'I','CROWN','CORRECTION','"+userA+"','"+correction+"')");
                assertSqlRejected(app,st,"INSERT INTO odontogram_finding_event(clinic_id,chart_id,patient_id,tooth_number,surface,condition_code,event_kind,recorded_by,supersedes_event_id) VALUES " +
                    "('"+clinicA+"','"+chartA+"','"+patientA+"',11,'I','CROWN','CORRECTION','"+userA+"','"+findingA+"')");
            }
            app.rollback();
        }
        // Earlier V11's global permission must no longer reveal patient rows.
        try (Connection app = appConnection(); var s = app.createStatement()) {
            s.execute("SELECT set_config('app.platform_admin','true',false)");
            try (var rs = s.executeQuery("SELECT count(*) FROM patient")) {
                rs.next(); assertThat(rs.getInt(1)).isZero();
            }
            try (var rs = s.executeQuery("SELECT count(*) FROM app_user")) {
                rs.next(); assertThat(rs.getInt(1)).isZero();
            }
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

    private void assertSqlRejected(Connection connection, java.sql.Statement statement, String sql) throws SQLException {
        var savepoint = connection.setSavepoint();
        assertThrows(SQLException.class, () -> statement.execute(sql));
        connection.rollback(savepoint);
        connection.releaseSavepoint(savepoint);
    }
}
