package io.github.etacassiopeia.rift.spawn;

import io.github.etacassiopeia.rift.error.EngineUnavailable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The OS/arch → release-triple matrix, exercised via the injectable {@code of(os, arch, musl)} seam. */
class SpawnTargetPlatformTest {

    @Test
    void mapsEverySupportedPlatform() {
        assertEquals("rift-v1.0.0-x86_64-unknown-linux-gnu.tar.gz", TargetPlatform.of("Linux", "amd64", false).archiveName("1.0.0"));
        assertEquals("rift-v1.0.0-x86_64-unknown-linux-musl.tar.gz", TargetPlatform.of("Linux", "x86_64", true).archiveName("1.0.0"));
        assertEquals("rift-v1.0.0-aarch64-unknown-linux-gnu.tar.gz", TargetPlatform.of("Linux", "aarch64", false).archiveName("1.0.0"));
        assertEquals("rift-v1.0.0-x86_64-apple-darwin.tar.gz", TargetPlatform.of("Mac OS X", "x86_64", false).archiveName("1.0.0"));
        assertEquals("rift-v1.0.0-aarch64-apple-darwin.tar.gz", TargetPlatform.of("Mac OS X", "arm64", false).archiveName("1.0.0"));

        TargetPlatform win = TargetPlatform.of("Windows 11", "amd64", false);
        assertEquals("rift-v1.0.0-x86_64-pc-windows-msvc.zip", win.archiveName("1.0.0"));
        assertEquals("rift.exe", win.binaryName());
        assertTrue(win.isWindows());
        assertEquals("rift", TargetPlatform.of("Linux", "amd64", false).binaryName());
    }

    @Test
    void unsupportedPlatformsThrow() {
        assertThrows(EngineUnavailable.class, () -> TargetPlatform.of("Windows 11", "aarch64", false));
        assertThrows(EngineUnavailable.class, () -> TargetPlatform.of("SunOS", "sparc", false));
        assertThrows(EngineUnavailable.class, () -> TargetPlatform.of("Linux", "riscv64", false));
    }
}
