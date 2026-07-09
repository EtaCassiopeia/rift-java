package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The XPath predicate selector — including its namespace map, whose {@code ns} rename is the most
 * fragile piece of the wire shape — is not exercised by any corpus fixture (only {@code jsonpath}
 * is, in task-management-api.json). Hand-written spec-derived round-trip test (G2 + G3).
 */
class XPathSelectorRoundTripTest {

    private static final String JSON = """
            {
              "equals": {"body": "<user/>"},
              "xpath": {"selector": "//a:user", "ns": {"a": "urn:example:accounts"}}
            }
            """;

    @Test
    void xpathSelectorRoundTrips() {
        RoundTripAssertions.assertRoundTrips(JSON, Predicate::fromJson, Predicate::toJson);
    }

    @Test
    void xpathSelectorIsTyped() {
        Predicate predicate = Predicate.fromJson(JSON);
        PredicateSelector.XPath xpath = (PredicateSelector.XPath) predicate.parameters().selector().orElseThrow();
        assertEquals("//a:user", xpath.selector());
        assertTrue(xpath.namespaces().orElseThrow().get("a").equals("urn:example:accounts"));
        assertTrue(predicate.operation() instanceof PredicateOperation.Equals);
    }
}
