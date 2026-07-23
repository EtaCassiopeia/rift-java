package io.github.achirdlabs.rift.conformance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for the live-engine switch. Every gated conformance suite now gates on this one
 * function, so the branches are pinned here rather than only implied by which suites happen to run.
 */
class LiveEngineTest {

    @Test
    void unsetOrBlankMeansOff() {
        assertFalse(LiveEngine.integrationEnabled(null), "unset is off — the default for a plain build");
        assertFalse(LiveEngine.integrationEnabled(""));
        assertFalse(LiveEngine.integrationEnabled("   "));
    }

    @Test
    void zeroIsAnExplicitOff() {
        // The one value that is present and still means off, so a lane can be disabled without
        // unsetting the variable.
        assertFalse(LiveEngine.integrationEnabled("0"));
    }

    @Test
    void anyOtherValueMeansOn() {
        assertTrue(LiveEngine.integrationEnabled("1"));
        assertTrue(LiveEngine.integrationEnabled("true"));
        // Deliberately not parsed as a boolean or a number: CI sets it to "1", and anything else
        // present is taken as intent to run rather than silently ignored.
        assertTrue(LiveEngine.integrationEnabled("yes"));
        assertTrue(LiveEngine.integrationEnabled("00"));
    }
}
