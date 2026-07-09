package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Response {@code _behaviors} beyond the fixed/inject {@code wait} the corpus fixtures use:
 * {@code decorate}, {@code copy}, and {@code repeat}. Hand-written spec-derived round-trip test
 * (G2 + G3).
 */
class BehaviorsRoundTripTest {

    private static final String JSON = """
            {
              "predicates": [{"equals": {"path": "/users/42"}}],
              "responses": [
                {
                  "is": {"statusCode": 200, "body": "templated"},
                  "_behaviors": {
                    "decorate": "function(request, response) { response.body += '!'; }",
                    "copy": [
                      {"from": "path", "into": "${id}", "using": {"method": "regex", "selector": "/users/(.*)"}}
                    ],
                    "repeat": 3
                  }
                }
              ]
            }
            """;

    @Test
    void behaviorsRoundTrip() {
        RoundTripAssertions.assertRoundTrips(JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void behaviorsAreTyped() {
        Stub stub = Stub.fromJson(JSON);
        Response.Is is = (Response.Is) stub.responses().get(0);
        Behaviors behaviors = is.behaviors();
        assertEquals(3, behaviors.entries().size());

        Behavior.Decorate decorate = (Behavior.Decorate) find(behaviors, "decorate");
        assertTrue(decorate.script().contains("response.body"));

        Behavior.Copy copy = (Behavior.Copy) find(behaviors, "copy");
        assertEquals(1, copy.entries().size());
        assertEquals("${id}", copy.entries().get(0).into());
        assertEquals("regex", copy.entries().get(0).using().method());

        Behavior.Repeat repeat = (Behavior.Repeat) find(behaviors, "repeat");
        assertEquals(3, repeat.count());
    }

    private static Behavior find(Behaviors behaviors, String key) {
        return behaviors.entries().stream()
                .filter(b -> b.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing behavior: " + key));
    }
}
