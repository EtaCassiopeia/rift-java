package io.github.etacassiopeia.rift.embedded;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddedRuntimeTest {

    @Test
    void runsOnAStableFfmRuntime() {
        assertTrue(
                Runtime.version().feature() >= 22,
                "rift-java-embedded requires JDK 22+ for the stable Foreign Function & Memory API");
    }
}
