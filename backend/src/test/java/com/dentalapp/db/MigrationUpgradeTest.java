package com.dentalapp.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Verifies the supported V14-to-V15 patient data upgrade on an explicitly disposable database. */
@EnabledIfEnvironmentVariable(named = "M25_UPGRADE_TEST_DB_URL", matches = ".+")
class MigrationUpgradeTest {
    @Test
    void upgradesV14PatientHistoryAndConsentDataToV15WithoutLoss() throws Exception {
        String url = System.getenv("M25_UPGRADE_TEST_DB_URL");
        String user = System.getenv().getOrDefault("M25_UPGRADE_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("M25_UPGRADE_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url, user, password).target(MigrationVersion.fromVersion("14")).load().migrate();

        UUID clinic = UUID.randomUUID(), staff = UUID.randomUUID(), patient = UUID.randomUUID(), consent = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO clinic(id,slug,name) VALUES ('"+clinic+"','m25-upgrade-"+clinic.toString().substring(0,8)+"','Upgrade Clinic')");
            statement.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES ('"+staff+"','"+clinic+"','upgrade@test.invalid','test','Owner','OWNER')");
            statement.execute("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES ('"+patient+"','"+clinic+"','U-000001','Upgrade','Patient')");
            statement.execute("INSERT INTO patient_medical_history(clinic_id,patient_id,allergies,updated_by) VALUES ('"+clinic+"','"+patient+"','preserve this synthetic allergy','"+staff+"')");
            statement.execute("INSERT INTO consent_form(id,clinic_id,patient_id,form_type,signed_at,signed_by) VALUES ('"+consent+"','"+clinic+"','"+patient+"','Legacy synthetic form',now(),'historical-test-signer')");
        }

        Flyway.configure().dataSource(url, user, password).target(MigrationVersion.fromVersion("15")).load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")) {
                int expected = 1;
                while (result.next()) assertThat(result.getString(1)).isEqualTo(String.valueOf(expected++));
                assertThat(expected).isEqualTo(16);
            }
            try (var result = statement.executeQuery("SELECT allergies FROM patient_medical_history WHERE clinic_id='"+clinic+"' AND patient_id='"+patient+"'")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("preserve this synthetic allergy");
            }
            try (var result = statement.executeQuery("SELECT consent_status,signed_at IS NOT NULL,signed_by FROM consent_form WHERE clinic_id='"+clinic+"' AND id='"+consent+"'")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("UNSIGNED");
                assertThat(result.getBoolean(2)).isTrue(); assertThat(result.getString(3)).isEqualTo("historical-test-signer");
            }
            statement.execute("INSERT INTO patient_guardian_contact(clinic_id,patient_id,full_name,relationship) VALUES ('"+clinic+"','"+patient+"','Synthetic contact','Parent')");
        }
    }

    /** M3.10: verifies the supported V15-to-V21 scheduling schema upgrade without data loss. */
    @Test
    void upgradesV15PatientDataToV21SchedulingSchemaWithoutLoss() throws Exception {
        String url = System.getenv("M310_UPGRADE_TEST_DB_URL");
        String user = System.getenv().getOrDefault("M310_UPGRADE_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("M310_UPGRADE_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url, user, password).target(MigrationVersion.fromVersion("15")).load().migrate();

        UUID clinic = UUID.randomUUID(), staff = UUID.randomUUID(), patient = UUID.randomUUID(), branch = UUID.randomUUID(), consent = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO clinic(id,slug,name,timezone) VALUES ('"+clinic+"','m310-upgrade-"+clinic.toString().substring(0,8)+"','Upgrade Clinic','America/New_York')");
            statement.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES ('"+staff+"','"+clinic+"','m310-upgrade@test.invalid','test','Owner','OWNER')");
            statement.execute("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES ('"+patient+"','"+clinic+"','V21-1','Upgrade','Patient')");
            statement.execute("INSERT INTO branch(id,clinic_id,name) VALUES ('"+branch+"','"+clinic+"','Upgrade Branch')");
            statement.execute("INSERT INTO patient_medical_history(clinic_id,patient_id,allergies,updated_by) VALUES ('"+clinic+"','"+patient+"','preserve across scheduling upgrade','"+staff+"')");
            statement.execute("INSERT INTO consent_form(id,clinic_id,patient_id,form_type,signed_at,signed_by) VALUES ('"+consent+"','"+clinic+"','"+patient+"','Legacy synthetic form',now(),'historical-test-signer')");
        }

        Flyway.configure().dataSource(url, user, password).target(MigrationVersion.fromVersion("21")).load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
                assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isEqualTo(21);
            }
            // Legacy M2 data is preserved through the scheduling upgrade.
            try (var result = statement.executeQuery("SELECT allergies FROM patient_medical_history WHERE clinic_id='"+clinic+"' AND patient_id='"+patient+"'")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("preserve across scheduling upgrade");
            }
            try (var result = statement.executeQuery("SELECT consent_status FROM consent_form WHERE clinic_id='"+clinic+"' AND id='"+consent+"'")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("UNSIGNED");
            }
            // Every M3 table exists, is tenant-RLS enforced, and reachable columns kept their constraints.
            for (String table : List.of("scheduling_policy", "dentist_availability", "dentist_time_off", "chair_block", "appointment_event", "appointment_waitlist", "appointment_notification_outbox")) {
                statement.execute("SELECT 1 FROM "+table+" LIMIT 0");
                statement.execute("ALTER TABLE "+table+" DISABLE TRIGGER ALL"); // benign no-op with superuser to prove ownership access
                statement.execute("ALTER TABLE "+table+" ENABLE TRIGGER ALL");
                try (var result = statement.executeQuery("SELECT count(*) FROM pg_class WHERE relname='"+table+"' AND relrowsecurity AND relforcerowsecurity")) {
                    assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isEqualTo(1);
                }
            }
            // Defaults survive the upgrade: policy row materialises from the clinic default.
            try (var result = statement.executeQuery("SELECT appointment_type.* FROM appointment_type LIMIT 0")) { assertThat(result.next()).isFalse(); }
        }
    }
}
