package io.github.etacassiopeia.rift.junit5;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.etacassiopeia.rift.RiftVersion;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.Test;

class JUnit5WiringTest {

    @Test
    void jupiterExtensionApiIsOnTheCompileClasspath() {
        assertNotNull(Extension.class, "junit-jupiter-api must be a compile dependency of this module");
    }

    @Test
    void coreIsOnTheClasspath() {
        assertFalse(RiftVersion.get().isBlank());
    }
}
