package io.github.achirdlabs.rift;

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
    void versionIsSemanticVersion() {
        assertTrue(
                RiftVersion.get().matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"),
                "version must be a MAJOR.MINOR.PATCH string, optionally -SNAPSHOT");
    }
}
