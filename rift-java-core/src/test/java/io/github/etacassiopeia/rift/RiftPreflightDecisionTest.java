package io.github.etacassiopeia.rift;

import org.junit.jupiter.api.Test;

import static io.github.etacassiopeia.rift.RiftImpl.PreflightDecision;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The version-preflight decision (issue #94): at or above the floor always passes; below the floor,
 * the embedded transport ({@code abiVerified}) trusts its already-validated ABI and only warns, while
 * remote/spawn keep the strict fail.
 */
class RiftPreflightDecisionTest {

    @Test
    void atOrAboveFloorAlwaysPasses() {
        assertEquals(PreflightDecision.PASS, RiftImpl.decide("0.13.3", VersionCheck.FAIL, false));
        assertEquals(PreflightDecision.PASS, RiftImpl.decide("0.13.1", VersionCheck.FAIL, false));
        assertEquals(PreflightDecision.PASS, RiftImpl.decide("1.0.0", VersionCheck.FAIL, true));
    }

    @Test
    void embeddedBelowFloorIsDemotedToWarnEvenInFailMode() {
        // The workspace-placeholder scenario: a local dev build reports 0.1.0 but its symbol set is
        // v2-complete (bind already passed). The ABI is authoritative → warn, don't fail.
        assertEquals(PreflightDecision.WARN, RiftImpl.decide("0.1.0", VersionCheck.FAIL, true));
        assertEquals(PreflightDecision.WARN, RiftImpl.decide("0.0.5", VersionCheck.FAIL, true));
    }

    @Test
    void remoteBelowFloorFailsInFailMode() {
        assertEquals(PreflightDecision.FAIL, RiftImpl.decide("0.1.0", VersionCheck.FAIL, false));
        assertEquals(PreflightDecision.FAIL, RiftImpl.decide("0.12.0", VersionCheck.FAIL, false));
    }

    @Test
    void warnModeNeverFails() {
        assertEquals(PreflightDecision.WARN, RiftImpl.decide("0.1.0", VersionCheck.WARN, false));
        assertEquals(PreflightDecision.WARN, RiftImpl.decide("0.12.0", VersionCheck.WARN, true));
    }
}
