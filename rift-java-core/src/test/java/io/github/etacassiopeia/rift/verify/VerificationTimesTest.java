package io.github.etacassiopeia.rift.verify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationTimesTest {

    @Test
    void timesAndExactlyAreEqualCountMatchers() {
        assertTrue(VerificationTimes.times(2).matches(2));
        assertFalse(VerificationTimes.times(2).matches(3));
        assertFalse(VerificationTimes.times(2).matches(1));
        assertTrue(VerificationTimes.exactly(2).matches(2));
        assertFalse(VerificationTimes.exactly(2).matches(1));
    }

    @Test
    void atLeastAtMostBetween() {
        assertTrue(VerificationTimes.atLeast(2).matches(2));
        assertTrue(VerificationTimes.atLeast(2).matches(5));
        assertFalse(VerificationTimes.atLeast(2).matches(1));

        assertTrue(VerificationTimes.atMost(2).matches(0));
        assertTrue(VerificationTimes.atMost(2).matches(2));
        assertFalse(VerificationTimes.atMost(2).matches(3));

        assertTrue(VerificationTimes.between(1, 3).matches(1));
        assertTrue(VerificationTimes.between(1, 3).matches(3));
        assertFalse(VerificationTimes.between(1, 3).matches(0));
        assertFalse(VerificationTimes.between(1, 3).matches(4));
    }

    @Test
    void never() {
        assertTrue(VerificationTimes.never().matches(0));
        assertFalse(VerificationTimes.never().matches(1));
    }

    @Test
    void descriptionIsHumanReadable() {
        assertTrue(VerificationTimes.times(1).describe().toLowerCase().contains("1"));
        assertTrue(VerificationTimes.atLeast(2).describe().toLowerCase().contains("at least"));
        assertTrue(VerificationTimes.never().describe().toLowerCase().contains("never"));
    }
}
