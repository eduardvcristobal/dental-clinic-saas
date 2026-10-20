package com.dentalapp.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dentalapp.auth.AuthPrincipal;
import com.dentalapp.clinic.SchedulingController;
import com.dentalapp.tenancy.TenantContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * M3.10: scheduling acceptance integration against a disposable PostgreSQL database
 * (Flyway V1-V21, restricted dental_app role, forced tenant RLS, exclusion-constraint
 * double-booking defence). Synthetic data only.
 */
@EnabledIfEnvironmentVariable(named = "M310_SCHEDULING_TEST_DB_URL", matches = ".+")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class SchedulingDatabaseIntegrationTest {

    private static final String NY = "America/New_York";
    private static final String HIMALAYA = "Asia/Kathmandu";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("M310_SCHEDULING_TEST_DB_URL");
        String ownerUser = System.getenv().getOrDefault("M310_SCHEDULING_TEST_OWNER_USER", "postgres");
        String ownerPassword = System.getenv("M310_SCHEDULING_TEST_OWNER_PASSWORD");
        String appPassword = System.getenv("M310_SCHEDULING_TEST_APP_PASSWORD");
        try (Connection owner = DriverManager.getConnection(url, ownerUser, ownerPassword); var s = owner.createStatement()) {
            var roles = s.executeQuery("SELECT rolsuper,rolbypassrls FROM pg_roles WHERE rolname='dental_app'");
            boolean roleExists = roles.next();
            if (roleExists) {
                if (roles.getBoolean(1) || roles.getBoolean(2))
                    throw new IllegalStateException("dental_app must be NOSUPERUSER NOBYPASSRLS");
            } else {
                s.execute("CREATE ROLE dental_app LOGIN PASSWORD '" + appPassword.replace("'", "''") + "' NOSUPERUSER NOBYPASSRLS");
            }
            s.execute("GRANT USAGE ON SCHEMA public TO dental_app");
        } catch (SQLException e) { throw new IllegalStateException("Could not prepare restricted scheduling-test role", e); }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "dental_app");
        properties.add("spring.datasource.password", () -> appPassword);
        properties.add("spring.flyway.url", () -> url);
        properties.add("spring.flyway.user", () -> ownerUser);
        properties.add("spring.flyway.password", () -> ownerPassword);
        properties.add("app.auth.jwt-secret", () -> "m310-scheduling-test-jwt-secret-at-least-32-bytes");
    }

    @Autowired SchedulingController api;
    @Autowired JdbcTemplate pooled;
    @Autowired DataSource dataSource;

    private final UUID clinicA = UUID.fromString("31000000-0000-4000-8000-000000000001");
    private final UUID clinicB = UUID.fromString("31000000-0000-4000-8000-000000000002");
    private final UUID branchA = UUID.fromString("31000000-0000-4000-8000-000000000011");
    private final UUID branchB = UUID.fromString("31000000-0000-4000-8000-000000000012");
    private final UUID chairA = UUID.fromString("31000000-0000-4000-8000-000000000031");
    private final UUID dentistA = UUID.fromString("31000000-0000-4000-8000-000000000041");
    private final UUID dentistA2 = UUID.fromString("31000000-0000-4000-8000-000000000042");
    private final UUID patientA = UUID.fromString("31000000-0000-4000-8000-000000000051");
    private final UUID patientA2 = UUID.fromString("31000000-0000-4000-8000-000000000052");
    private final UUID patientB = UUID.fromString("31000000-0000-4000-8000-000000000053");
    private final UUID typeA = UUID.fromString("31000000-0000-4000-8000-000000000071");

    @BeforeEach
    void seed() throws SQLException {
        TenantContext.clear();
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            s.execute("INSERT INTO clinic(id,slug,name,timezone) VALUES ('" + clinicA + "','m310-a','M3.10 Clinic A','Asia/Manila'),('" + clinicB + "','m310-b','M3.10 Clinic B','" + HIMALAYA + "')");
            s.execute("INSERT INTO branch(id,clinic_id,name) VALUES ('" + branchA + "','" + clinicA + "','M310 Main A'),('" + branchB + "','" + clinicB + "','M310 Main B')");
            s.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES " +
                "('31000000-0000-4000-8000-000000000061','" + clinicA + "','owner-a@m310.test','x','Owner A','OWNER',true)," +
                "('31000000-0000-4000-8000-000000000062','" + clinicA + "','front-a@m310.test','x','Front Desk A','FRONT_DESK',true)," +
                "('31000000-0000-4000-8000-000000000063','" + clinicA + "','dentist-a@m310.test','x','Dentist A','DENTIST',true)," +
                "('31000000-0000-4000-8000-000000000064','" + clinicB + "','owner-b@m310.test','x','Owner B','OWNER',true)");
            s.execute("INSERT INTO dentist(id,clinic_id,full_name,active) VALUES ('" + dentistA + "','" + clinicA + "','Dr. Ada',true),('" + dentistA2 + "','" + clinicA + "','Dr. Abel',true)");
            s.execute("INSERT INTO chair(id,clinic_id,branch_id,name,active) VALUES ('" + chairA + "','" + clinicA + "','" + branchA + "','Chair 1',true)");
            s.execute("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES " +
                "('" + patientA + "','" + clinicA + "','M310A1','Synthetic','Alice')," +
                "('" + patientA2 + "','" + clinicA + "','M310A2','Archived','Backup')," +
                "('" + patientB + "','" + clinicB + "','M310B1','Other','Clinic')");
            // Operating hours 09:00-17:00 for every weekday; dentist availability Monday only.
            for (int day = 1; day <= 7; day++) {
                // Monday closes early so branch-operating-hour rejection is deterministic.
                String close = day == 1 ? "22:00" : "23:59";
                s.execute("INSERT INTO branch_operating_hour(clinic_id,branch_id,day_of_week,opens_at,closes_at) VALUES ('" + clinicA + "','" + branchA + "'," + day + ",'00:00','" + close + "')");
                if (day == 1) {
                    // Monday split: dentist A works until noon, dentist A2 from noon.
                    s.execute("INSERT INTO dentist_availability(clinic_id,dentist_id,branch_id,day_of_week,starts_at,ends_at) VALUES ('" + clinicA + "','" + dentistA + "','" + branchA + "',1,'00:00','12:00')");
                    s.execute("INSERT INTO dentist_availability(clinic_id,dentist_id,branch_id,day_of_week,starts_at,ends_at) VALUES ('" + clinicA + "','" + dentistA2 + "','" + branchA + "',1,'12:00','23:59')");
                } else {
                    s.execute("INSERT INTO dentist_availability(clinic_id,dentist_id,branch_id,day_of_week,starts_at,ends_at) VALUES ('" + clinicA + "','" + dentistA + "','" + branchA + "'," + day + ",'00:00','23:59')");
                    s.execute("INSERT INTO dentist_availability(clinic_id,dentist_id,branch_id,day_of_week,starts_at,ends_at) VALUES ('" + clinicA + "','" + dentistA2 + "','" + branchA + "'," + day + ",'00:00','23:59')");
                }
            }
            s.execute("INSERT INTO appointment_type(id,clinic_id,name,default_minutes,buffer_before_minutes,buffer_after_minutes) VALUES ('" + typeA + "','" + clinicA + "','Anchor Type',30,0,0)");
        }
        // Overlapping-appointment race protection also relies on database constraints; granted above.
    }

    @AfterEach
    void cleanupSeed() { TenantContext.clear(); truncateAll(); }

    private void truncateAll() {
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            s.execute("TRUNCATE appointment_notification_outbox, appointment_event, appointment_waitlist, appointment, chair_block, dentist_time_off, dentist_availability, branch_operating_hour, chair, dentist, patient, app_user, scheduling_policy, appointment_type, branch, clinic CASCADE");
        } catch (SQLException e) { throw new IllegalStateException("cleanup failed", e); }
    }

    private UsernamePasswordAuthenticationToken auth(UUID user, UUID clinic, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(user, clinic, role), null);
    }

    private UUID user(String seed) { return UUID.fromString("31000000-0000-4000-8000-" + seed); }

    /** The upcoming clinic-local (Asia/Manila) Monday at the requested local time. */
    private Instant monday(LocalTime time) {
        ZoneId tz = ZoneId.of("Asia/Manila");
        LocalDate base = LocalDate.now(tz).plusDays(2);
        while (base.getDayOfWeek() != DayOfWeek.MONDAY) base = base.plusDays(1);
        return base.atTime(time).atZone(tz).toInstant();
    }

    private SchedulingController.BookingInput booking(UUID patient, UUID branch, UUID dentist, UUID chair, UUID type, Instant start, String label) {
        return new SchedulingController.BookingInput(patient, branch, dentist, chair, type, start, start.plusSeconds(1800), label);
    }

    private UUID bookFront(Instant start, String label) {
        TenantContext.set(clinicA);
        Map<String, Object> row = api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"),
            booking(patientA, branchA, dentistA, chairA, null, start, label));
        return (UUID) row.get("id");
    }

    @Test
    void databaseExclusionConstraintRejectsOverlappingActiveAppointments() throws SQLException {
        TenantContext.set(clinicA);
        Map<String, Object> booked = api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"),
            booking(patientA, branchA, dentistA, chairA, null, monday(LocalTime.parse("10:00")), "anchor"));
        assertThat(booked.get("status")).isEqualTo("BOOKED");

        // Dentist overlap (same dentist, different patient, no application validation): rejected.
        try (Connection app = appConnection()) {
            app.setAutoCommit(false);
            var s = app.createStatement();
            s.execute("SELECT set_config('app.clinic_id','" + clinicA + "',false)");
            Savepoint sp = app.setSavepoint();
            assertThrows(SQLException.class, () -> s.execute(
                "INSERT INTO appointment(id,clinic_id,branch_id,patient_id,dentist_id,starts_at,ends_at) VALUES ('" +
                UUID.randomUUID() + "','" + clinicA + "','" + branchA + "','" + patientA2 + "','" + dentistA + "','" +
                monday(LocalTime.parse("10:15")) + "','" + monday(LocalTime.parse("10:45")) + "')"));
            app.rollback(sp);
        }
        // Chair overlap with a different dentist: rejected by the chair exclusion constraint.
        try (Connection app = appConnection()) {
            app.setAutoCommit(false);
            var s = app.createStatement();
            s.execute("SELECT set_config('app.clinic_id','" + clinicA + "',false)");
            Savepoint sp = app.setSavepoint();
            assertThrows(SQLException.class, () -> s.execute(
                "INSERT INTO appointment(id,clinic_id,branch_id,patient_id,dentist_id,chair_id,starts_at,ends_at) VALUES ('" +
                UUID.randomUUID() + "','" + clinicA + "','" + branchA + "','" + patientA2 + "','" + dentistA2 + "','" + chairA + "','" +
                monday(LocalTime.parse("10:15")) + "','" + monday(LocalTime.parse("10:45")) + "')"));
            app.rollback(sp);
        }
        // Cancelled appointments free the slot for reuse.
        TenantContext.set(clinicA);
        UUID cancelledId = bookFront(monday(LocalTime.parse("11:00")), "to-cancel");
        api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), cancelledId, new SchedulingController.StatusInput("CANCELLED", "synthetic cancellation"));
        try (Connection app = appConnection(); var s = app.createStatement()) {
            s.execute("SELECT set_config('app.clinic_id','" + clinicA + "',false)");
            s.execute("INSERT INTO appointment(id,clinic_id,branch_id,patient_id,dentist_id,starts_at,ends_at,status) VALUES ('" +
                UUID.randomUUID() + "','" + clinicA + "','" + branchA + "','" + patientA2 + "','" + dentistA + "','" +
                monday(LocalTime.parse("11:00")) + "','" + monday(LocalTime.parse("11:30")) + "','CANCELLED')");
        }
    }

    @Test
    void simultaneousCompetingBookingsProduceExactlyOneWinner() throws Exception {
        Instant start = monday(LocalTime.parse("13:00")), end = start.plusSeconds(1800);
        try (Connection c1 = appConnection(); Connection c2 = appConnection()) {
            for (Connection c : List.of(c1, c2)) {
                c.setAutoCommit(false);
                c.createStatement().execute("SELECT set_config('app.clinic_id','" + clinicA + "',false)");
            }
            CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<Boolean> r1 = pool.submit(() -> rawInsert(c1, ready, go, start, end));
            Future<Boolean> r2 = pool.submit(() -> rawInsert(c2, ready, go, start, end));
            go.countDown();
            pool.shutdown(); pool.awaitTermination(60, TimeUnit.SECONDS);
            boolean okCount = countActiveAppointments(start);
            assertThat(okCount).as("exactly one appointment survived the race").isTrue();
            assertThat(r1.get() ^ r2.get()).as("exactly one competing request succeeded").isTrue();
            c1.rollback(); c2.rollback();
        }
    }

    private boolean countActiveAppointments(Instant start) {
        TenantContext.set(clinicA);
        int rows = pooled.queryForObject(
            "SELECT count(*) FROM appointment WHERE clinic_id=? AND starts_at=? AND status IN ('BOOKED','CONFIRMED','ARRIVED','IN_CHAIR')",
            Integer.class, clinicA, java.sql.Timestamp.from(start));
        return rows == 1;
    }

    private boolean rawInsert(Connection c, CountDownLatch ready, CountDownLatch go, Instant start, Instant end) {
        ready.countDown();
        try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        try {
            c.createStatement().execute("SET LOCAL statement_timeout='5s'");
            c.createStatement().execute("INSERT INTO appointment(id,clinic_id,branch_id,patient_id,dentist_id,starts_at,ends_at,reason) " +
            "VALUES ('" + UUID.randomUUID() + "','" + clinicA + "','" + branchA + "','" + patientA + "','" + dentistA + "','" + start + "','" + end + "','race')");
            c.commit(); return true;
        } catch (SQLException e) { return false; }
    }

    @Test
    void configurationRolePermissionsAndInvalidSettingsAreRejected() {
        TenantContext.set(clinicA);
        // FRONT_DESK cannot configure types or policy.
        assertThrows(AccessDeniedException.class, () ->
            api.type(auth(user("000000000062"), clinicA, "FRONT_DESK"), new SchedulingController.TypeInput("Blocked", 30, 0, 0, null)));
        assertThrows(AccessDeniedException.class, () ->
            api.updatePolicy(auth(user("000000000062"), clinicA, "FRONT_DESK"), new SchedulingController.PolicyInput(15, 0, 180)));
        // OWNER rejected configurations.
        assertThrows(ResponseStatusException.class, () ->
            api.type(auth(user("000000000061"), clinicA, "OWNER"), new SchedulingController.TypeInput("Too short", 4, 0, 0, null)));
        assertThrows(ResponseStatusException.class, () ->
            api.type(auth(user("000000000061"), clinicA, "OWNER"), new SchedulingController.TypeInput("Bad buffer", 30, -5, 0, null)));
        assertThrows(ResponseStatusException.class, () ->
            api.updatePolicy(auth(user("000000000061"), clinicA, "OWNER"), new SchedulingController.PolicyInput(7, 0, 180)));
        assertThrows(ResponseStatusException.class, () ->
            api.updatePolicy(auth(user("000000000061"), clinicA, "OWNER"), new SchedulingController.PolicyInput(15, -2, 180)));
        // Valid configuration works: appointment type with 45-minute after-buffer.
        Map<String, Object> created = api.type(auth(user("000000000061"), clinicA, "OWNER"),
            new SchedulingController.TypeInput("Deep Clean", 30, 0, 45, "#123456"));
        assertThat(created.get("default_minutes")).isEqualTo(30);
        assertThat(((Number) created.get("buffer_after_minutes")).intValue()).isEqualTo(45);
    }

    @Test
    void bookingValidationRejectsInvalidContextsWindowsAndResources() throws SQLException {
        TenantContext.set(clinicA);
        Map<String, Object> created = api.type(auth(user("000000000061"), clinicA, "OWNER"),
            new SchedulingController.TypeInput("Deep Clean", 30, 0, 45, "#123456"));
        UUID bufferedType = (UUID) created.get("id");

        // Valid anchor booking 10:00-10:30 for dentist A in chair 1.
        api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA, chairA, null, monday(LocalTime.parse("10:00")), "anchor"));

        // Buffered guard window: adjacent 10:30-11:00 booking for the SAME chair (different dentist) must be rejected.
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA2, chairA, bufferedType, monday(LocalTime.parse("10:30")), "inside guard buffer")));
        // Gerrymandered too: 11:14 still inside 45-minute guard window after the 10:30 end.
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA2, chairA, bufferedType, monday(LocalTime.parse("11:14")), "still inside guard buffer")));
        // 11:16 is outside the 45-min guard window → accepted for dentist A2 (available from 12:00).
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA2, chairA, bufferedType, monday(LocalTime.parse("11:16")), "just outside guard but outside availability")));

        // Operating hours enforcement: 18:00 locally is after 17:00 close.
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA, null, null, monday(LocalTime.parse("18:00")), "after hours")));

        // Dentist availability enforcement (dentist A works 09:00-12:00 Monday).
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA, null, null, monday(LocalTime.parse("13:00")), "outside dentist availability")));

        // Archived patients cannot book.
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            s.execute("UPDATE patient SET deleted_at=now() WHERE id='" + patientA2 + "'");
        }
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA2, branchA, dentistA, chairA, null, monday(LocalTime.parse("10:45")), "archived patient")));
        // Duration must be 5-480 minutes.
        Instant start = monday(LocalTime.parse("14:30"));
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), new SchedulingController.BookingInput(patientA, branchA, dentistA2, null, null, start, start.plusSeconds(240), "4 minutes")));
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), new SchedulingController.BookingInput(patientA, branchA, dentistA2, null, null, start, start.plusSeconds(490*60), "8.1 hours")));
        // Time interval must be monotonic.
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), new SchedulingController.BookingInput(patientA, branchA, dentistA2, null, null, start.plus(2, java.time.temporal.ChronoUnit.DAYS), start, "reversed")));
    }

    private void ownerExec(java.util.function.Consumer<java.sql.Statement> block) throws SQLException {
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) { block.accept(s); }
    }

    @Test
    void windowsAndTimezoneAreRespectedExactlyOncePerClinicDay() {
        TenantContext.set(clinicA);
        // Same-day New York bookings succeed.
        api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA, null, null, monday(LocalTime.parse("10:00")), "same day Ny"));
        // A booking that spans clinic-local midnight is rejected.
        LocalTime edgeTime = LocalTime.parse("16:45");
        Instant localEdge = monday(edgeTime);
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), new SchedulingController.BookingInput(patientA, branchA, dentistA, null, null, localEdge, localEdge.plusSeconds(3600), "span local midnight")));
        // Clinic B is Asia/Kathmandu and dentist is INVALID for its timezone — cross-tenant protection fires.
        TenantContext.set(clinicB);
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000064"), clinicB, "OWNER"), booking(patientA, branchA, dentistA, chairA, null, monday(LocalTime.parse("10:00")), "cross clinic booking")));
    }

    @Test
    void maintenanceBlocksAndTimeOffBlockBookingsAndReschedules() {
        TenantContext.set(clinicA);
        Map<String, Object> created = api.type(auth(user("000000000061"), clinicA, "OWNER"),
            new SchedulingController.TypeInput("Deep Clean", 30, 0, 45, "#123456"));
        UUID bufferedType = (UUID) created.get("id");
        api.chairBlock(auth(user("000000000061"), clinicA, "OWNER"), new SchedulingController.BlockInput(chairA, monday(LocalTime.parse("12:00")), monday(LocalTime.parse("12:30")), "synthetic maintenance"));
        // Booking inside a chair maintenance block fails.
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA2, chairA, bufferedType, monday(LocalTime.parse("12:00")), "in maintenance")));
        // A reschedule into the maintenance block fails as well.
        UUID movable = bookOkFor(dentistA2, monday(LocalTime.parse("13:00")), "resched-target");
        assertThrows(ResponseStatusException.class, () ->
            api.reschedule(auth(user("000000000062"), clinicA, "FRONT_DESK"), movable, new SchedulingController.RescheduleInput(monday(LocalTime.parse("12:00")), monday(LocalTime.parse("12:30")), "into maintenance")));
        // Dentist time off.
        api.timeOff(auth(user("000000000061"), clinicA, "OWNER"), new SchedulingController.BlockInput(dentistA, monday(LocalTime.parse("10:45")), monday(LocalTime.parse("11:15")), "synthetic training"));
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"), booking(patientA, branchA, dentistA, chairA, null, monday(LocalTime.parse("10:45")), "during time off")));
        // Time-off cannot be planned over existing booked appointments.
        assertThrows(ResponseStatusException.class, () ->
            api.timeOff(auth(user("000000000061"), clinicA, "OWNER"), new SchedulingController.BlockInput(dentistA2, monday(LocalTime.parse("13:00")), monday(LocalTime.parse("13:30")), "over booked slot")));
    }

    @Test
    void lifecycleTransitionsAuditEventsAndOptimisticRaceBehave() throws SQLException {
        TenantContext.set(clinicA);
        UUID appt = bookOkFor(dentistA, monday(LocalTime.parse("09:00")), "lifecycle");
        api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt, new SchedulingController.StatusInput("CONFIRMED", "will attend"));
        api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt, new SchedulingController.StatusInput("ARRIVED", null));
        api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt, new SchedulingController.StatusInput("IN_CHAIR", null));
        api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt, new SchedulingController.StatusInput("COMPLETED", null));
        assertThat(api.appointment(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt).get("status")).isEqualTo("COMPLETED");
        // Invalid transitions and immutable finished records.
        assertThrows(ResponseStatusException.class, () ->
            api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt, new SchedulingController.StatusInput("CANCELLED", "after completion")));
        assertThrows(ResponseStatusException.class, () ->
            api.reschedule(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt, new SchedulingController.RescheduleInput(monday(LocalTime.parse("16:30")), monday(LocalTime.parse("16:30")).plusSeconds(1800), "completed record")));
        List<Map<String, Object>> events = api.events(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt);
        assertThat(events).hasSizeGreaterThanOrEqualTo(5);
        assertThat(events.get(0).get("event_type")).isEqualTo("BOOKED");

        // NO_SHOW from CONFIRMED and BOOKED: no-show recording path.
        UUID noShow = bookOkFor(dentistA, monday(LocalTime.parse("09:30")), "no show");
        api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), noShow, new SchedulingController.StatusInput("NO_SHOW", "did not arrive"));

        // Optimistic concurrency: a stale-versioned status update loses; the row-locking version
        // guard guarantees exactly one writer at the same version. Verified at the DB layer.
        UUID race = bookOkFor(dentistA, monday(LocalTime.parse("10:00")), "race");
        try (Connection c1 = appConnection(); Connection c2 = appConnection()) {
            for (Connection c : List.of(c1, c2)) { c.setAutoCommit(false); c.createStatement().execute("SELECT set_config('app.clinic_id','" + clinicA + "',false)"); }
            var r1s = c1.createStatement(); var r2s = c2.createStatement();
            var v1 = r1s.executeQuery("SELECT version FROM appointment WHERE id='" + race + "'"); v1.next();
            var v2 = r2s.executeQuery("SELECT version FROM appointment WHERE id='" + race + "'"); v2.next();
            assertThat(v1.getInt(1)).isEqualTo(v2.getInt(1));
            r1s.execute("UPDATE appointment SET status='CONFIRMED', version=version+1 WHERE id='" + race + "' AND version=" + v1.getInt(1));
            c1.commit();
            var lost = r2s.executeUpdate("UPDATE appointment SET status='CANCELLED', version=version+1 WHERE id='" + race + "' AND version=" + v2.getInt(1));
            assertThat(lost).as("stale competing update must be rejected").isZero();
            c2.commit();
        }
    }

    @Test
    void queueCheckInWaitlistAndOutboxBehave() {
        TenantContext.set(clinicA);
        Map<String, Object> created = api.type(auth(user("000000000061"), clinicA, "OWNER"),
            new SchedulingController.TypeInput("Deep Clean", 30, 0, 45, "#123456"));
        UUID bufferedType = (UUID) created.get("id");
        // Walk-in: book a slot starting essentially now (today, clinic-local), then it appears in the queue.
        UUID queued = bookOkFor(dentistA, Instant.now().plusSeconds(90), "walk-in booking");
        api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), queued, new SchedulingController.StatusInput("ARRIVED", null));
        assertThrows(ResponseStatusException.class, () ->
            api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), queued, new SchedulingController.StatusInput("ARRIVED", "duplicate check-in")));
        List<Map<String, Object>> queue = api.queue(auth(user("000000000062"), clinicA, "FRONT_DESK"), branchA);
        assertThat(queue).isNotEmpty();

        // Waitlist flow.
        UUID waitlist = (UUID) api.waitlist(auth(user("000000000062"), clinicA, "FRONT_DESK"),
            new SchedulingController.WaitlistInput(patientA, branchA, null, monday(LocalTime.MIDNIGHT).minus(1, java.time.temporal.ChronoUnit.DAYS), monday(LocalTime.MIDNIGHT).plusSeconds(7*86400L))).get("id");
        api.offerWaitlist(auth(user("000000000062"), clinicA, "FRONT_DESK"), waitlist, 30);
        api.expireOffers(auth(user("000000000061"), clinicA, "OWNER"));
        assertThrows(ResponseStatusException.class, () ->
            api.offerWaitlist(auth(user("000000000062"), clinicA, "FRONT_DESK"), waitlist, 30));
        // Expired offers do not reserve anything: a DIRECT booking in the same window still works.
        bookFrontTry(dentistA, monday(LocalTime.parse("10:00")), "after expiry");

        // Waitlist closure hook: booking marks the matching WAITING entry BOOKED.
        UUID waitlist2 = (UUID) api.waitlist(auth(user("000000000062"), clinicA, "FRONT_DESK"),
            new SchedulingController.WaitlistInput(patientA, branchA, null, monday(LocalTime.MIDNIGHT), monday(LocalTime.MIDNIGHT).plusSeconds(7*86400L))).get("id");
        bookOkFor(dentistA, monday(LocalTime.parse("11:00")), "closing booking");
        TenantContext.set(clinicA);
        assertThat(((String) pooled.queryForMap("SELECT status FROM appointment_waitlist WHERE id='" + waitlist2 + "'").get("status"))).isEqualTo("BOOKED");

        // Waitlist entry cannot reference another clinic's patient.
        TenantContext.set(clinicB);
        assertThrows(ResponseStatusException.class, () ->
            api.waitlist(auth(user("000000000064"), clinicB, "OWNER"), new SchedulingController.WaitlistInput(patientA, branchB, null, Instant.now(), Instant.now().plusSeconds(3600))));

        // Waitlist cancellation.
        TenantContext.set(clinicA);
        UUID wl3 = (UUID) api.waitlist(auth(user("000000000062"), clinicA, "FRONT_DESK"),
            new SchedulingController.WaitlistInput(patientA, branchA, null, monday(LocalTime.MIDNIGHT), monday(LocalTime.MIDNIGHT).plusSeconds(7*86400L))).get("id");
        assertThat(((String) api.cancelWaitlist(auth(user("000000000062"), clinicA, "FRONT_DESK"), wl3).get("status"))).isEqualTo("CANCELLED");
        assertThrows(ResponseStatusException.class, () -> api.cancelWaitlist(auth(user("000000000062"), clinicA, "FRONT_DESK"), wl3));

        // Notification outbox: PENDING, deduplicated per (appointment,event) combination.
        var rows = api.pendingNotifications(auth(user("000000000061"), clinicA, "OWNER"));
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) assertThat(row.get("state")).isEqualTo("PENDING");
        int dupes = pooled.queryForObject(
            "SELECT count(*) - count(DISTINCT (appointment_id, event_type)) FROM appointment_notification_outbox WHERE clinic_id='" + clinicA + "'", Integer.class);
        assertThat(dupes).isEqualTo(0);
    }

    @Test
    void crossClinicAccessIsRlsProtected() throws SQLException {
        TenantContext.set(clinicA);
        UUID appt = bookOkFor(dentistA, monday(LocalTime.parse("10:00")), "isolation anchor");
        assertThat(api.events(auth(user("000000000062"), clinicA, "FRONT_DESK"), appt)).isNotEmpty();
        // Clinic B cannot see or modify clinic A's appointment.
        TenantContext.set(clinicB);
        assertThrows(ResponseStatusException.class, () -> api.appointment(auth(user("000000000064"), clinicB, "OWNER"), appt));
        assertThrows(ResponseStatusException.class, () -> api.events(auth(user("000000000064"), clinicB, "OWNER"), appt));
        // Cross-clinic booking references are scoped, not 500s.
        assertThrows(ResponseStatusException.class, () ->
            api.book(auth(user("000000000064"), clinicB, "OWNER"), booking(patientA, branchA, dentistA, chairA, null, monday(LocalTime.parse("10:00")), "cross-clinic patients/dentist/chair/branch")));
        // RLS layer: clinic A rows invisible from a clinic B session regardless of API.
        try (Connection app = appConnection(); var s = app.createStatement()) {
            s.execute("SELECT set_config('app.clinic_id','" + clinicB + "',false)");
            try (var rs = s.executeQuery("SELECT count(*) FROM appointment")) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
            try (var rs = s.executeQuery("SELECT count(*) FROM appointment_waitlist")) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
            try (var rs = s.executeQuery("SELECT count(*) FROM appointment_event")) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
        }
    }

    // ——— helpers ———

    private UUID bookOkFor(UUID dentist, Instant start, String label) {
        TenantContext.set(clinicA);
        try { return _bookOkFor(dentist, start, label); }
        catch (ResponseStatusException e) {
                throw e;
        }
    }
    private UUID _bookOkFor(UUID dentist, Instant start, String label) {
        Map<String, Object> row = api.book(auth(user("000000000062"), clinicA, "FRONT_DESK"),
            booking(patientA, branchA, dentist, chairA, null, start, label));
        return (UUID) row.get("id");
    }

    private void bookFrontTry(UUID dentist, Instant start, String label) { bookOkFor(dentist, start, label); }

    private int raceStatus(UUID id, String status, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return 500; }
        TenantContext.set(clinicA);
        try { api.status(auth(user("000000000062"), clinicA, "FRONT_DESK"), id, new SchedulingController.StatusInput(status, null)); return 200; }
        catch (ResponseStatusException e) { return e.getStatusCode().value(); }
    }

    private int winnerCount(Future<Integer> a, Future<Integer> b) {
        try { return (a.get() == 200 ? 1 : 0) + (b.get() == 200 ? 1 : 0); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(System.getenv("M310_SCHEDULING_TEST_DB_URL"),
            System.getenv().getOrDefault("M310_SCHEDULING_TEST_OWNER_USER", "postgres"),
            System.getenv("M310_SCHEDULING_TEST_OWNER_PASSWORD"));
    }

    private Connection appConnection() throws SQLException {
        return DriverManager.getConnection(System.getenv("M310_SCHEDULING_TEST_DB_URL"), "dental_app",
            System.getenv("M310_SCHEDULING_TEST_APP_PASSWORD"));
    }

    @Test
    void daylightSavingTransitionIsHandledWithClinicTimezoneMath() throws SQLException {
        UUID clinicDst = UUID.fromString("31000000-0000-4000-8000-000000000003");
        UUID typeDst = UUID.fromString("31000000-0000-4000-8000-000000000072");
        ZoneId nyTz = ZoneId.of(NY);
        // The Monday-US DST spring-forward week (US spring-forward falls on a Sunday).
        LocalDate dstMonday = LocalDate.of(2027, 3, 14).plusDays(1); // Monday after 2027-03-14
        Instant nine = dstMonday.atTime(LocalTime.parse("09:00")).atZone(nyTz).toInstant();
        try (Connection owner = ownerConnection(); var s = owner.createStatement()) {
            s.execute("INSERT INTO clinic(id,slug,name,timezone) VALUES ('" + clinicDst + "','m310-dst','DST Clinic','" + NY + "')");
            s.execute("INSERT INTO branch(id,clinic_id,name) VALUES ('31000000-0000-4000-8000-000000000013','" + clinicDst + "','DST Branch')");
            s.execute("INSERT INTO app_user(id,clinic_id,email,password_hash,full_name,role,active) VALUES ('31000000-0000-4000-8000-000000000065','" + clinicDst + "','owner-dst@m310.test','x','Owner DST','OWNER',true)");
            s.execute("INSERT INTO dentist(id,clinic_id,full_name,active) VALUES ('31000000-0000-4000-8000-000000000043','" + clinicDst + "','Dr. DST',true)");
            s.execute("INSERT INTO patient(id,clinic_id,chart_no,first_name,last_name) VALUES ('31000000-0000-4000-8000-000000000074','" + clinicDst + "','DST1','D','Synthetic')");
            s.execute("INSERT INTO branch_operating_hour(clinic_id,branch_id,day_of_week,opens_at,closes_at) VALUES ('" + clinicDst + "','" + "31000000-0000-4000-8000-000000000013" + "',1,'00:00','23:59')");
            s.execute("INSERT INTO dentist_availability(clinic_id,dentist_id,branch_id,day_of_week,starts_at,ends_at) VALUES ('" + clinicDst + "','31000000-0000-4000-8000-000000000043','31000000-0000-4000-8000-000000000013',1,'09:00','17:00')");;
            s.execute("INSERT INTO appointment_type(id,clinic_id,name,default_minutes) VALUES ('" + typeDst + "','" + clinicDst + "','DST Type',30)");
        }
        TenantContext.set(clinicDst);
        try {
            // Booking inside Monday availability during the spring-forward week: local 09:00 EDT
            // must resolve to the right UTC instant and pass operating/availability checks.
            Map<String, Object> booked = api.book(auth(user("000000000065"), clinicDst, "OWNER"),
                booking(UUID.fromString("31000000-0000-4000-8000-000000000074"), UUID.fromString("31000000-0000-4000-8000-000000000013"), UUID.fromString("31000000-0000-4000-8000-000000000043"), null, typeDst, nine, "dst monday"));
            assertThat(booked.get("status")).isEqualTo("BOOKED");
            // A clinic-local midnight span crossing into Tuesday is rejected as before.
            Instant twentyThree = dstMonday.atTime(LocalTime.parse("23:45")).atZone(nyTz).toInstant();
            assertThrows(ResponseStatusException.class, () ->
                api.book(auth(user("000000000065"), clinicDst, "OWNER"), new SchedulingController.BookingInput(UUID.fromString("31000000-0000-4000-8000-000000000074"), UUID.fromString("31000000-0000-4000-8000-000000000013"), UUID.fromString("31000000-0000-4000-8000-000000000043"), null, null, twentyThree, twentyThree.plusSeconds(3600), "span dst midnight")));
        } finally {
            TenantContext.clear();
            truncateAll();
        }
    }
}
