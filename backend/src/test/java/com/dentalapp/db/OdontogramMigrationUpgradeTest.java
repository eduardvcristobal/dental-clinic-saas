package com.dentalapp.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Run only against a dedicated disposable DB: tests the V21 -> V24 clinical chart upgrade. */
@EnabledIfEnvironmentVariable(named="M4_UPGRADE_TEST_DB_URL",matches=".+")
class OdontogramMigrationUpgradeTest {
 @Test void preservesHistoricalToothRecordsAndAddsImmutableEventSchema() throws Exception {
    String url=System.getenv("M4_UPGRADE_TEST_DB_URL");
    String user=System.getenv().getOrDefault("M4_UPGRADE_TEST_DB_USER","postgres");
    String pwd=System.getenv().getOrDefault("M4_UPGRADE_TEST_DB_PASSWORD","");
    Flyway.configure().dataSource(url,user,pwd).target(MigrationVersion.fromVersion("21")).load().migrate();
    UUID c=UUID.randomUUID(),staff=UUID.randomUUID(),patient=UUID.randomUUID(),legacy=UUID.randomUUID();
    try(var con=DriverManager.getConnection(url,user,pwd);var st=con.createStatement()) {
      st.execute("INSERT INTO clinic(id,slug,name) VALUES ('"+c+"','m4-upgrade-"+c.toString().substring(0,8)+"','Synthetic Clinic')");
      st.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role) VALUES ('"+staff+"','"+c+"','m4-upgrade@test.invalid','test','Test Dentist','DENTIST')");
      st.execute("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES ('"+patient+"','"+c+"','M4-001','Synthetic','Patient')");
      st.execute("INSERT INTO tooth_record(id,clinic_id,patient_id,tooth_number,surface,condition_code,recorded_by) VALUES ('"+legacy+"','"+c+"','"+patient+"',11,'I','CARIES','"+staff+"')");
    }
    Flyway.configure().dataSource(url,user,pwd).target(MigrationVersion.fromVersion("24")).load().migrate();
    try(var con=DriverManager.getConnection(url,user,pwd);var st=con.createStatement()) {
      try(var rs=st.executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")) {
        assertThat(rs.next()).isTrue();assertThat(rs.getString(1)).isEqualTo("24");
      }
      try(var rs=st.executeQuery("SELECT tooth_number,condition_code FROM tooth_record WHERE id='"+legacy+"'")) {
        assertThat(rs.next()).isTrue();assertThat(rs.getInt(1)).isEqualTo(11);
        assertThat(rs.getString(2)).isEqualTo("CARIES");
      }
      st.execute("INSERT INTO odontogram_chart(clinic_id,patient_id,created_by) VALUES ('"+c+"','"+patient+"','"+staff+"')");
      try(var rs=st.executeQuery("SELECT count(*) FROM odontogram_chart WHERE clinic_id='"+c+"' AND patient_id='"+patient+"'")) {
        rs.next();assertThat(rs.getInt(1)).isEqualTo(1);
      }
      try(var rs=st.executeQuery("SELECT odontogram_valid_fdi(11),odontogram_valid_fdi(49),odontogram_valid_surface(11,'I'),odontogram_valid_surface(11,'O')")) {
        rs.next();assertThat(rs.getBoolean(1)).isTrue();assertThat(rs.getBoolean(2)).isFalse();
        assertThat(rs.getBoolean(3)).isTrue();assertThat(rs.getBoolean(4)).isFalse();
      }
    }
 }
}
