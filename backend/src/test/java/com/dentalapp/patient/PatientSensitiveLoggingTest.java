package com.dentalapp.patient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PatientSensitiveLoggingTest {
    @Test
    void demographicAndClinicalInputStringFormsDoNotExposeSensitiveValues() {
        String patientName = "Synthetic Private Name";
        String phone = "555-0109";
        String clinicalNote = "synthetic medication detail";
        String guardianName = "Synthetic Guardian Name";
        String email = "guardian@m25.test";

        var patient = new PatientController.PatientInput(patientName, "Private", LocalDate.of(2015, 4, 3),
                "UNDISCLOSED", phone, email, "synthetic address", guardianName, phone, false, 0L);
        var history = new PatientCareController.HistoryInput("synthetic allergy", "synthetic condition", clinicalNote);
        var guardian = new PatientCareController.GuardianInput(guardianName, "Parent", phone, email, true);

        assertThat(patient.toString()).doesNotContain(patientName, phone, email, guardianName, "synthetic address");
        assertThat(history.toString()).doesNotContain("synthetic allergy", "synthetic condition", clinicalNote);
        assertThat(guardian.toString()).doesNotContain(guardianName, phone, email);
    }
}
