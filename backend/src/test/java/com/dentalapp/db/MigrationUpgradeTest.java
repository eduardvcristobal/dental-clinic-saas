package com.dentalapp.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
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
}
