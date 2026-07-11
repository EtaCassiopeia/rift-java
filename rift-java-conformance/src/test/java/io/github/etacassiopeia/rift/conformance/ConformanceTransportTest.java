package io.github.etacassiopeia.rift.conformance;

import org.junit.jupiter.api.Test;

import static io.github.etacassiopeia.rift.conformance.ConformanceTransport.EMBEDDED;
import static io.github.etacassiopeia.rift.conformance.ConformanceTransport.SPAWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit coverage for transport selection (AC1) — precedence, default, and the fail-loud unknown value. */
class ConformanceTransportTest {

    @Test
    void defaultsToSpawnWhenNeitherSourceIsSet() {
        assertEquals(SPAWN, ConformanceTransport.resolve(null, null));
        assertEquals(SPAWN, ConformanceTransport.resolve("", "  "));
    }

    @Test
    void readsEitherSource() {
        assertEquals(EMBEDDED, ConformanceTransport.resolve("EMBEDDED", null));
        assertEquals(EMBEDDED, ConformanceTransport.resolve(null, "EMBEDDED"));
        assertEquals(SPAWN, ConformanceTransport.resolve(null, "SPAWN"));
    }

    @Test
    void systemPropertyOutranksEnvironment() {
        assertEquals(SPAWN, ConformanceTransport.resolve("SPAWN", "EMBEDDED"));
        assertEquals(EMBEDDED, ConformanceTransport.resolve("EMBEDDED", "SPAWN"));
        // a blank property falls through to the environment
        assertEquals(EMBEDDED, ConformanceTransport.resolve("", "EMBEDDED"));
    }

    @Test
    void isTrimmedAndCaseInsensitive() {
        assertEquals(EMBEDDED, ConformanceTransport.resolve("  embedded ", null));
    }

    @Test
    void unknownValueFailsLoudRatherThanDefaulting() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConformanceTransport.resolve("EMBEDED", null));
        assertEquals(true, e.getMessage().contains("EMBEDED"),
                () -> "the message must name the offending value, was: " + e.getMessage());
    }
}
