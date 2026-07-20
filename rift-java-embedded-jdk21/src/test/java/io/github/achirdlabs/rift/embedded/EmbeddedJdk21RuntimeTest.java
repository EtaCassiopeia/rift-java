package io.github.achirdlabs.rift.embedded;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddedJdk21RuntimeTest {

    @Test
    void runsOnJdk21OrNewer() {
        assertTrue(
                Runtime.version().feature() >= 21,
                "rift-java-embedded-jdk21 targets JDK 21 (preview FFM)");
    }
}
