package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonValue;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared G2/G3 assertions (see the WireModelRoundTripTest gate) reused by the spec-derived
 * round-trip tests that exercise surfaces not present in the corpus fixtures.
 */
final class RoundTripAssertions {

    private RoundTripAssertions() {}

    /**
     * Parses {@code json} with {@code fromJson}, writes it back, re-parses the output, and
     * asserts: G2 — the two typed models are equal; G3 — the original and round-tripped text are
     * semantically equal as generic JSON trees.
     */
    static <T> void assertRoundTrips(String json, Function<String, T> fromJson, Function<T, String> toJson) {
        T model1 = fromJson.apply(json);
        String out = toJson.apply(model1);
        T model2 = fromJson.apply(out);
        assertEquals(model1, model2, "G2: parse -> write -> parse must be stable");

        JsonValue original = JsonValue.parse(json);
        JsonValue roundTripped = JsonValue.parse(out);
        assertTrue(
                JsonValue.semanticEquals(original, roundTripped),
                () -> "G3: semantic mismatch.\n  original: " + original.toJson() + "\n  produced: " + roundTripped.toJson());
    }
}
