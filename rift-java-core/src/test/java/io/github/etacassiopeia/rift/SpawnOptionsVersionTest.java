package io.github.etacassiopeia.rift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@code Rift.spawn()} defaults to the pinned engine version, not the compatibility floor (issue #80):
 * the default is single-sourced from {@code <rift.engine.version>} via {@link RiftVersion#engineVersion()}.
 */
class SpawnOptionsVersionTest {

    @Test
    void defaultsToThePinnedEngineVersionNotTheFloor() {
        String defaulted = SpawnOptions.builder().build().version();
        assertEquals(RiftVersion.engineVersion(), defaulted,
                "spawn default is the pinned engine version");
        // The whole point: it is NOT the compatibility floor.
        assertNotEquals(RiftImpl.MIN_ENGINE_VERSION, defaulted,
                "spawn no longer defaults to the floor " + RiftImpl.MIN_ENGINE_VERSION);
    }

    @Test
    void explicitVersionWins() {
        assertEquals("9.9.9", SpawnOptions.builder().version("9.9.9").build().version());
    }

    @Test
    void engineVersionIsAResolvedSemver() {
        String v = RiftVersion.engineVersion();
        assertFalse(v.isBlank());
        assertFalse(v.startsWith("${"), "the <rift.engine.version> placeholder was filtered at build time: " + v);
        assertTrue(v.matches("\\d+\\.\\d+\\.\\d+.*"), "looks like a semver: " + v);
    }
}
