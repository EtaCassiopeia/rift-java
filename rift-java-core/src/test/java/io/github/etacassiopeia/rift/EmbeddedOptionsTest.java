package io.github.etacassiopeia.rift;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EmbeddedOptionsTest {

    @Test
    void defaults() {
        EmbeddedOptions o = EmbeddedOptions.builder().build();
        assertEquals(Optional.empty(), o.libraryPath());
        assertEquals(VersionCheck.FAIL, o.versionCheck());
        assertFalse(o.serveAdminEagerly());
        assertEquals("127.0.0.1", o.adminHost());
        assertEquals(0, o.adminPort());
        assertEquals(Optional.empty(), o.apiKey());
    }

    @Test
    void overridesApplied() {
        EmbeddedOptions o = EmbeddedOptions.builder()
                .libraryPath(Path.of("/tmp/librift_ffi.so"))
                .versionCheck(VersionCheck.OFF)
                .serveAdminEagerly(true)
                .adminHost("0.0.0.0")
                .adminPort(2525)
                .apiKey("secret")
                .build();
        assertEquals(Optional.of(Path.of("/tmp/librift_ffi.so")), o.libraryPath());
        assertEquals(VersionCheck.OFF, o.versionCheck());
        assertEquals(true, o.serveAdminEagerly());
        assertEquals("0.0.0.0", o.adminHost());
        assertEquals(2525, o.adminPort());
        assertEquals(Optional.of("secret"), o.apiKey());
    }
}
