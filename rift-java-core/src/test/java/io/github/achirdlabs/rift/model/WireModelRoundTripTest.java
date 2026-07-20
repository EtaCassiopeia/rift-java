package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The objective gate for issue #2: every real Mountebank+_rift fixture in the corpus must parse
 * losslessly (G1), round-trip stably through the typed model (G2), and remain semantically
 * equivalent to the original JSON modulo only the two documented normalizations (G3). None of
 * these assertions may be weakened — a fixture the typed model cannot express is a FAILING test.
 */
class WireModelRoundTripTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "basic-api.json",
            "authentication-api.json",
            "error-testing.json",
            "feature-flags-api.json",
            "latency-testing.json",
            "task-management-api.json"
    })
    void fixtureRoundTrips(String fixtureName) {
        String text = readFixture(fixtureName);

        // G1 — parses without loss: every imposter/stub/predicate/response is a typed value.
        ImposterDefinitions model1 = assertDoesNotThrow(() -> ImposterDefinitions.fromJson(text), fixtureName + ": must parse without throwing");
        assertTrue(!model1.imposters().isEmpty(), fixtureName + ": fixture must contain at least one imposter");

        // G2 — lossless round-trip stability: parse -> write -> parse yields an equal model.
        String serialized = model1.toJson();
        ImposterDefinitions model2 = ImposterDefinitions.fromJson(serialized);
        assertEquals(model1, model2, fixtureName + ": G2 round-trip must be stable");

        // G3 — semantic equivalence to the input modulo key order + documented normalizations.
        JsonValue original = JsonValue.parse(text);
        JsonValue roundTripped = JsonValue.parse(serialized);
        assertTrue(
                JsonValue.semanticEquals(original, roundTripped),
                () -> fixtureName + ": G3 semantic mismatch.\n  original:   " + original.toJson()
                        + "\n  round-trip: " + roundTripped.toJson());
    }

    private static String readFixture(String name) {
        String resource = "/fixtures/rift-examples/" + name;
        try (InputStream in = WireModelRoundTripTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
