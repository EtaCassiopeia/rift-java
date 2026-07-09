package io.github.etacassiopeia.rift.model;

import io.github.etacassiopeia.rift.json.JsonString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Predicate surfaces not exercised by any corpus fixture: the {@code or} boolean operation, the
 * {@code caseSensitive}/{@code keyCaseSensitive}/{@code except} matcher parameters, and a malformed
 * {@code xpath.ns} value (must fail with a typed {@link WireFormatException}, not a
 * {@link ClassCastException}). Hand-written spec-derived round-trip test (G2 + G3) plus a negative
 * case.
 */
class PredicateCoverageTest {

    private static final String OR_JSON = """
            {
              "or": [
                {"equals": {"path": "/a"}},
                {"equals": {"path": "/b"}}
              ]
            }
            """;

    private static final String PARAMETERS_JSON = """
            {
              "equals": {"path": "/CaseTest"},
              "caseSensitive": false,
              "keyCaseSensitive": true,
              "except": "^/api"
            }
            """;

    @Test
    void orOperationRoundTrips() {
        RoundTripAssertions.assertRoundTrips(OR_JSON, Predicate::fromJson, Predicate::toJson);
    }

    @Test
    void orOperationIsTyped() {
        Predicate predicate = Predicate.fromJson(OR_JSON);
        PredicateOperation.Or or = (PredicateOperation.Or) predicate.operation();
        assertEquals(2, or.predicates().size());

        PredicateOperation.Equals first = (PredicateOperation.Equals) or.predicates().get(0).operation();
        assertEquals("/a", ((JsonString) first.fields().get("path")).value());

        PredicateOperation.Equals second = (PredicateOperation.Equals) or.predicates().get(1).operation();
        assertEquals("/b", ((JsonString) second.fields().get("path")).value());
    }

    @Test
    void matcherParametersRoundTrip() {
        RoundTripAssertions.assertRoundTrips(PARAMETERS_JSON, Predicate::fromJson, Predicate::toJson);
    }

    @Test
    void matcherParametersAreTyped() {
        Predicate predicate = Predicate.fromJson(PARAMETERS_JSON);
        PredicateParameters parameters = predicate.parameters();
        assertEquals(false, parameters.caseSensitive().orElseThrow());
        assertEquals(true, parameters.keyCaseSensitive().orElseThrow());
        assertEquals("^/api", parameters.except());
        assertTrue(predicate.operation() instanceof PredicateOperation.Equals);
    }

    @Test
    void malformedXPathNamespaceValueThrowsWireFormatExceptionNotClassCastException() {
        String json = """
                {
                  "equals": {"body": "<user/>"},
                  "xpath": {"selector": "//a", "ns": {"a": 5}}
                }
                """;
        assertThrows(WireFormatException.class, () -> Predicate.fromJson(json));
    }
}
