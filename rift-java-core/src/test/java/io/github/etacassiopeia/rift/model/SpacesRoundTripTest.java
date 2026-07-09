package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The correlated-isolation "space" scope on a stub (engine issue #223): when set, the stub is
 * eligible only for requests whose resolved flow id equals it. Absent from every corpus fixture;
 * hand-written spec-derived round-trip test (G2 + G3).
 */
class SpacesRoundTripTest {

    private static final String JSON = """
            {
              "space": "tenant-42",
              "predicates": [{"equals": {"path": "/account"}}],
              "responses": [{"is": {"statusCode": 200, "body": "ok"}}]
            }
            """;

    @Test
    void spaceRoundTrips() {
        RoundTripAssertions.assertRoundTrips(JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void spaceFieldIsTyped() {
        Stub stub = Stub.fromJson(JSON);
        assertEquals("tenant-42", stub.space().orElseThrow());
    }
}
