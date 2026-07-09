package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proxy responses do not appear in any corpus fixture, so this is a hand-written spec-derived
 * round-trip test (G2 + G3) covering every {@code ProxyResponse} field, including the four that
 * the engine always serializes (mode/predicateGenerators/addWaitBehavior/injectHeaders) even at
 * their default value — this test's input already carries all four so the round trip is exact.
 */
class ProxyResponseRoundTripTest {

    private static final String JSON = """
            {
              "predicates": [{"equals": {"path": "/orders"}}],
              "responses": [
                {
                  "proxy": {
                    "to": "http://upstream:8080",
                    "mode": "proxyOnce",
                    "predicateGenerators": [{"matches": {"path": true}}],
                    "addWaitBehavior": true,
                    "injectHeaders": {"X-Forwarded-By": "rift"},
                    "addDecorateBehavior": "function(request, response) {}",
                    "pathRewrite": {"from": "/orders", "to": "/v2/orders"}
                  }
                }
              ]
            }
            """;

    @Test
    void proxyResponseRoundTrips() {
        RoundTripAssertions.assertRoundTrips(JSON, Stub::fromJson, Stub::toJson);
    }

    @Test
    void proxyFieldsAreTyped() {
        Stub stub = Stub.fromJson(JSON);
        Response response = stub.responses().get(0);
        assertTrue(response instanceof Response.Proxy);
        ProxyResponse proxy = ((Response.Proxy) response).proxy();
        assertEquals("http://upstream:8080", proxy.to());
        assertEquals("proxyOnce", proxy.mode());
        assertTrue(proxy.addWaitBehavior());
        assertEquals("rift", proxy.injectHeaders().get("X-Forwarded-By"));
        assertEquals("/orders", proxy.pathRewrite().orElseThrow().from());
        assertEquals("/v2/orders", proxy.pathRewrite().orElseThrow().to());
    }
}
