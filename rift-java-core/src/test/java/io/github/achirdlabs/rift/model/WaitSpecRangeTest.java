package io.github.achirdlabs.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code wait} behavior's {@code {"min": ..., "max": ...}} range shape (as opposed to a fixed
 * millisecond number or an {@code inject} script) is not exercised by any corpus fixture;
 * hand-written spec-derived round-trip test (G2 + G3).
 */
class WaitSpecRangeTest {

    private static final String JSON = """
            {
              "predicates": [{"equals": {"path": "/wait"}}],
              "responses": [
                {
                  "is": {"statusCode": 200},
                  "_behaviors": {"wait": {"min": 100, "max": 200}}
                }
              ]
            }
            """;

    @Test
    void waitRangeRoundTrips() {
        RoundTripAssertions.assertRoundTrips(JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void waitRangeIsTyped() {
        Stub stub = Stub.fromJson(JSON);
        Response.Is is = (Response.Is) stub.responses().get(0);
        Behavior.Wait wait = (Behavior.Wait) is.behaviors().entries().get(0);
        WaitSpec.Range range = (WaitSpec.Range) wait.spec();
        assertEquals(100L, range.minMs());
        assertEquals(200L, range.maxMs());
    }
}
