package io.github.etacassiopeia.rift.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip test (G2 + G3) for {@code ProxyResponse}. The {@code JSON} fixture carries every field
 * so the full-field round trip is exact; {@link #minimalProxyRoundTrips} covers the engine's minimal
 * form (corpus 07), where {@code addWaitBehavior} (false) and {@code injectHeaders} (empty) are
 * omitted on the wire and so must not be re-injected on write (issue #56).
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

    private static final String MINIMAL_JSON = """
            {
              "predicates": [{"equals": {"path": "/orders"}}],
              "responses": [
                {
                  "proxy": {
                    "to": "http://localhost:4501",
                    "mode": "proxyOnce",
                    "predicateGenerators": [{"matches": {"method": true, "path": true}}]
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
    void minimalProxyRoundTrips() {
        // Corpus 07 shape: no addWaitBehavior, no injectHeaders — serialization must not inject them.
        RoundTripAssertions.assertRoundTrips(MINIMAL_JSON, Stub::fromJson, Stub::toJson);
    }

    private static final String WAIT_ONLY_JSON = """
            {
              "predicates": [{"equals": {"path": "/orders"}}],
              "responses": [
                {
                  "proxy": {
                    "to": "http://localhost:4501",
                    "mode": "proxyOnce",
                    "predicateGenerators": [{"matches": {"path": true}}],
                    "addWaitBehavior": true
                  }
                }
              ]
            }
            """;

    private static final String HEADERS_ONLY_JSON = """
            {
              "predicates": [{"equals": {"path": "/orders"}}],
              "responses": [
                {
                  "proxy": {
                    "to": "http://localhost:4501",
                    "mode": "proxyOnce",
                    "predicateGenerators": [{"matches": {"path": true}}],
                    "injectHeaders": {"X-Forwarded-By": "rift"}
                  }
                }
              ]
            }
            """;

    @Test
    void mixedToggleProxiesRoundTrip() {
        // The two omit-at-default fields are gated by independent conditions: addWaitBehavior=true with
        // no injectHeaders, and injectHeaders present with addWaitBehavior=false, must each round-trip
        // without the other field being injected.
        RoundTripAssertions.assertRoundTrips(WAIT_ONLY_JSON, Stub::fromJson, Stub::toJson);
        RoundTripAssertions.assertRoundTrips(HEADERS_ONLY_JSON, Stub::fromJson, Stub::toJson);
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
