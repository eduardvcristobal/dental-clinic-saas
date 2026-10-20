package com.dentalapp.odontogram;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ToothNotationTest {
    @Test void acceptsAllAndOnlyStandardPermanentAndPrimaryTeeth() {
        int count = 0;
        for (int n = 0; n <= 99; n++) {
            if (ToothNotation.validFdi(n)) count++;
        }
        assertThat(count).isEqualTo(52);
        for (int q=1;q<=4;q++) for (int p=1;p<=8;p++)
            assertThat(ToothNotation.validFdi(q*10+p)).isTrue();
        for (int q=5;q<=8;q++) for (int p=1;p<=5;p++)
            assertThat(ToothNotation.validFdi(q*10+p)).isTrue();
        for (int n : new int[]{0, 10, 19, 49, 50, 56, 60, 86, 99})
            assertThat(ToothNotation.validFdi(n)).isFalse();
    }

    @Test void validatesToothSurfaces() {
        assertThat(ToothNotation.validSurface(11,"I")).isTrue();
        assertThat(ToothNotation.validSurface(11,"O")).isFalse();
        assertThat(ToothNotation.validSurface(16,"O")).isTrue();
        assertThat(ToothNotation.validSurface(55,"O")).isTrue();
        assertThat(ToothNotation.validSurface(54,"I")).isFalse();
        assertThat(ToothNotation.validSurface(46,"P")).isFalse();
        assertThat(ToothNotation.validSurface(26,"P")).isTrue();
        assertThat(ToothNotation.validSurface(11,null)).isTrue();
        assertThat(ToothNotation.validSurface(99,null)).isFalse();
        assertThat(ToothNotation.validSurface(36,"UNKNOWN")).isFalse();
    }
}
