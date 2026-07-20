package io.github.achirdlabs.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fault responses (Mountebank's raw-fault stub shape, {@code {"fault": "..."}}) do not appear in
 * any corpus fixture; hand-written spec-derived round-trip test (G2 + G3).
 */
class FaultResponseRoundTripTest {

    private static final String JSON = """
            {
              "predicates": [{"equals": {"path": "/flaky"}}],
              "responses": [{"fault": "CONNECTION_RESET_BY_PEER"}]
            }
            """;

    @Test
    void faultResponseRoundTrips() {
        RoundTripAssertions.assertRoundTrips(JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void faultFieldIsTyped() {
        Stub stub = Stub.fromJson(JSON);
        Response response = stub.responses().get(0);
        assertTrue(response instanceof Response.Fault);
        assertEquals("CONNECTION_RESET_BY_PEER", ((Response.Fault) response).fault());
    }
}
