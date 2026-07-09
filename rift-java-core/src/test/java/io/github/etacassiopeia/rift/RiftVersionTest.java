package io.github.etacassiopeia.rift;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiftVersionTest {

    @Test
    void reportsAResolvedSdkVersion() {
        String version = RiftVersion.get();
        assertFalse(version.isBlank(), "SDK version must be resolved from the filtered resource");
        assertFalse(version.contains("${"), "version placeholder must be filtered at build time");
    }

    @Test
    void versionMatchesTheCurrentDevelopmentLine() {
        assertTrue(RiftVersion.get().startsWith("0.1.0"), "expected the 0.1.0 development line");
    }
}
