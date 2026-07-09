package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scenario FSM gate/transition ({@code requiredScenarioState}/{@code newScenarioState},
 * WireMock's {@code whenScenarioStateIs}/{@code willSetStateTo}): none of the corpus fixtures
 * exercise a state transition (only bare {@code scenarioName} for documentation). Hand-written
 * spec-derived round-trip test (G2 + G3).
 */
class ScenarioStateRoundTripTest {

    private static final String JSON = """
            {
              "scenarioName": "Checkout",
              "requiredScenarioState": "cart-filled",
              "newScenarioState": "order-placed",
              "predicates": [{"equals": {"path": "/checkout"}}],
              "responses": [{"is": {"statusCode": 200, "body": "placed"}}]
            }
            """;

    @Test
    void scenarioStateRoundTrips() {
        RoundTripAssertions.assertRoundTrips(JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void scenarioStateFieldsAreTyped() {
        Stub stub = Stub.fromJson(JSON);
        assertEquals("Checkout", stub.scenarioName().orElseThrow());
        assertEquals("cart-filled", stub.requiredScenarioState().orElseThrow());
        assertEquals("order-placed", stub.newScenarioState().orElseThrow());
    }
}
